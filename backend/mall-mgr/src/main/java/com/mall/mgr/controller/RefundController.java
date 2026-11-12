package com.mall.mgr.controller;

import com.mall.mgr.common.BizException;
import com.mall.mgr.common.R;
import com.mall.mgr.common.UserContext;
import com.mall.mgr.entity.PayOrder;
import com.mall.mgr.entity.RefundOrder;
import com.mall.mgr.repository.PayOrderRepository;
import com.mall.mgr.repository.RefundOrderRepository;
import com.mall.mgr.service.RefundService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 退款接口（mall-mgr 平台管理服务，指导书 4.4）
 * <p>所属模块：mall-mgr 平台管理服务；接口前缀：/refund、/api/mch
 * <p>退款单状态：0=审核中、1=成功、2=失败。接口清单：
 * - POST /refund/create          商户发起退款
 * - POST /refund/query           退款结果查询（带 refundOrderId 查单条；不带则分页查当前商户退款列表）
 * - POST /api/mch/refund/handle  商户处理退款（同意/拒绝）
 */
@RestController
@RequiredArgsConstructor
public class RefundController {

    private final RefundService refundService;
    private final RefundOrderRepository refundOrderRepository;
    private final PayOrderRepository payOrderRepository;
    private final UserContext userContext;

    private static final DateTimeFormatter DT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    /**
     * 商户发起退款（POST /refund/create）
     * <p>商户号一律取网关透传登录态（商户登录 token 即 shopNum），请求体 shopNum 不采信，防止代他人商户发起退款；
     * 委托 RefundService 创建退款单（初始状态 0=审核中）。
     * 关键参数：payOrderId、refundMoney、refundReason 等。
     * 返回：refundOrderId，提示“退款申请提交成功，等待审核”。
     */
    @PostMapping("/refund/create")
    public LinkedHashMap<String, Object> create(@RequestBody Map<String, Object> body, HttpServletRequest request) {
        String shopNum = userContext.userNum(request);
        if (shopNum == null || shopNum.isBlank() || "null".equals(shopNum)) {
            throw new BizException("请先登录商户账号");
        }
        body.put("shopNum", shopNum);
        RefundOrder refund = refundService.createRefund(body);
        Map<String, Object> data = new HashMap<>();
        data.put("refundOrderId", refund.getRefundOrderId());
        return R.ok("退款申请提交成功，等待审核", data);
    }

    /**
     * 退款结果查询（POST /refund/query）
     * <p>带 refundOrderId 则查询单条退款单（登录商户只能查本店退款单），否则按当前登录商户分页查询退款列表。
     * 关键参数：refundOrderId（可选）、pageNum、pageSize。
     * 返回：单条详情或 {total, pages, current, size, records} 分页结构。
     */
    @PostMapping("/refund/query")
    public LinkedHashMap<String, Object> query(@RequestBody Map<String, Object> body, HttpServletRequest request) {
        String loginShopNum = userContext.userNum(request);
        String refundOrderId = PayController.str(body.get("refundOrderId"));
        if (refundOrderId != null && !refundOrderId.isEmpty()) {
            RefundOrder r = refundOrderRepository.findById(refundOrderId)
                    .orElseThrow(() -> new BizException("退款订单不存在"));
            // 归属校验：登录商户仅可查询本店退款单（信息防泄露）
            if (loginShopNum != null && !loginShopNum.isBlank() && !"null".equals(loginShopNum)
                    && !loginShopNum.equals(r.getShopNum())) {
                throw new BizException("无权查询该退款订单");
            }
            LinkedHashMap<String, Object> resp = R.ok("查询成功");
            resp.putAll(refundVo(r));
            return resp;
        }
        // 商户退款列表：一律按登录态商户过滤
        String shopNum = loginShopNum;
        if (shopNum == null || shopNum.isBlank() || "null".equals(shopNum)) throw new BizException("请先登录");
        int pageNum = PayController.intVal(body.get("pageNum"), 1);
        int pageSize = PayController.intVal(body.get("pageSize"), 20);
        Page<RefundOrder> page = refundOrderRepository.findByShopNum(shopNum,
                PageRequest.of(pageNum - 1, pageSize, Sort.by(Sort.Direction.DESC, "applyTime")));
        List<Map<String, Object>> records = new ArrayList<>();
        for (RefundOrder r : page.getContent()) {
            Map<String, Object> vo = refundVo(r);
            payOrderRepository.findById(r.getPayOrderId()).ifPresent(po -> {
                vo.put("orderNum", po.getMchOrderNo());
                vo.put("goodsName", po.getSubject());
            });
            records.add(vo);
        }
        LinkedHashMap<String, Object> resp = R.ok("查询成功");
        R.putPage(resp, page.getTotalElements(), pageNum, pageSize, records);
        return resp;
    }

    /**
     * 用户申请售后/退款（POST /api/refund/apply，购物端「我的订单」）
     * <p>仅限本人订单（按网关透传 userNum 校验归属），状态须为 1-待发货 或 3-已完成；
     * 委托 RefundService.applyRefundByUser 创建退款单（0=审核中）并把订单置为 5-售后中。
     * 关键参数：orderNum、refundReason。
     */
    @PostMapping("/api/refund/apply")
    public LinkedHashMap<String, Object> apply(@RequestBody Map<String, Object> body, HttpServletRequest request) {
        String userNum = userContext.userNum(request);
        if (userNum == null || userNum.isBlank() || "null".equals(userNum)) {
            throw new BizException("登录状态无效，请重新登录");
        }
        String orderNum = PayController.str(body.get("orderNum"));
        String reason = PayController.str(body.get("refundReason"));
        RefundOrder refund = refundService.applyRefundByUser(orderNum, reason, userNum);
        Map<String, Object> data = new HashMap<>();
        data.put("refundOrderId", refund.getRefundOrderId());
        data.put("refundMoney", refund.getRefundMoney() == null ? "0" : refund.getRefundMoney().toPlainString());
        return R.ok("售后申请已提交，等待商家处理", data);
    }

    /**
     * 我的售后申请列表（POST /api/refund/my，购物端）
     * <p>按网关透传 userNum 过滤本人支付订单的退款记录，合并商品名/订单号。
     */
    @PostMapping("/api/refund/my")
    public LinkedHashMap<String, Object> myRefunds(@RequestBody Map<String, Object> body, HttpServletRequest request) {
        String userNum = userContext.userNum(request);
        if (userNum == null || userNum.isBlank() || "null".equals(userNum)) {
            throw new BizException("登录状态无效，请重新登录");
        }
        List<RefundOrder> mine = new ArrayList<>();
        for (PayOrder po : payOrderRepository.findAll()) {
            if (!userNum.equals(po.getUserNum())) continue;
            for (RefundOrder r : refundOrderRepository.findByPayOrderId(po.getPayOrderId())) {
                mine.add(r);
            }
        }
        mine.sort((a, b) -> {
            LocalDateTime ta = a.getApplyTime() == null ? LocalDateTime.MIN : a.getApplyTime();
            LocalDateTime tb = b.getApplyTime() == null ? LocalDateTime.MIN : b.getApplyTime();
            return tb.compareTo(ta);
        });
        List<Map<String, Object>> records = new ArrayList<>();
        for (RefundOrder r : mine) {
            Map<String, Object> vo = refundVo(r);
            payOrderRepository.findById(r.getPayOrderId()).ifPresent(po -> {
                vo.put("orderNum", po.getMchOrderNo());
                vo.put("goodsName", po.getSubject());
                vo.put("money", po.getMoney() == null ? "0" : po.getMoney().toPlainString());
            });
            records.add(vo);
        }
        LinkedHashMap<String, Object> resp = R.ok("查询成功");
        resp.put("records", records);
        return resp;
    }

    /**
     * 商户处理退款申请。
     * 归属校验：仅退款单所属商户（登录态 shopNum）可处理，防止任何商户同意/拒绝他人退款触发打款。
     */
    @PostMapping("/api/mch/refund/handle")
    public LinkedHashMap<String, Object> handle(@RequestBody Map<String, Object> body, HttpServletRequest request) {
        String refundOrderId = PayController.str(body.get("refundOrderId"));
        boolean agree = Boolean.parseBoolean(String.valueOf(body.get("agree")));
        if (refundOrderId == null) throw new BizException("缺少参数：refundOrderId");
        String shopNum = userContext.userNum(request);
        if (shopNum == null || shopNum.isBlank() || "null".equals(shopNum)) {
            throw new BizException("请先登录商户账号");
        }
        RefundOrder refund = refundOrderRepository.findById(refundOrderId)
                .orElseThrow(() -> new BizException("退款订单不存在"));
        if (!shopNum.equals(refund.getShopNum())) throw new BizException("无权处理其他商户的退款申请");
        Map<String, Object> data = refundService.handleRefund(refundOrderId, agree);
        return R.ok(String.valueOf(data.get("message")), data);
    }

    private Map<String, Object> refundVo(RefundOrder r) {
        Map<String, Object> vo = new HashMap<>();
        vo.put("refundOrderId", r.getRefundOrderId());
        vo.put("payOrderId", r.getPayOrderId());
        vo.put("shopNum", r.getShopNum());
        vo.put("refundMoney", r.getRefundMoney() == null ? "0" : r.getRefundMoney().toPlainString());
        vo.put("refundState", r.getRefundState());
        vo.put("state", r.getRefundState());
        vo.put("refundReason", r.getRefundReason());
        vo.put("reason", r.getRefundReason());
        vo.put("applyTime", r.getApplyTime() == null ? "" : r.getApplyTime().format(DT));
        vo.put("refundTime", r.getRefundTime() == null ? "" : r.getRefundTime().format(DT));
        return vo;
    }
}
