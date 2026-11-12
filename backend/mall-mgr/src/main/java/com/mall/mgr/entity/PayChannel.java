package com.mall.mgr.entity;

import jakarta.persistence.*;
import lombok.Data;

/**
 * 支付渠道配置实体（微信/支付宝/银联等第三方支付渠道）。
 * 记录渠道的接入参数与基础费率。对应数据库表：pay_channel。所属模块：mall-mgr（平台管理服务）。
 */
@Data
@Entity
@Table(name = "pay_channel")
public class PayChannel {
    /** 支付方式编码（主键） */
    @Id
    private String wayCode;

    /** 渠道名称 */
    private String name;

    /** 1-启用 0-停用 */
    private Integer state;

    /** 渠道应用ID */
    private String appId;

    /** 渠道应用密钥 */
    private String appSecret;

    /** 渠道商户号 */
    private String mchId;

    /** 渠道API密钥（如微信支付V3密钥） */
    private String apiV3Key;

    /** 渠道基础费率，如 "0.3%" */
    private String rate;

    /** 备注 */
    private String remark;
}
