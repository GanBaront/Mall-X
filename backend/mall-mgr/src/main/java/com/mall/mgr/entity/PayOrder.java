package com.mall.mgr.entity;

import jakarta.persistence.*;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 支付订单实体 —— 记录一笔支付单的完整生命周期（创建→支付成功/失败→退款/关闭）。
 * 对应数据库表：pay_order。所属模块：mall-mgr（平台管理服务）。
 * 支付状态（orderState）：-1=待支付, 0=支付成功, 1=支付失败, 2=已退款, 3=订单关闭。
 */
@Data
@Entity
@Table(name = "pay_order", indexes = @Index(name = "uk_mch_order_no", columnList = "mchOrderNo", unique = true))
public class PayOrder {
    /** 支付订单ID（主键） */
    @Id
    private String payOrderId;

    /** 商户侧唯一订单号 */
    @Column(unique = true)
    private String mchOrderNo;

    /** 商户/店铺编号 */
    private String shopNum;

    /** 商户名称 */
    private String shopName;

    /** 商户支付应用ID */
    private String appId;

    /** 支付方式编码 */
    private String wayCode;

    /** 支付金额 */
    private BigDecimal money;

    /** 已退款金额 */
    private BigDecimal refundMoney;

    /** 手续费 */
    private BigDecimal fee;

    /** 下单时费率快照 */
    private String rate;

    /** 支付状态：-1=待支付, 0=支付成功, 1=支付失败, 2=已退款, 3=订单关闭 */
    private Integer orderState;

    /** 订单标题/商品名 */
    private String subject;

    /** 订单描述/详情 */
    private String body;

    /** 付款用户编号 */
    private String userNum;

    /** 下单使用的优惠券 ID（mkt_coupon，未用券为空） */
    private Long couponId;

    /** 优惠券抵扣金额（元，未用券为 0） */
    private BigDecimal couponDiscount;

    /** 第三方渠道订单号 */
    private String channelOrderNo;

    /** 创建时间 */
    private LocalDateTime createdAt;

    /** 支付成功时间 */
    private LocalDateTime payTime;

    /** 更新时间 */
    private LocalDateTime updatedAt;
}
