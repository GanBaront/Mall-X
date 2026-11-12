package com.mall.mgr.controller;

import com.mall.mgr.common.BizException;
import com.mall.mgr.common.R;
import com.mall.mgr.common.SignUtil;
import com.mall.mgr.common.UserContext;
import com.mall.mgr.entity.BizOrder;
import com.mall.mgr.entity.PayOrder;
import com.mall.mgr.entity.PayWay;
import com.mall.mgr.repository.BizOrderRepository;
import com.mall.mgr.repository.PayOrderRepository;
import com.mall.mgr.repository.PayWayRepository;
import com.mall.mgr.service.PayService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 支付交易与订单接口（mall-mgr 平台管理服务，指导书 4.2 / 4.3）
 * <p>所属模块：mall-mgr 平台管理服务；接口前缀：/pay
 * <p>接口清单：/pay/create 统一下单、/pay/callback 支付回调（幂等）、/pay/query 订单查询、
 * /pay/list/mch 商户订单分页。
 * 说明：回调与查询均经 MD5 签名校验（mall.sign.required 开关，默认 false 关闭）；
 * 支付订单 orderState：-1=待支付, 0=成功, 1=失败, 2=已退款, 3=关闭。
 */
@RestController
@RequiredArgsConstructor
public class PayController {

    private final PayService payService;
    private final PayOrderRepository payOrderRepository;
    private final PayWayRepository payWayRepository;
    private final BizOrderRepository bizOrderRepository;
    private final SignUtil signUtil;
    private final UserContext userContext;

    private static final DateTimeFormatter DT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    /**
     * 当前可用支付方式列表（POST /api/pay/ways，游客可访问）。
     * 后端实时计算可用性：支付方式启用（pay_way.state=1）且 所属支付接口启用（pay_channel.state=1）。
     * 管理员在「支付接口」/「支付方式」页停用后，本列表即时生效，用户端下单页据此渲染。
     * 返回：records（wayCode/name/wayType/rate）。
     */
    @PostMapping("/api/pay/ways")
    public LinkedHashMap<String, Object> availableWays() {
        List<Map<String, Object>> records = new ArrayList<>();
        for (PayWay w : payWayRepository.findAll()) {
            if (!payService.isWayEnabled(w.getWayCode())) continue;
            Map<String, Object> vo = new HashMap<>();
            vo.put("wayCode", w.getWayCode());
            vo.put("name", w.getName());
            vo.put("wayType", w.getWayType());
            vo.put("rate", w.getRate());
            records.add(vo);
        }
        LinkedHashMap<String, Object> resp = R.ok("查询成功");
        resp.put("records", records);
        return resp;
    }

    /** 4.2.1 统一支付下单 */
    @PostMapping("/pay/create")
    public LinkedHashMap<String, Object> create(@RequestBody Map<String, Object> body, HttpServletRequest request) {
        Map<String, Object> data = payService.createPayOrder(body, userContext.username(request), userContext.userNum(request));
        return R.ok("下单成功", data);
    }

    /**
     * 4.2.2 支付结果回调（幂等）。
     * 回调端点在网关白名单中免登录，验签为硬性要求（force=true，不受 mall.sign.required 开关影响），
     * 防止任何人伪造支付成功。
     */
    @PostMapping("/pay/callback")
    public LinkedHashMap<String, Object> callback(@RequestBody Map<String, Object> body) {
        if (!signUtil.verify(body, true)) {
            throw new BizException("回调验签失败");
        }
        payService.handleCallback(body);
        return R.ok("回调处理成功");
    }

    /** 4.3.1 订单查询（未支付订单自动向支付宝沙箱主动对账同步状态；登录商户/用户仅可查询本人订单） */
    @PostMapping("/pay/query")
    public LinkedHashMap<String, Object> query(@RequestBody Map<String, Object> body, HttpServletRequest request) {
        if (!signUtil.verify(body)) throw new BizException("验签失败");
        String payOrderNum = str(body.get("payOrderNum"));
        if (payOrderNum == null) throw new BizException("缺少参数：payOrderNum");
        PayOrder po = payOrderRepository.findById(payOrderNum).orElse(null);
        if (po == null) {
            // 兼容传商户订单号
            po = payOrderRepository.findByMchOrderNo(payOrderNum).orElse(null);
        }
        if (po == null) throw new BizException("支付订单不存在");
        // 归属校验：商户登录（num=shopNum）只能查本店订单；用户登录（num=U 开头）只能查本人订单
        String loginUserNum = userContext.userNum(request);
        if (loginUserNum != null && !loginUserNum.isBlank() && !"null".equals(loginUserNum)) {
            if (loginUserNum.equals(po.getShopNum()) || loginUserNum.equals(po.getUserNum())) {
                // 本人订单，放行
            } else {
                throw new BizException("无权查询该订单");
            }
        }
        // 待支付单：向渠道主动对账（仅支付宝渠道），支付成功则走回调记账（幂等）
        if (po.getOrderState() != null && po.getOrderState() == -1
                && po.getWayCode() != null && po.getWayCode().startsWith("ALIPAY")) {
            Map<String, Object> ch = payService.queryChannel(po.getPayOrderId());
            String tradeStatus = String.valueOf(ch.get("tradeStatus"));
            if ("TRADE_SUCCESS".equals(tradeStatus) || "TRADE_FINISHED".equals(tradeStatus)) {
                // 安全校验：渠道返回金额必须与本地订单一致（数值比较，避免 "10.00" vs "10.0" 格式误判），防止串单
                String chAmount = String.valueOf(ch.get("totalAmount"));
                if (po.getMoney() != null && chAmount != null) {
                    try {
                        if (new BigDecimal(chAmount).compareTo(po.getMoney()) != 0) {
                            throw new BizException("渠道金额与订单不一致，请人工核查：" + po.getPayOrderId());
                        }
                    } catch (NumberFormatException e) {
                        throw new BizException("渠道金额格式异常，请人工核查：" + po.getPayOrderId());
                    }
                }
                Map<String, Object> cb = new HashMap<>();
                cb.put("payOrderId", po.getPayOrderId());
                cb.put("channelOrderNo", ch.get("channelOrderNo"));
                cb.put("payState", 0);
                cb.put("payTime", ch.get("payTime"));
                payService.handleCallback(cb);
                po = payOrderRepository.findById(po.getPayOrderId()).orElse(po);
            }
        }
        HashMap<String, Object> data = new HashMap<>();
        data.put("payOrderId", po.getPayOrderId());
        data.put("mchOrderNo", po.getMchOrderNo());
        data.put("appId", po.getAppId());
        data.put("wayCode", po.getWayCode());
        data.put("money", po.getMoney() == null ? "0" : po.getMoney().toPlainString());
        data.put("subject", po.getSubject());
        data.put("createdAt", po.getCreatedAt() == null ? "" : po.getCreatedAt().format(DT));
        data.put("payTime", po.getPayTime() == null ? "" : po.getPayTime().format(DT));
        data.put("orderState", po.getOrderState());
        return R.ok("查询订单成功", data);
    }

    /** 4.3.2 商户订单列表分页（合并交易履约单信息，供商户端展示；keyword 匹配订单号/商品名/买家；商户号一律取登录态） */
    @PostMapping("/pay/list/mch")
    public LinkedHashMap<String, Object> mchOrderList(@RequestBody Map<String, Object> body, HttpServletRequest request) {
        String shopNum = userContext.userNum(request);
        if (shopNum == null || shopNum.isBlank() || "null".equals(shopNum)) throw new BizException("请先登录商户账号");
        int pageNum = intVal(body.get("pageNum"), 1);
        int pageSize = intVal(body.get("pageSize"), 10);
        Integer orderState = body.get("orderState") != null ? Integer.parseInt(String.valueOf(body.get("orderState"))) : null;
        String wayCode = str(body.get("wayCode"));
        String keyword = str(body.get("keyword"));

        Specification<PayOrder> spec = (root, q, cb) -> {
            var preds = new ArrayList<jakarta.persistence.criteria.Predicate>();
            preds.add(cb.equal(root.get("shopNum"), shopNum));
            if (orderState != null) preds.add(cb.equal(root.get("orderState"), orderState));
            if (wayCode != null) preds.add(cb.equal(root.get("wayCode"), wayCode));
            if (keyword != null) {
                String like = "%" + keyword + "%";
                preds.add(cb.or(
                        cb.like(root.get("mchOrderNo"), like),
                        cb.like(root.get("subject"), like),
                        cb.like(root.get("userNum"), like)
                ));
            }
            return cb.and(preds.toArray(new jakarta.persistence.criteria.Predicate[0]));
        };
        Page<PayOrder> page = payOrderRepository.findAll(spec,
                PageRequest.of(pageNum - 1, pageSize, Sort.by(Sort.Direction.DESC, "createdAt")));

        List<Map<String, Object>> records = new ArrayList<>();
        for (PayOrder po : page.getContent()) {
            Map<String, Object> row = new HashMap<>();
            row.put("payOrderId", po.getPayOrderId());
            row.put("mchOrderNo", po.getMchOrderNo());
            row.put("orderNum", po.getMchOrderNo());
            row.put("shopNum", po.getShopNum());
            row.put("shopName", po.getShopName());
            row.put("wayCode", po.getWayCode()); // 原始编码，前端自行映射展示
            row.put("money", po.getMoney() == null ? "0" : po.getMoney().toPlainString());
            row.put("fee", po.getFee() == null ? "0" : po.getFee().toPlainString());
            row.put("refundMoney", po.getRefundMoney() == null ? "0" : po.getRefundMoney().toPlainString());
            row.put("payOrderState", po.getOrderState());
            row.put("createdAt", po.getCreatedAt() == null ? "" : po.getCreatedAt().format(DT));
            row.put("payTime", po.getPayTime() == null ? "" : po.getPayTime().format(DT));
            // 合并履约单信息（商品、买家、物流等）
            List<BizOrder> bizs = bizOrderRepository.findByPayOrderId(po.getPayOrderId());
            if (!bizs.isEmpty()) {
                BizOrder biz = bizs.get(0);
                row.put("orderState", biz.getState());
                row.put("state", biz.getState());
                row.put("goodsName", biz.getGoodsName());
                row.put("image", biz.getImage());
                row.put("num", biz.getNum());
                row.put("total", biz.getTotal() == null ? "0" : biz.getTotal().toPlainString());
                row.put("buyer", biz.getBuyer());
                row.put("address", biz.getAddress());
                row.put("logisticsNo", biz.getLogisticsNo() == null ? "" : biz.getLogisticsNo());
            } else {
                row.put("orderState", po.getOrderState());
                row.put("state", po.getOrderState());
                row.put("goodsName", po.getSubject());
                row.put("image", "");
                row.put("num", 1);
                row.put("total", po.getMoney() == null ? "0" : po.getMoney().toPlainString());
                row.put("buyer", "");
                row.put("address", "");
                row.put("logisticsNo", "");
            }
            records.add(row);
        }
        LinkedHashMap<String, Object> resp = R.ok("查询成功");
        R.putPage(resp, page.getTotalElements(), pageNum, pageSize, records);
        return resp;
    }

    /** 支付渠道编码转中文展示名（WECHAT→微信、ALIPAY→支付宝、UNIONPAY→银联） */
    static String cnWay(String wayCode) {
        if (wayCode == null) return "";
        return switch (wayCode) {
            case "WECHAT" -> "微信";
            case "ALIPAY", "ALIPAY_WAP" -> "支付宝";
            case "UNIONPAY" -> "银联";
            default -> wayCode;
        };
    }

    static String str(Object o) {
        if (o == null) return null;
        String s = String.valueOf(o).trim();
        return s.isEmpty() ? null : s;
    }

    static int intVal(Object o, int def) {
        try {
            return o == null ? def : Integer.parseInt(String.valueOf(o));
        } catch (NumberFormatException e) {
            return def;
        }
    }
}
