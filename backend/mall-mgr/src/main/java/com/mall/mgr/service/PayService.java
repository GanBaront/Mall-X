package com.mall.mgr.service;

import com.mall.mgr.common.BizException;
import com.mall.mgr.entity.*;
import com.mall.mgr.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 统一支付核心逻辑（指导书 4.2/4.3）
 * 幂等设计：分布式环境应为「分布式锁 + 数据库唯一索引 + 订单状态机」；
 * 课程单机环境以「DB 唯一索引 + 订单状态机 + 内存锁」等价实现。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PayService {

    private final PayOrderRepository payOrderRepository;
    private final PayChannelRepository payChannelRepository;
    private final PayWayRepository payWayRepository;
    private final PayAppRepository payAppRepository;
    private final MerchantRepository merchantRepository;
    private final ProductRepository productRepository;
    private final BizOrderRepository bizOrderRepository;
    private final MchNoticeRepository mchNoticeRepository;
    private final MktCouponRepository mktCouponRepository;

    @Value("${mall.alipay.base-url}")
    private String alipayBaseUrl;

    @Value("${mall.alipay.return-url:}")
    private String alipayReturnUrl;

    private final RestTemplate restTemplate = new RestTemplate();
    private static final DateTimeFormatter TS = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");
    private static final Random RANDOM = new Random();
    /** payOrderId -> 锁，保证同一笔回调串行处理 */
    private final ConcurrentHashMap<String, Object> callbackLocks = new ConcurrentHashMap<>();

    /** 暴露同一把锁给取消订单/关单等写操作：与支付回调互斥，防止取消与回调竞态互相覆盖 */
    public Object lockOf(String payOrderId) {
        return callbackLocks.computeIfAbsent(payOrderId, k -> new Object());
    }

    /* ==================== 4.2.1 统一支付下单 ==================== */

    /**
     * 统一支付下单（指导书 4.2.1）。
     * 职责：参数校验 -> 用户身份确定 -> 商户/商品归属匹配 -> 费率与手续费计算 ->
     * 生成/复用支付单（支付单初始状态 -1=待支付）-> 生成用户侧履约单（状态 0=待支付）->
     * 调用 mall-alipay 支付微服务预创建订单并获取收银台 payUrl。
     * 支付单状态机：-1=待支付，0=成功，1=失败，2=已退款，3=关闭。
     *
     * @param payload       下单参数 Map：wayCode(支付渠道)、payMoney(金额)、subject(商品/订单标题)
     *                      可选 orderNum(商户订单号)、userNum(用户编号)、body/address/num 等
     * @param loginUsername 当前登录用户名（用于履约单买家姓名脱敏展示）
     * @param loginUserNum  当前登录用户编号（登录态优先于 payload.userNum）
     * @return 含 payOrderId、mchOrderNo、payUrl、orderState 的下单结果 Map
     */
    @Transactional
    public Map<String, Object> createPayOrder(Map<String, Object> payload, String loginUsername, String loginUserNum) {
        String wayCode = str(payload.get("wayCode"));
        String subject = str(payload.get("subject"));
        BigDecimal payMoney;
        try {
            payMoney = payload.get("payMoney") == null ? null : new BigDecimal(String.valueOf(payload.get("payMoney")));
        } catch (NumberFormatException e) {
            throw new BizException("支付金额格式无效");
        }
        if (wayCode == null || payMoney == null) {
            throw new BizException("缺少必要参数：wayCode/payMoney");
        }
        if (payMoney.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BizException("支付金额必须大于 0");
        }
        // 支付方式可用性校验：方式启用 且 所属支付接口启用（管理员停用后下单直接拒绝，与可选列表同口径）
        if (!isWayEnabled(wayCode)) {
            throw new BizException("该支付方式已停用，请更换支付方式");
        }

        // 用户身份：一律以登录态为准（网关透传），请求体 userNum 不再采信，防止冒名下单
        if (loginUserNum == null || loginUserNum.isBlank() || "null".equals(loginUserNum)) {
            throw new BizException("登录状态无效，请重新登录");
        }
        String userNum = loginUserNum;

        // 商品明细解析：
        //  - items 非空：购物车多商品合并结算，逐项匹配在售商品并由服务端逐项计价（price 不采信客户端）；
        //  - items 为空：立即购买单商品路径，subject 精确匹配在售商品，num 为件数。
        java.util.List<Map<String, Object>> items = parseItems(payload.get("items"));
        java.util.LinkedHashMap<Product, Integer> itemProducts = new java.util.LinkedHashMap<>();
        int num = 1;
        if (items == null) {
            if (subject == null) throw new BizException("缺少必要参数：subject");
            Product product = matchOnSaleProduct(subject);
            num = parseNum(payload.get("num"));
            itemProducts.put(product, num);
        } else {
            if (items.isEmpty()) throw new BizException("商品明细为空，请重新选择商品");
            for (Map<String, Object> it : items) {
                String name = str(it.get("name"));
                int n = parseNum(it.get("num"));
                if (name == null || n < 1) throw new BizException("商品明细无效，请刷新后重试");
                itemProducts.merge(matchOnSaleProduct(name), n, Integer::sum);
            }
        }

        // 商户归属：全部商品必须属于同一在营商户（跨店合并结算拒绝，前端引导分店结算）
        Merchant merchant = null;
        BigDecimal listTotal = BigDecimal.ZERO; // 商品原价合计（服务端逐项计算，不采信客户端金额）
        for (Map.Entry<Product, Integer> e : itemProducts.entrySet()) {
            Product p = e.getKey();
            if (merchant == null) {
                merchant = merchantRepository.findById(p.getShopNum())
                        .orElseThrow(() -> new BizException("商品所属商户不存在"));
            } else if (!merchant.getShopNum().equals(p.getShopNum())) {
                throw new BizException("商品分属不同店铺，请按店铺分开结算");
            }
            listTotal = listTotal.add(nvl(p.getPrice()).multiply(new BigDecimal(e.getValue())));
        }
        listTotal = listTotal.setScale(2, RoundingMode.HALF_UP);
        if (merchant.getShopState() == null || merchant.getShopState() != 1) {
            throw new BizException("商户不可用，无法交易");
        }

        final Merchant mch = merchant;
        PayApp app = payAppRepository.findByShopNum(mch.getShopNum()).stream()
                .filter(a -> a.getState() != null && a.getState() == 1)
                .findFirst()
                .orElseGet(() -> payAppRepository.findByShopNum(mch.getShopNum()).stream().findFirst().orElse(null));

        // 费率：商户差异化费率优先，否则取渠道基础费率（手续费按优惠后实付金额计算）
        BigDecimal rate = resolveRate(mch, wayCode);

        // 订单号
        String mchOrderNo = str(payload.get("orderNum"));
        if (mchOrderNo == null) {
            mchOrderNo = "M" + LocalDateTime.now().format(TS) + (1000 + RANDOM.nextInt(9000));
        }

        // 支付单复用判定（状态机）：
        //   -1 待支付：正常重付复用；1 失败：复用并重置为待支付；3 关闭：允许重新下单；
        //   0 已成功 / 2 已退款：拒绝重复下单。
        PayOrder existing = payOrderRepository.findByMchOrderNo(mchOrderNo).orElse(null);
        if (existing != null) {
            Integer st = existing.getOrderState();
            if (st != null && st == 3) {
                existing = null; // 已关闭单允许重新下单
            } else if (st != null && st == 0) {
                throw new BizException("该订单已支付成功，请勿重复下单");
            } else if (st != null && st == 2) {
                throw new BizException("该订单已退款，无法重新支付");
            }
        }
        // 金额校验口径：
        //   复用「带券」支付单（重付场景，前端传券后实付 total）：payMoney 必须等于原支付单实付金额，券抵扣沿用；
        //   新单 / 无券复用：payMoney 必须等于服务端逐项计算的原价合计（防篡改/价格变动）。
        boolean reuseWithCoupon = existing != null && existing.getCouponId() != null;
        BigDecimal expectMoney = reuseWithCoupon ? existing.getMoney() : listTotal;
        if (payMoney.compareTo(expectMoney) != 0) {
            log.warn("下单金额与服务端计算不一致：subject={}, 客户端={}, 服务端={}", subject, payMoney, expectMoney);
            throw new BizException("商品价格已更新，请刷新后重新下单");
        }

        // 优惠券抵扣（payMoney 为商品原价）：现金红包直减、折扣券按折扣率打折，抵后至少 0.01 元
        BigDecimal couponDiscount = BigDecimal.ZERO;
        MktCoupon coupon = null;
        if (reuseWithCoupon) {
            // 复用带券支付单：券已核销绑定该订单号，抵扣沿用原值（重付不丢抵扣），并幂等重绑核销信息
            couponDiscount = existing.getCouponDiscount() == null ? BigDecimal.ZERO : existing.getCouponDiscount();
            coupon = mktCouponRepository.findById(existing.getCouponId()).orElse(null);
            if (coupon != null) {
                coupon.setState(1);
                coupon.setUsedAt(LocalDateTime.now());
                coupon.setUsedOrderNum(mchOrderNo);
                mktCouponRepository.save(coupon);
            }
        } else {
            String couponIdStr = str(payload.get("couponId"));
            if (couponIdStr != null) {
                long couponId;
                try {
                    couponId = Long.parseLong(couponIdStr);
                } catch (NumberFormatException e) {
                    throw new BizException("优惠券参数无效");
                }
                coupon = mktCouponRepository.findById(couponId).orElseThrow(() -> new BizException("优惠券不存在"));
                if (!userNum.equals(coupon.getUserNum())) throw new BizException("无权使用该优惠券");
                if (coupon.getState() != null && coupon.getState() == 1
                        && !mchOrderNo.equals(coupon.getUsedOrderNum())) {
                    throw new BizException("该优惠券已使用");
                }
                if (coupon.isExpired()) throw new BizException("该优惠券已过期");
                if (coupon.getType() != null && coupon.getType() == 2) {
                    couponDiscount = coupon.getAmount() == null ? BigDecimal.ZERO : coupon.getAmount();
                } else {
                    BigDecimal discRate = coupon.getDiscountRate() == null ? BigDecimal.ONE : coupon.getDiscountRate();
                    couponDiscount = payMoney.multiply(BigDecimal.ONE.subtract(discRate)).setScale(2, RoundingMode.HALF_UP);
                }
                if (couponDiscount.compareTo(payMoney) > 0) couponDiscount = payMoney;
            }
        }
        BigDecimal finalMoney = reuseWithCoupon ? existing.getMoney() : payMoney.subtract(couponDiscount);
        if (finalMoney.compareTo(new BigDecimal("0.01")) < 0) finalMoney = new BigDecimal("0.01");
        BigDecimal fee = finalMoney.multiply(rate).divide(new BigDecimal("100"), 2, RoundingMode.HALF_UP);

        PayOrder payOrder;
        if (existing != null) {
            payOrder = existing;
            if (payOrder.getOrderState() != null && payOrder.getOrderState() == 1) {
                payOrder.setOrderState(-1); // 失败单重新支付：重置为待支付（否则回调终态判断会忽略真实支付）
            }
        } else {
            payOrder = new PayOrder();
            payOrder.setPayOrderId("P" + LocalDateTime.now().format(TS) + String.format("%06d", RANDOM.nextInt(1000000)));
            payOrder.setMchOrderNo(mchOrderNo);
            payOrder.setOrderState(-1); // 待支付
            payOrder.setCreatedAt(LocalDateTime.now());
        }
        payOrder.setShopNum(mch.getShopNum());
        payOrder.setShopName(mch.getShopName());
        payOrder.setAppId(app != null ? app.getAppId() : "");
        payOrder.setWayCode(wayCode);
        payOrder.setMoney(finalMoney.setScale(2, RoundingMode.HALF_UP)); // 实付金额 = 商品原价 - 券抵扣
        payOrder.setCouponId(coupon != null ? coupon.getId() : null);
        payOrder.setCouponDiscount(coupon != null ? couponDiscount : BigDecimal.ZERO);
        payOrder.setRefundMoney(BigDecimal.ZERO);
        payOrder.setFee(fee);
        payOrder.setRate(rate.toPlainString() + "%");
        payOrder.setSubject(subject != null ? subject : "购物车结算(" + itemProducts.size() + "件)");
        payOrder.setBody(str(payload.get("body")));
        payOrder.setUserNum(userNum);
        payOrder.setUpdatedAt(LocalDateTime.now());
        payOrderRepository.save(payOrder);

        // 优惠券核销：绑定到本订单号（同订单重复下单时重复核销为幂等覆盖）
        if (coupon != null && !reuseWithCoupon) {
            coupon.setState(1);
            coupon.setUsedAt(LocalDateTime.now());
            coupon.setUsedOrderNum(mchOrderNo);
            mktCouponRepository.save(coupon);
        }

        // 交易履约单（用户侧订单）：多商品明细逐项生成（orderNum 带序号），单商品沿用 orderNum 本身；
        // 复用支付单重新下单时同步/重建履约单，保证展示金额与实付一致。
        var bizList = bizOrderRepository.findByPayOrderId(payOrder.getPayOrderId());
        if (items == null) {
            BizOrder bizOrder;
            if (bizList.isEmpty()) {
                bizOrder = new BizOrder();
                bizOrder.setOrderNum(mchOrderNo);
                bizOrder.setPayOrderId(payOrder.getPayOrderId());
                bizOrder.setUserNum(userNum);
                bizOrder.setShopNum(mch.getShopNum());
                bizOrder.setCreatedAt(LocalDateTime.now());
            } else {
                bizOrder = bizList.get(0);
            }
            Product product = itemProducts.keySet().iterator().next();
            bizOrder.setProductId(product.getId());
            bizOrder.setGoodsName(product.getName());
            bizOrder.setImage(product.getImage() == null ? "" : product.getImage());
            bizOrder.setNum(num);
            bizOrder.setPrice(nvl(product.getPrice()));
            // 订单金额存「实付金额」（券后），保证列表/详情/重新支付都以实付为准，不会丢失券抵扣
            bizOrder.setTotal(finalMoney.setScale(2, RoundingMode.HALF_UP));
            bizOrder.setState(0);
            bizOrder.setWayCode(wayCode);
            bizOrder.setBuyer(maskName(loginUsername));
            bizOrder.setAddress(str(payload.get("address")));
            bizOrder.setUpdatedAt(LocalDateTime.now());
            bizOrderRepository.save(bizOrder);
        } else {
            // 多商品合并结算：重建履约单（先删旧再逐项创建，幂等）
            bizOrderRepository.deleteAll(bizList);
            int idx = 1;
            for (Map.Entry<Product, Integer> e : itemProducts.entrySet()) {
                Product p = e.getKey();
                BizOrder b = new BizOrder();
                b.setOrderNum(mchOrderNo + "-" + idx);
                b.setPayOrderId(payOrder.getPayOrderId());
                b.setUserNum(userNum);
                b.setShopNum(mch.getShopNum());
                b.setProductId(p.getId());
                b.setGoodsName(p.getName());
                b.setImage(p.getImage() == null ? "" : p.getImage());
                b.setNum(e.getValue());
                b.setPrice(nvl(p.getPrice()));
                b.setTotal(nvl(p.getPrice()).multiply(new BigDecimal(e.getValue())).setScale(2, RoundingMode.HALF_UP));
                b.setState(0);
                b.setWayCode(wayCode);
                b.setBuyer(maskName(loginUsername));
                b.setAddress(str(payload.get("address")));
                b.setCreatedAt(LocalDateTime.now());
                b.setUpdatedAt(LocalDateTime.now());
                bizOrderRepository.save(b);
                idx++;
            }
        }

        // 调用支付微服务（mall-alipay）向渠道下单，获取 payUrl
        Map<String, Object> channelResp = requestChannelPrecreate(payOrder);

        Map<String, Object> data = new HashMap<>();
        data.put("payOrderId", payOrder.getPayOrderId());
        data.put("mchOrderNo", payOrder.getMchOrderNo());
        data.put("payUrl", channelResp.get("payUrl"));
        data.put("orderState", payOrder.getOrderState());
        return data;
    }

    /** 解析购物车商品明细（items: [{name, num}]），未传或为空数组返回 null（走单商品路径） */
    @SuppressWarnings("unchecked")
    private static java.util.List<Map<String, Object>> parseItems(Object raw) {
        if (!(raw instanceof java.util.List<?> list) || list.isEmpty()) return null;
        java.util.List<Map<String, Object>> out = new java.util.ArrayList<>();
        for (Object o : list) {
            if (o instanceof Map) out.add((Map<String, Object>) o);
        }
        return out.isEmpty() ? null : out;
    }

    /** 按商品名精确匹配「在售」商品；未命中或已下架一律拒绝（不回退模糊匹配，防跨店串价） */
    private Product matchOnSaleProduct(String name) {
        Product product = null;
        for (Product p : productRepository.findByNameContaining(name)) {
            if (name.equals(p.getName())) { product = p; break; }
        }
        if (product == null || product.getState() == null || product.getState() != 1) {
            throw new BizException("商品不存在或已下架，请刷新后重试");
        }
        return product;
    }

    /** 安全解析购买数量：非法值按 1 件处理 */
    private static int parseNum(Object raw) {
        try {
            int n = raw == null ? 1 : Integer.parseInt(String.valueOf(raw));
            return Math.max(n, 1);
        } catch (NumberFormatException e) {
            return 1;
        }
    }

    /* ==================== 4.2.2 支付回调（幂等） ==================== */

    /**
     * 支付结果回调处理（指导书 4.2.2，幂等保证）。
     * 由 mall-alipay 在用户支付完成后调用。幂等由两层组成：
     *   1) 内存锁 callbackLocks：同一 payOrderId 串行执行，杜绝并发重复入账；
     *   2) 订单状态机终态判定：已处于终态（orderState>=0）的订单直接返回成功，重复回调不重复处理。
     * 支付成功(payState=0)：支付单->0(成功)、履约单->1(待发货)、商户入账并推送「新订单提醒」；
     * 支付失败(payState!=0)：支付单->1(失败)。
     * （DB 唯一索引 payOrderId 为另一道幂等防线，课程环境以内存锁+状态机等价实现。）
     *
     * @param payload 回调参数 Map：payOrderId(支付单号)、payState(0=成功/1=失败)、channelOrderNo(渠道流水号)
     * @return 统一响应 Map（message=回调处理成功）
     */
    @Transactional
    public Map<String, Object> handleCallback(Map<String, Object> payload) {
        String payOrderId = str(payload.get("payOrderId"));
        Integer payState = payload.get("payState") != null ? Integer.parseInt(String.valueOf(payload.get("payState"))) : null;
        if (payOrderId == null || payState == null) {
            throw new BizException("回调参数缺失：payOrderId/payState");
        }
        Object lock = callbackLocks.computeIfAbsent(payOrderId, k -> new Object());
        synchronized (lock) {
            // 内存锁保证同一笔支付单的回调串行执行，避免并发重复入账
            PayOrder payOrder = payOrderRepository.findById(payOrderId)
                    .orElseThrow(() -> new BizException("支付订单不存在：" + payOrderId));
            // 幂等一：订单状态机——终态直接返回成功
            if (payOrder.getOrderState() != null && payOrder.getOrderState() >= 0) {
                return Map.of("message", "回调处理成功");
            }
            // 安全校验：回调金额必须与订单金额一致（防串单/篡改），带 money 字段时强校验
            Object moneyObj = payload.get("money");
            if (moneyObj != null && payOrder.getMoney() != null) {
                try {
                    BigDecimal cbMoney = new BigDecimal(String.valueOf(moneyObj));
                    if (cbMoney.compareTo(payOrder.getMoney()) != 0) {
                        log.error("回调金额与订单不一致，拒绝处理：payOrderId={}, 回调={}, 订单={}",
                                payOrderId, cbMoney.toPlainString(), payOrder.getMoney().toPlainString());
                        throw new BizException("回调金额与订单金额不一致");
                    }
                } catch (NumberFormatException ignore) {
                    // 非法金额格式视为未携带，交由渠道对账兜底
                }
            }
            LocalDateTime now = LocalDateTime.now();
            if (payState == 0) {
                payOrder.setOrderState(0);
                payOrder.setPayTime(now);
                payOrder.setChannelOrderNo(str(payload.get("channelOrderNo")));
                payOrder.setUpdatedAt(now);
                payOrderRepository.save(payOrder);

                // 履约单进入待发货
                for (BizOrder biz : bizOrderRepository.findByPayOrderId(payOrderId)) {
                    biz.setState(1);
                    biz.setPayTime(now); // 记录付款时间（订单详情展示用）
                    biz.setUpdatedAt(now);
                    bizOrderRepository.save(biz);
                }
                // 销量/库存：支付成功即累计销量、扣减库存（库存不足时只扣到 0，课程演示不做预占）
                for (BizOrder biz : bizOrderRepository.findByPayOrderId(payOrderId)) {
                    if (biz.getProductId() == null) continue;
                    productRepository.findById(biz.getProductId()).ifPresent(p -> {
                        int num = biz.getNum() == null ? 1 : biz.getNum();
                        p.setSales((p.getSales() == null ? 0 : p.getSales()) + num);
                        p.setStock(Math.max((p.getStock() == null ? 0 : p.getStock()) - num, 0));
                        productRepository.save(p);
                    });
                }
                // 货款托管：支付成功不立即入账商户，待用户确认收货后才结算（见 ShopController.confirm）
                notice(payOrder.getShopNum(), payOrder.getShopName(), "新订单提醒",
                        "您有一笔新订单 " + payOrder.getMchOrderNo() + "（" + payOrder.getSubject() + "），请及时发货。", "订单通知");
            } else {
                payOrder.setOrderState(1);
                payOrder.setUpdatedAt(now);
                payOrderRepository.save(payOrder);
            }
        }
        return Map.of("message", "回调处理成功");
    }

    /* ==================== 超时关单（生产用 MQ 延迟消息，此处定时任务等价实现） ==================== */

    /**
     * 超时未支付订单自动关单（等价实现，生产环境用 RocketMQ 延迟消息）。
     * 筛选状态为待支付(-1)且创建时间早于 deadline(当前时间 - timeoutMinutes) 的支付单，
     * 将其状态置为关闭(3)，并同步将用户侧履约单置为超时关闭(4)。
     *
     * @param timeoutMinutes 超时分钟数（由 OrderScheduler 注入，默认 30 分钟）
     */
    @Transactional
    public void closeTimeoutOrders(int timeoutMinutes) {
        LocalDateTime deadline = LocalDateTime.now().minusMinutes(timeoutMinutes);
        for (PayOrder po : payOrderRepository.findByOrderStateAndCreatedAtBefore(-1, deadline)) {
            // 与支付回调互斥：同一 payOrderId 串行处理（回调侧持有同一把锁），避免关单覆盖刚成功的回调
            Object lock = callbackLocks.computeIfAbsent(po.getPayOrderId(), k -> new Object());
            synchronized (lock) {
                // 持锁后重读最新状态，订单可能刚被回调置为成功
                PayOrder fresh = payOrderRepository.findById(po.getPayOrderId()).orElse(null);
                if (fresh == null || fresh.getOrderState() == null || fresh.getOrderState() != -1) {
                    continue;
                }
                // 条件原子更新：仅当仍为 -1-待支付 时置为 3-关闭（数据库层兜底，防并发竞态）
                int updated = payOrderRepository.closeIfUnpaid(po.getPayOrderId(), LocalDateTime.now());
                if (updated == 0) continue;
                for (BizOrder biz : bizOrderRepository.findByPayOrderId(po.getPayOrderId())) {
                    biz.setState(4);
                    biz.setUpdatedAt(LocalDateTime.now());
                    bizOrderRepository.save(biz);
                }
                // 超时关单退回核销的优惠券（恢复为未使用，可再次抵扣）
                refundCouponByOrder(po.getMchOrderNo());
                log.info("订单超时自动关闭：{}", po.getPayOrderId());
            }
        }
    }

    /**
     * 按商户订单号退回核销的优惠券（订单取消 / 超时关闭时调用）。
     * 找到 usedOrderNum 等于该订单号的券，恢复为未使用并清空核销信息。
     *
     * @param mchOrderNo 商户订单号
     */
    @Transactional
    public void refundCouponByOrder(String mchOrderNo) {
        if (mchOrderNo == null || mchOrderNo.isBlank()) return;
        for (MktCoupon c : mktCouponRepository.findByUsedOrderNum(mchOrderNo)) {
            c.setState(0);
            c.setUsedAt(null);
            c.setUsedOrderNum(null);
            mktCouponRepository.save(c);
            log.info("优惠券已退回：id={} order={}", c.getId(), mchOrderNo);
        }
    }

    /* ==================== private ==================== */

    /**
     * 支付方式 → 所属支付接口编码：优先同码渠道，否则取下划线前缀（如 ALIPAY_WAP → ALIPAY）。
     * pay_channel（支付接口，管理员「支付接口」页维护）与 pay_way（支付方式，管理员「支付方式」页维护）
     * 通过该规则关联。
     */
    public static String channelCodeOf(String wayCode) {
        if (wayCode == null) return null;
        int i = wayCode.indexOf('_');
        return i > 0 ? wayCode.substring(0, i) : wayCode;
    }

    /**
     * 支付方式是否真实可用（与用户端可选列表同口径）：
     * 支付方式本身启用（pay_way.state=1）且 所属支付接口启用（pay_channel.state=1，接口未配置视为可用）。
     */
    public boolean isWayEnabled(String wayCode) {
        if (wayCode == null) return false;
        PayWay way = payWayRepository.findById(wayCode).orElse(null);
        if (way == null || way.getState() == null || way.getState() != 1) return false;
        PayChannel ch = payChannelRepository.findById(wayCode)
                .orElseGet(() -> payChannelRepository.findById(channelCodeOf(wayCode)).orElse(null));
        return ch == null || ch.getState() == null || ch.getState() == 1;
    }

    /** 查询支付方式所属支付接口（优先同码，否则下划线前缀），未配置返回 null */
    public PayChannel channelOf(String wayCode) {
        if (wayCode == null) return null;
        return payChannelRepository.findById(wayCode)
                .orElseGet(() -> payChannelRepository.findById(channelCodeOf(wayCode)).orElse(null));
    }

    /**
     * 调用 mall-alipay 支付微服务执行渠道下单（预创建）。
     * 组装 payOrderId/wayCode/money/subject/shopName/returnUrl 请求体，
     * POST {alipayBaseUrl}/alipay/precreate；响应 id='0' 视为下单成功，否则抛出 BizException。
     *
     * @param payOrder 待下单的支付单（提供渠道、金额、标题等信息）
     * @return 渠道返回 Map（含 payUrl 收银台地址、channelOrderNo 渠道流水号等）
     */
    private Map<String, Object> requestChannelPrecreate(PayOrder payOrder) {
        try {
            Map<String, Object> req = new HashMap<>();
            req.put("payOrderId", payOrder.getPayOrderId());
            req.put("wayCode", payOrder.getWayCode());
            req.put("money", payOrder.getMoney().toPlainString());
            req.put("subject", payOrder.getSubject());
            req.put("shopName", payOrder.getShopName());
            if (alipayReturnUrl != null && !alipayReturnUrl.isBlank()) {
                req.put("returnUrl", alipayReturnUrl);
            }
            @SuppressWarnings("unchecked")
            Map<String, Object> resp = restTemplate.postForObject(alipayBaseUrl + "/alipay/precreate", req, Map.class);
            if (resp != null && "0".equals(String.valueOf(resp.get("id")))) {
                return resp;
            }
            throw new BizException("渠道下单失败：" + (resp != null ? resp.get("message") : "无响应"));
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            log.error("调用支付微服务失败", e);
            throw new BizException("支付渠道服务不可用，请确认 mall-alipay 已启动");
        }
    }

    /**
     * 主动对账：向支付微服务查询沙箱/渠道侧支付状态（无公网异步通知时的状态同步手段）
     * 返回 tradeStatus：TRADE_SUCCESS/TRADE_FINISHED=已支付；WAIT_BUYER_PAY=未支付；其他=未知
     */
    public Map<String, Object> queryChannel(String payOrderId) {
        try {
            Map<String, Object> req = new HashMap<>();
            req.put("payOrderId", payOrderId);
            @SuppressWarnings("unchecked")
            Map<String, Object> resp = restTemplate.postForObject(alipayBaseUrl + "/alipay/query", req, Map.class);
            return resp == null ? Map.of() : resp;
        } catch (Exception e) {
            log.error("渠道对账失败：{}", e.getMessage());
            return Map.of();
        }
    }

    /**
     * 解析支付费率：商户差异化费率 mchRate 优先，否则取渠道(wayCode)基础费率。
     *
     * @param merchant 商户对象（可能含 mchRate，格式如 "0.6%"）
     * @param wayCode  支付渠道编码
     * @return 费率 BigDecimal（如 0.6 表示 0.6%）
     */
    private BigDecimal resolveRate(Merchant merchant, String wayCode) {
        if (merchant.getMchRate() != null && !merchant.getMchRate().isBlank()) {
            try {
                return new BigDecimal(merchant.getMchRate().replace("%", ""));
            } catch (NumberFormatException ignore) { }
        }
        return payChannelRepository.findById(wayCode)
                .map(c -> new BigDecimal(c.getRate() == null ? "0" : c.getRate().replace("%", "")))
                .orElse(BigDecimal.ZERO);
    }

    /**
     * 向商户站内信表写入一条通知（如「新订单提醒」），状态默认 0=未读。
     *
     * @param shopNum  商户编号
     * @param shopName 商户名称
     * @param title    通知标题
     * @param content  通知正文
     * @param type     通知类型
     */
    private void notice(String shopNum, String shopName, String title, String content, String type) {
        MchNotice n = new MchNotice();
        n.setShopNum(shopNum);
        n.setShopName(shopName);
        n.setTitle(title);
        n.setContent(content);
        n.setNoticeType(type);
        n.setState(0);
        n.setCreatedAt(LocalDateTime.now());
        mchNoticeRepository.save(n);
    }

    /**
     * 将对象转为去空白字符串：null 或空串返回 null，便于缺省/必填参数的判定。
     */
    private static String str(Object o) {
        if (o == null) return null;
        String s = String.valueOf(o).trim();
        return s.isEmpty() ? null : s;
    }

    /**
     * BigDecimal 空值兜底：null 返回 0，用于商户余额/收入等金额累加的安全计算。
     */
    private static BigDecimal nvl(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }

    /**
     * 买家姓名脱敏：仅保留首字符，其余以 ** 代替（如「张三」->「张**」），用于履约单展示。
     */
    private static String maskName(String name) {
        if (name == null || name.length() <= 1) return name + "**";
        return name.charAt(0) + "**";
    }
}
