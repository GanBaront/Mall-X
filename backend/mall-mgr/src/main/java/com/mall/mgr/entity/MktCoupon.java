package com.mall.mgr.entity;

import jakarta.persistence.*;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 营销优惠券/红包实体（领券中心幸运转盘发放，购物支付时可抵扣）。
 * 对应数据库表：mkt_coupon。所属模块：mall-mgr（平台管理服务）。
 * 类型（type）：1=折扣券（按 discountRate 打折，如 0.85 表示 8.5 折）；2=现金红包（直接抵扣 amount 元）。
 * 状态（state）：0=未使用，1=已使用（核销于 usedOrderNum 订单）。
 */
@Data
@Entity
@Table(name = "mkt_coupon")
public class MktCoupon {

    /** 券ID（主键，自增） */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 归属用户编号（如 Uuser） */
    private String userNum;

    /** 券名称（如「¥5 现金红包」「8.5 折优惠券」） */
    private String name;

    /** 类型：1=折扣券，2=现金红包 */
    private Integer type;

    /** 折扣率（type=1 时有效，如 0.85 表示 8.5 折） */
    private BigDecimal discountRate;

    /** 现金抵扣金额（type=2 时有效，单位元） */
    private BigDecimal amount;

    /** 状态：0=未使用，1=已使用 */
    private Integer state;

    /** 过期时间（发券时间 + 15 天，过期后不可用于下单抵扣；历史数据为空时按 createdAt+15 天推算） */
    private LocalDateTime expireAt;

    /** 获得时间（转盘抽取时间） */
    private LocalDateTime createdAt;

    /** 核销时间（下单使用时间，未使用为空） */
    private LocalDateTime usedAt;

    /** 核销订单号（使用该券的商户订单号，未使用为空；订单关闭时据此退回券） */
    private String usedOrderNum;

    /** 优惠券有效期（天），发券时起算 */
    public static final int VALID_DAYS = 15;

    /** 是否已过期：expireAt 有值按 expireAt 判定；历史数据 expireAt 为空时按 createdAt + 15 天推算 */
    public boolean isExpired() {
        LocalDateTime now = LocalDateTime.now();
        if (expireAt != null) return expireAt.isBefore(now);
        return createdAt != null && createdAt.plusDays(VALID_DAYS).isBefore(now);
    }
}
