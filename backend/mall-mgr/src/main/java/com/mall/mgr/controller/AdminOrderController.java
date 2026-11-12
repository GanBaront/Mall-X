package com.mall.mgr.controller;

import com.mall.mgr.common.BizException;
import com.mall.mgr.common.PageUtil;
import com.mall.mgr.common.R;
import com.mall.mgr.entity.PayOrder;
import com.mall.mgr.entity.PayTransfer;
import com.mall.mgr.entity.RefundOrder;
import com.mall.mgr.repository.PayOrderRepository;
import com.mall.mgr.repository.PayTransferRepository;
import com.mall.mgr.repository.RefundOrderRepository;
import com.mall.mgr.repository.MerchantRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 平台管理端：支付订单 / 退款订单 / 转账订单（mall-mgr 平台管理服务，指导书 4.3 / 4.4 管控侧）
 * <p>所属模块：mall-mgr 平台管理服务；接口前缀：/admin
 * <p>接口清单：/admin/pay/order/list、/admin/refund/order/list、/admin/transfer/order/list。
 * 多条件筛选与分页均在后端完成；支付订单状态 0=成功,1=失败,2=已退款,3=关闭；退款单 0=审核中,1=成功,2=失败。
 */
@RestController
@RequiredArgsConstructor
public class AdminOrderController {

    private final PayOrderRepository payOrderRepository;
    private final RefundOrderRepository refundOrderRepository;
    private final PayTransferRepository payTransferRepository;
    private final MerchantRepository merchantRepository;

    private static final DateTimeFormatter DT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    /** 平台支付订单分页（多条件筛选由后端完成） */
    @PostMapping("/admin/pay/order/list")
    public LinkedHashMap<String, Object> payOrderList(@RequestBody Map<String, Object> params) {
        Integer orderState = intOrNull(params.get("orderState"));
        String wayCode = PayController.str(params.get("wayCode"));
        String shopNum = PayController.str(params.get("shopNum"));
        String shopName = PayController.str(params.get("shopName"));
        String payOrderId = PayController.str(params.get("payOrderId"));
        String keyword = PayController.str(params.get("keyword"));
        String startTime = PayController.str(params.get("startTime"));
        String endTime = PayController.str(params.get("endTime"));

        List<PayOrder> filtered = payOrderRepository.findAll().stream()
                .filter(p -> orderState == null || orderState.equals(p.getOrderState()))
                .filter(p -> wayCode == null || wayCode.equals(p.getWayCode()))
                .filter(p -> shopNum == null || shopNum.equals(p.getShopNum()))
                .filter(p -> shopName == null || contains(p.getShopName(), shopName))
                .filter(p -> payOrderId == null || contains(p.getPayOrderId(), payOrderId)
                        || contains(p.getMchOrderNo(), payOrderId))
                .filter(p -> matchKeyword(p, keyword))
                .filter(p -> inTimeRange(p.getCreatedAt() == null ? null : p.getCreatedAt().format(DT), startTime, endTime))
                .sorted((a, b) -> b.getCreatedAt() == null ? -1
                        : (a.getCreatedAt() == null ? 1 : b.getCreatedAt().compareTo(a.getCreatedAt())))
                .collect(Collectors.toList());

        LinkedHashMap<String, Object> resp = R.ok("查询成功");
        resp.put("total", filtered.size());
        resp.put("records", PageUtil.paginate(filtered, params, this::payOrderVo));
        return resp;
    }

    /** 平台退款订单分页（退款单号/原支付单号/商户名称/状态/申请时间筛选由后端完成） */
    @PostMapping("/admin/refund/order/list")
    public LinkedHashMap<String, Object> refundOrderList(@RequestBody Map<String, Object> params) {
        Integer refundState = intOrNull(params.get("refundState"));
        String shopNum = PayController.str(params.get("shopNum"));
        String shopName = PayController.str(params.get("shopName"));
        String refundOrderId = PayController.str(params.get("refundOrderId"));
        String payOrderId = PayController.str(params.get("payOrderId"));
        String keyword = PayController.str(params.get("keyword"));
        String startTime = PayController.str(params.get("startTime"));
        String endTime = PayController.str(params.get("endTime"));

        List<RefundOrder> filtered = refundOrderRepository.findAll().stream()
                .filter(r -> refundState == null || refundState.equals(r.getRefundState()))
                .filter(r -> shopNum == null || shopNum.equals(r.getShopNum()))
                .filter(r -> refundOrderId == null || contains(r.getRefundOrderId(), refundOrderId))
                .filter(r -> payOrderId == null || contains(r.getPayOrderId(), payOrderId))
                .filter(r -> keyword == null || contains(r.getRefundOrderId(), keyword)
                        || contains(r.getPayOrderId(), keyword))
                .filter(r -> shopName == null || merchantRepository.findById(r.getShopNum())
                        .map(m -> contains(m.getShopName(), shopName)).orElse(false))
                .filter(r -> inTimeRange(r.getApplyTime() == null ? null : r.getApplyTime().format(DT), startTime, endTime))
                .sorted((a, b) -> b.getApplyTime() == null ? -1
                        : (a.getApplyTime() == null ? 1 : b.getApplyTime().compareTo(a.getApplyTime())))
                .collect(Collectors.toList());

        LinkedHashMap<String, Object> resp = R.ok("查询成功");
        resp.put("total", filtered.size());
        resp.put("records", PageUtil.paginate(filtered, params, r -> {
            Map<String, Object> vo = new HashMap<>();
            vo.put("refundOrderId", r.getRefundOrderId());
            vo.put("payOrderId", r.getPayOrderId());
            vo.put("shopNum", r.getShopNum());
            merchantRepository.findById(r.getShopNum()).ifPresent(m -> vo.put("shopName", m.getShopName()));
            vo.put("refundMoney", r.getRefundMoney() == null ? "0" : r.getRefundMoney().toPlainString());
            vo.put("refundState", r.getRefundState());
            vo.put("refundReason", r.getRefundReason());
            vo.put("applyTime", r.getApplyTime() == null ? "" : r.getApplyTime().format(DT));
            vo.put("refundTime", r.getRefundTime() == null ? "" : r.getRefundTime().format(DT));
            return vo;
        }));
        return resp;
    }

    /** 平台转账（提现）订单分页（单号/商户名称/状态/时间筛选由后端完成） */
    @PostMapping("/admin/transfer/order/list")
    public LinkedHashMap<String, Object> transferList(@RequestBody Map<String, Object> params) {
        Integer state = intOrNull(params.get("state"));
        String shopNum = PayController.str(params.get("shopNum"));
        String shopName = PayController.str(params.get("shopName"));
        String transferId = PayController.str(params.get("transferId"));
        String startTime = PayController.str(params.get("startTime"));
        String endTime = PayController.str(params.get("endTime"));
        List<PayTransfer> filtered = payTransferRepository.findAll().stream()
                .filter(t -> state == null || state.equals(t.getState()))
                .filter(t -> shopNum == null || shopNum.equals(t.getShopNum()))
                .filter(t -> transferId == null || contains(t.getTransferId(), transferId))
                .filter(t -> shopName == null || contains(t.getShopName(), shopName))
                .filter(t -> inTimeRange(t.getCreatedAt() == null ? null : t.getCreatedAt().format(DT), startTime, endTime))
                .sorted((a, b) -> b.getCreatedAt() == null ? -1
                        : (a.getCreatedAt() == null ? 1 : b.getCreatedAt().compareTo(a.getCreatedAt())))
                .collect(Collectors.toList());
        LinkedHashMap<String, Object> resp = R.ok("查询成功");
        resp.put("total", filtered.size());
        resp.put("records", PageUtil.paginate(filtered, params, t -> {
            Map<String, Object> vo = new HashMap<>();
            vo.put("transferId", t.getTransferId());
            vo.put("shopNum", t.getShopNum());
            vo.put("shopName", t.getShopName());
            vo.put("entryAccount", t.getEntryAccount());
            vo.put("entryName", t.getEntryName());
            vo.put("amount", t.getAmount() == null ? "0" : t.getAmount().toPlainString());
            vo.put("state", t.getState());
            vo.put("createdAt", t.getCreatedAt() == null ? "" : t.getCreatedAt().format(DT));
            vo.put("finishTime", t.getFinishTime() == null ? "" : t.getFinishTime().format(DT));
            return vo;
        }));
        return resp;
    }

    /**
     * 平台处理提现申请（POST /admin/transfer/order/handle）
     * <p>body：transferId、approve（true=打款成功 / false=驳回）。
     * 同意：转账单 0(处理中)→1(成功)（申请时已扣减商户余额与累计已提现，无需重复记账）；
     * 驳回：转账单 0→2(失败)，退回商户可提现余额并扣减累计已提现。
     */
    @PostMapping("/admin/transfer/order/handle")
    @org.springframework.transaction.annotation.Transactional
    public LinkedHashMap<String, Object> transferHandle(@RequestBody Map<String, Object> body) {
        String transferId = PayController.str(body.get("transferId"));
        boolean approve = Boolean.parseBoolean(String.valueOf(body.get("approve")));
        if (transferId == null) throw new BizException("缺少参数：transferId");
        PayTransfer t = payTransferRepository.findById(transferId)
                .orElseThrow(() -> new BizException("转账订单不存在"));
        if (t.getState() == null || t.getState() != 0) {
            throw new BizException("该提现申请已处理，请勿重复操作");
        }
        LocalDateTime now = LocalDateTime.now();
        if (approve) {
            t.setState(1);
        } else {
            t.setState(2);
            // 驳回：提现金额退回商户可提现余额，累计已提现同步扣回（事务内与转账单同批提交）
            merchantRepository.findById(t.getShopNum()).ifPresent(m -> {
                m.setBalance(nvl(m.getBalance()).add(nvl(t.getAmount())));
                m.setWithdrawn(nvl(m.getWithdrawn()).subtract(nvl(t.getAmount())).max(java.math.BigDecimal.ZERO));
                merchantRepository.save(m);
            });
        }
        t.setFinishTime(now);
        payTransferRepository.save(t);
        Map<String, Object> data = new HashMap<>();
        data.put("transferId", t.getTransferId());
        data.put("state", t.getState());
        return R.ok(approve ? "提现打款成功" : "提现申请已驳回，金额已退回商户余额", data);
    }

    private static java.math.BigDecimal nvl(java.math.BigDecimal v) {
        return v == null ? java.math.BigDecimal.ZERO : v;
    }

    private Map<String, Object> payOrderVo(PayOrder p) {
        Map<String, Object> vo = new HashMap<>();
        vo.put("payOrderId", p.getPayOrderId());
        vo.put("mchOrderNo", p.getMchOrderNo());
        vo.put("shopNum", p.getShopNum());
        vo.put("shopName", p.getShopName());
        vo.put("appId", p.getAppId());
        vo.put("wayCode", p.getWayCode()); // 原始编码，前端自行映射展示
        vo.put("money", p.getMoney() == null ? "0" : p.getMoney().toPlainString());
        vo.put("refundMoney", p.getRefundMoney() == null ? "0" : p.getRefundMoney().toPlainString());
        vo.put("fee", p.getFee() == null ? "0" : p.getFee().toPlainString());
        vo.put("orderState", p.getOrderState());
        vo.put("subject", p.getSubject());
        vo.put("body", p.getBody());
        vo.put("rate", p.getRate());
        vo.put("createdAt", p.getCreatedAt() == null ? "" : p.getCreatedAt().format(DT));
        vo.put("payTime", p.getPayTime() == null ? "" : p.getPayTime().format(DT));
        vo.put("updatedAt", p.getUpdatedAt() == null ? "" : p.getUpdatedAt().format(DT));
        return vo;
    }

    private boolean matchKeyword(PayOrder p, String keyword) {
        if (keyword == null) return true;
        return contains(p.getPayOrderId(), keyword) || contains(p.getMchOrderNo(), keyword)
                || contains(p.getSubject(), keyword) || contains(p.getShopName(), keyword);
    }

    private boolean contains(String s, String keyword) {
        return s != null && s.contains(keyword);
    }

    private boolean inTimeRange(String dateStr, String startTime, String endTime) {
        if (dateStr == null) return startTime == null && endTime == null;
        if (startTime != null && dateStr.compareTo(startTime) < 0) return false;
        if (endTime != null && dateStr.compareTo(endTime) > 0) return false;
        return true;
    }

    private Integer intOrNull(Object o) {
        if (o == null || String.valueOf(o).isEmpty()) return null;
        try {
            return Integer.parseInt(String.valueOf(o));
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
