package com.mall.mgr.entity;

import jakarta.persistence.*;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 退款订单实体 —— 记录一笔支付订单的退款申请与处理过程。
 * 对应数据库表：refund_order。所属模块：mall-mgr（平台管理服务）。
 * 退款状态（refundState）：0=审核中, 1=退款成功, 2=退款失败。
 */
@Data
@Entity
@Table(name = "refund_order")
public class RefundOrder {
    /** 退款单号（主键） */
    @Id
    private String refundOrderId;

    /** 关联的支付订单ID */
    private String payOrderId;

    /** 商户/店铺编号 */
    private String shopNum;

    /** 退款金额 */
    private BigDecimal refundMoney;

    /** 退款状态：0=审核中, 1=退款成功, 2=退款失败 */
    private Integer refundState;

    /** 退款原因 */
    private String refundReason;

    /** 申请售后时的订单状态（1=待发货 / 3=已完成；商家拒绝售后后订单回退到该状态） */
    private Integer bizPreState;

    /** 申请时间 */
    private LocalDateTime applyTime;

    /** 退款完成时间 */
    private LocalDateTime refundTime;
}
