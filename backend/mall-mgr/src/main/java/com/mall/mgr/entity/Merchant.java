package com.mall.mgr.entity;

import jakarta.persistence.*;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 入驻商户实体。
 * 记录平台入驻商户的基本资料、审核状态与资金账户（累计营收/手续费/可提现/已提现）。
 * 对应数据库表：mch_shop。所属模块：mall-mgr（平台管理服务）。
 * 商户状态（shopState）：0=待审核, 1=正常, 2=禁用（含审核驳回）。
 */
@Data
@Entity
@Table(name = "mch_shop")
public class Merchant {
    /** 商户/店铺编号（主键） */
    @Id
    private String shopNum;

    /** 登录账号（唯一） */
    @Column(unique = true)
    private String username;

    /** 登录密码 */
    private String password;

    /** 店铺名称 */
    private String shopName;

    /** 店铺联系电话 */
    private String shopPhone;

    /** 店铺地址 */
    private String shopAddress;

    /** 店铺简介 */
    private String shopDesc;

    /** 0-待审核 1-正常 2-禁用（禁用含审核驳回） */
    private Integer shopState;

    /** 商户差异化费率（如 "0.6%"），优先级高于渠道基础费率 */
    private String mchRate;

    /** 审核备注（驳回时填写原因） */
    private String auditRemark;

    /** 入驻申请时间 */
    private LocalDateTime applyTime;

    /** 资金：累计营收 / 累计手续费 / 可提现余额 / 已提现 */
    private BigDecimal totalIncome;
    private BigDecimal totalFee;
    private BigDecimal balance;
    private BigDecimal withdrawn;

    /** 创建时间 */
    private LocalDateTime createdAt;
}
