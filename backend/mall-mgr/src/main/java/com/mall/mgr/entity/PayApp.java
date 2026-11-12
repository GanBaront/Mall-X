package com.mall.mgr.entity;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

/**
 * 商户支付应用实体。
 * 商户在平台开通的支付应用（一个商户可拥有多个应用）。对应数据库表：mch_app。所属模块：mall-mgr（平台管理服务）。
 */
@Data
@Entity
@Table(name = "mch_app")
public class PayApp {
    /** 应用ID（主键） */
    @Id
    private String appId;

    /** 应用名称 */
    private String appName;

    /** 商户/店铺编号 */
    private String shopNum;

    /** 1-启用 0-停用 */
    private Integer state;

    /** 备注 */
    private String remark;

    /** 创建时间 */
    private LocalDateTime createdAt;
}
