package com.mall.mgr.service;

import com.mall.mgr.common.BizException;
import com.mall.mgr.entity.BizOrder;
import com.mall.mgr.entity.Merchant;
import com.mall.mgr.entity.MktCoupon;
import com.mall.mgr.entity.PayOrder;
import com.mall.mgr.entity.RefundOrder;
import com.mall.mgr.repository.BizOrderRepository;
import com.mall.mgr.repository.MerchantRepository;
import com.mall.mgr.repository.MktCouponRepository;
import com.mall.mgr.repository.PayOrderRepository;
import com.mall.mgr.repository.ProductRepository;
import com.mall.mgr.repository.RefundOrderRepository;
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

/**
 * 退款核心逻辑（指导书 4.4）
 * 状态：0-审核中 1-退款成功 2-退款失败
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RefundService {

    private final RefundOrderRepository refundOrderRepository;
    private final PayOrderRepository payOrderRepository;
    private final MerchantRepository merchantRepository;
    private final BizOrderRepository bizOrderRepository;
    private final MktCouponRepository mktCouponRepository;
    private final ProductRepository productRepository;

    @Value("${mall.alipay.base-url}")
    private String alipayBaseUrl;

    private final RestTemplate restTemplate = new RestTemplate();
    private static final DateTimeFormatter TS = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");
    private static final Random RANDOM = new Random();
    /** 并发防重锁：payOrderId/refundOrderId -> 锁（同一支付单的售后申请、同一退款单的处理串行执行） */
    private final java.util.concurrent.ConcurrentHashMap<String, Object> refundLocks = new java.util.concurrent.ConcurrentHashMap<>();

    /**
     * 商户发起退款（指导书 4.4.1）。
     * 校验支付单存在、归属商户、状态为支付成功(0)，并校验退款金额不超过可退余额；
     * 幂等：若已存在「审核中(0)」的退款单则拒绝重复发起。通过后生成退款单（状态 0=审核中）。
     * 退款单状态机：0=审核中，1=成功，2=失败。
     *
     * @param payload 退款参数 Map：payOrderId(原支付单号)、refundMoney(退款金额)、
     *                refundReason(退款原因)、shopNum(商户编号，用于越权校验)
     * @return 新建的退款单 RefundOrder
     */
    @Transactional
    public RefundOrder createRefund(Map<String, Object> payload) {
        String payOrderId = str(payload.get("payOrderId"));
        String refundReason = str(payload.get("refundReason"));
        String shopNum = str(payload.get("shopNum"));
        BigDecimal refundMoney;
        try {
            refundMoney = payload.get("refundMoney") == null ? null : new BigDecimal(String.valueOf(payload.get("refundMoney")));
        } catch (NumberFormatException e) {
            throw new BizException("退款金额格式无效");
        }
        if (payOrderId == null || refundMoney == null || refundReason == null) {
            throw new BizException("缺少必要参数：payOrderId/refundMoney/refundReason");
        }
        if (refundMoney.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BizException("退款金额必须大于 0");
        }
        PayOrder payOrder = payOrderRepository.findById(payOrderId)
                .orElseThrow(() -> new BizException("原支付订单不存在"));

        if (shopNum == null || !shopNum.equals(payOrder.getShopNum())) {
            throw new BizException("无权对该订单发起退款");
        }
        if (payOrder.getOrderState() == null || payOrder.getOrderState() != 0) {
            throw new BizException("仅支付成功的订单可发起退款");
        }
        // 幂等：存在审核中/成功的退款单则拒绝重复发起
        for (RefundOrder r : refundOrderRepository.findByPayOrderId(payOrderId)) {
            if (r.getRefundState() != null && r.getRefundState() == 0) {
                throw new BizException("该订单已有退款申请审核中");
            }
        }
        BigDecimal refundable = payOrder.getMoney().subtract(nvl(payOrder.getRefundMoney()));
        if (refundMoney.compareTo(refundable) > 0) {
            throw new BizException("退款金额超过可退金额");
        }

        RefundOrder refund = new RefundOrder();
        refund.setRefundOrderId("R" + LocalDateTime.now().format(TS) + String.format("%06d", RANDOM.nextInt(1000000)));
        refund.setPayOrderId(payOrderId);
        refund.setShopNum(payOrder.getShopNum());
        refund.setRefundMoney(refundMoney.setScale(2, RoundingMode.HALF_UP));
        refund.setRefundState(0);
        refund.setRefundReason(refundReason);
        refund.setApplyTime(LocalDateTime.now());
        refundOrderRepository.save(refund);
        return refund;
    }

    /**
     * 用户申请售后/退款（购物端「我的订单」，仅限本人订单）。
     * 校验：订单存在且属于当前用户、状态为 1-待发货（仅退款）或 3-已完成（售后退款）、
     * 支付单已支付成功、该订单无审核中(0)退款且未全额退款过。
     * 退款金额 = 支付单可退余额（实付 - 已退，即全额退款）；
     * 生成退款单（0=审核中）并将订单置为 5-售后中，等待商户在「售后退款」页处理。
     *
     * @param orderNum 商户订单号
     * @param reason   售后/退款原因
     * @param userNum  当前登录用户编号（网关透传）
     * @return 新建的退款单
     */
    @Transactional
    public RefundOrder applyRefundByUser(String orderNum, String reason, String userNum) {
        if (orderNum == null || reason == null) {
            throw new BizException("缺少必要参数：orderNum/refundReason");
        }
        BizOrder biz = bizOrderRepository.findById(orderNum)
                .orElseThrow(() -> new BizException("订单不存在"));
        if (!userNum.equals(biz.getUserNum())) {
            throw new BizException("无权操作该订单");
        }
        if (biz.getState() == null || (biz.getState() != 1 && biz.getState() != 3)) {
            throw new BizException("仅待发货或已完成订单可申请售后");
        }
        PayOrder payOrder = payOrderRepository.findById(biz.getPayOrderId())
                .orElseThrow(() -> new BizException("原支付订单不存在"));
        if (payOrder.getOrderState() == null || payOrder.getOrderState() != 0) {
            throw new BizException("仅支付成功的订单可申请售后");
        }
        // 并发防重：同一支付单的售后申请串行执行，防止并发重复提交生成两张全额退款单
        Object lock = refundLocks.computeIfAbsent(payOrder.getPayOrderId(), k -> new Object());
        synchronized (lock) {
            return doApplyRefundByUser(biz, reason, userNum, payOrder);
        }
    }

    /** 售后申请的实际执行（调用方已持有 payOrderId 锁） */
    private RefundOrder doApplyRefundByUser(BizOrder biz, String reason, String userNum, PayOrder payOrder) {
        // 幂等：已有审核中的售后申请则拒绝重复提交（持锁后复查，双保险）
        for (RefundOrder r : refundOrderRepository.findByPayOrderId(payOrder.getPayOrderId())) {
            if (r.getRefundState() != null && r.getRefundState() == 0) {
                throw new BizException("该订单已有售后申请审核中");
            }
        }
        BigDecimal refundable = payOrder.getMoney().subtract(nvl(payOrder.getRefundMoney()));
        if (refundable.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BizException("该订单已全额退款");
        }

        // 售前状态先于入库记录（拒绝时回退依据），必须在 save 前设置
        int bizPreState = biz.getState() == null ? 0 : biz.getState();
        RefundOrder refund = new RefundOrder();
        refund.setRefundOrderId("R" + LocalDateTime.now().format(TS) + String.format("%06d", RANDOM.nextInt(1000000)));
        refund.setPayOrderId(payOrder.getPayOrderId());
        refund.setShopNum(payOrder.getShopNum());
        refund.setRefundMoney(refundable.setScale(2, RoundingMode.HALF_UP));
        refund.setRefundState(0);
        refund.setRefundReason(reason);
        refund.setBizPreState(bizPreState);
        refund.setApplyTime(LocalDateTime.now());
        refundOrderRepository.save(refund);

        // 订单进入售后中（5）：商户同意→退款并关闭(4)；拒绝→回退原状态
        biz.setState(5);
        biz.setUpdatedAt(LocalDateTime.now());
        bizOrderRepository.save(biz);

        // 申请售后即从商户经营数据中扣除该订单（营业额/可提现余额/手续费）；
        // 仅已完成(3)订单此前已结算入账需扣回；待发货(1)订单货款仍在托管中、从未入账，无需扣减。
        if (bizPreState == 3) {
            merchantRepository.findById(biz.getShopNum()).ifPresent(m -> {
                m.setTotalIncome(nvl(m.getTotalIncome()).subtract(nvl(payOrder.getMoney())).max(BigDecimal.ZERO));
                m.setBalance(nvl(m.getBalance()).subtract(nvl(payOrder.getMoney()).subtract(nvl(payOrder.getFee()))).max(BigDecimal.ZERO));
                m.setTotalFee(nvl(m.getTotalFee()).subtract(nvl(payOrder.getFee())).max(BigDecimal.ZERO));
                merchantRepository.save(m);
            });
        }
        return refund;
    }

    /**
     * 退款处理（审核打款）。
     * agree=true：调用 mall-alipay 执行渠道退款，退款单->1(成功)，累加支付单已退金额，
     *             若已退金额>=原金额则支付单->2(已退款)（商户经营数据已在申请售后时扣除）；
     *             同时关联的用户侧订单置为 4-已关闭（退款完成）。
     * agree=false：退款单->2(失败)，用户侧订单回退到申请售后前的状态（1-待发货/3-已完成），
     *             并恢复申请售后时扣除的商户经营数据（仅已完成订单扣过账）。
     * 重复处理保护：退款单状态非 0(审核中) 时直接抛异常，避免重复打款。
     *
     * @param refundOrderId 退款单号
     * @param agree         true=同意退款并打款，false=拒绝
     * @return 统一响应 Map（message 处理说明、refundState 最新退款状态）
     */
    @Transactional
    public Map<String, Object> handleRefund(String refundOrderId, boolean agree) {
        // 并发防重：同一退款单的处理串行执行，防止重复打款
        Object lock = refundLocks.computeIfAbsent(refundOrderId, k -> new Object());
        synchronized (lock) {
            return doHandleRefund(refundOrderId, agree);
        }
    }

    /** 退款处理的实际执行（调用方已持有 refundOrderId 锁） */
    private Map<String, Object> doHandleRefund(String refundOrderId, boolean agree) {
        RefundOrder refund = refundOrderRepository.findById(refundOrderId)
                .orElseThrow(() -> new BizException("退款订单不存在"));
        if (refund.getRefundState() != 0) {
            throw new BizException("该退款申请已处理，请勿重复操作");
        }
        LocalDateTime now = LocalDateTime.now();
        PayOrder payOrder = payOrderRepository.findById(refund.getPayOrderId()).orElse(null);
        if (agree) {
            // 打款前校验：累计退款 + 本次退款不得超过订单实付金额（防并发双倍打款）
            if (payOrder != null) {
                BigDecimal refundable = payOrder.getMoney().subtract(nvl(payOrder.getRefundMoney()));
                if (refund.getRefundMoney() == null || refund.getRefundMoney().compareTo(refundable) > 0) {
                    throw new BizException("退款金额超过该订单可退金额，请人工核查");
                }
            }
            // 调用支付微服务执行渠道退款（模拟）
            channelRefund(refund.getPayOrderId(), refund.getRefundMoney().toPlainString());
            refund.setRefundState(1);
            refund.setRefundTime(now);

            if (payOrder != null) {
                payOrder.setRefundMoney(nvl(payOrder.getRefundMoney()).add(refund.getRefundMoney()));
                boolean fullRefund = payOrder.getRefundMoney().compareTo(payOrder.getMoney()) >= 0;
                if (fullRefund) {
                    payOrder.setOrderState(2); // 全额退款
                }
                payOrder.setUpdatedAt(now);
                payOrderRepository.save(payOrder);
                // 销量回落、库存回补：仅全额退款时执行（部分退款交易仍成立，商品不回库、销量不冲减）
                if (fullRefund) {
                    for (BizOrder biz : bizOrderRepository.findByPayOrderId(refund.getPayOrderId())) {
                        if (biz.getProductId() == null) continue;
                        productRepository.findById(biz.getProductId()).ifPresent(p -> {
                            int num = biz.getNum() == null ? 1 : biz.getNum();
                            p.setSales(Math.max((p.getSales() == null ? 0 : p.getSales()) - num, 0));
                            p.setStock((p.getStock() == null ? 0 : p.getStock()) + num);
                            productRepository.save(p);
                        });
                    }
                }
                // 注：申请售后时已从商户经营数据中扣除该订单，此处不再重复扣减
                // 全额退款时退回下单使用的优惠券（券恢复为未使用，可再次抽奖/使用）
                if (payOrder.getCouponId() != null
                        && payOrder.getRefundMoney().compareTo(payOrder.getMoney()) >= 0) {
                    mktCouponRepository.findById(payOrder.getCouponId()).ifPresent(c -> {
                        c.setState(0);
                        c.setUsedAt(null);
                        c.setUsedOrderNum(null);
                        mktCouponRepository.save(c);
                        log.info("售后全额退款，优惠券已退回：id={} order={}", c.getId(), refund.getPayOrderId());
                    });
                }
            }
        } else {
            refund.setRefundState(2);
            refund.setRefundTime(now);
            // 拒绝后订单回退到售后前状态，交易继续成立 → 恢复申请售后时扣除的商户经营数据
            // 仅已完成(3)订单申请时扣过账；待发货(1)订单货款未结算，无需恢复
            if (refund.getBizPreState() != null && refund.getBizPreState() == 3) {
                bizOrderRepository.findByPayOrderId(refund.getPayOrderId()).stream().findFirst().ifPresent(biz ->
                        payOrderRepository.findById(refund.getPayOrderId()).ifPresent(po ->
                                merchantRepository.findById(biz.getShopNum()).ifPresent(m -> {
                                    m.setTotalIncome(nvl(m.getTotalIncome()).add(nvl(po.getMoney())));
                                    m.setBalance(nvl(m.getBalance()).add(nvl(po.getMoney())).subtract(nvl(po.getFee())));
                                    m.setTotalFee(nvl(m.getTotalFee()).add(nvl(po.getFee())));
                                    merchantRepository.save(m);
                                })));
            }
        }
        refundOrderRepository.save(refund);

        // 同步用户侧订单状态：全额退款→已关闭(4)；部分退款→回退售后前状态（交易仍成立，商品照常发货/已完成）；
        // 拒绝→回退到售后前状态（1-待发货/3-已完成）
        boolean fullRefund = agree && payOrder != null
                && nvl(payOrder.getRefundMoney()).compareTo(payOrder.getMoney()) >= 0;
        for (BizOrder biz : bizOrderRepository.findByPayOrderId(refund.getPayOrderId())) {
            if (agree && !fullRefund) {
                biz.setState(refund.getBizPreState() != null ? refund.getBizPreState() : biz.getState());
            } else {
                biz.setState(agree ? 4 : (refund.getBizPreState() != null ? refund.getBizPreState() : biz.getState()));
            }
            biz.setUpdatedAt(now);
            bizOrderRepository.save(biz);
        }
        return Map.of(
                "message", agree ? "已同意退款，等待平台审核打款" : "已拒绝退款",
                "refundState", refund.getRefundState()
        );
    }

    /**
     * 调用 mall-alipay 支付微服务执行渠道退款（模拟即时打款）。
     * POST {alipayBaseUrl}/alipay/refund；响应 id='0' 视为成功，否则抛出 BizException。
     *
     * @param payOrderId  原支付单号
     * @param refundMoney 退款金额（字符串）
     */
    private void channelRefund(String payOrderId, String refundMoney) {
        try {
            Map<String, Object> req = new HashMap<>();
            req.put("payOrderId", payOrderId);
            req.put("refundMoney", refundMoney);
            @SuppressWarnings("unchecked")
            Map<String, Object> resp = restTemplate.postForObject(alipayBaseUrl + "/alipay/refund", req, Map.class);
            if (resp == null || !"0".equals(String.valueOf(resp.get("id")))) {
                throw new BizException("渠道退款失败：" + (resp != null ? resp.get("message") : "无响应"));
            }
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            log.error("调用支付微服务退款失败", e);
            throw new BizException("支付渠道服务不可用，请确认 mall-alipay 已启动");
        }
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
     * BigDecimal 空值兜底：null 返回 0，用于退款金额累加的安全计算。
     */
    private static BigDecimal nvl(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }
}
