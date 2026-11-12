package com.mall.mgr.entity;

import jakarta.persistence.*;
import lombok.Data;

/**
 * 支付方式实体（如微信支付、支付宝、银行卡等）。
 * 对应数据库表：pay_way。所属模块：mall-mgr（平台管理服务）。
 */
@Data
@Entity
@Table(name = "pay_way")
public class PayWay {
    /** 支付方式编码（主键） */
    @Id
    private String wayCode;

    /** 支付方式名称 */
    private String name;

    /** 支付方式类型 */
    private String wayType;

    /** 状态（如 0=停用, 1=启用） */
    private Integer state;

    /** 费率 */
    private String rate;

    /** 备注 */
    private String remark;
}
