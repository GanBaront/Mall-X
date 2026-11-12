package com.mall.mgr.entity;

import jakarta.persistence.*;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 转账/提现订单实体 —— 记录商户提现与平台转账的资金结算单据。
 * 对应数据库表：pay_transfer。所属模块：mall-mgr（平台管理服务）。
 * 状态（state）：0=处理中, 1=成功, 2=失败。
 */
@Data
@Entity
@Table(name = "pay_transfer")
public class PayTransfer {
    /** 转账/提现单号（主键） */
    @Id
    private String transferId;

    /** 商户/店铺编号 */
    private String shopNum;

    /** 商户名称 */
    private String shopName;

    /** 收款账户 */
    private String entryAccount;

    /** 收款人姓名 */
    private String entryName;

    /** 转账金额 */
    private BigDecimal amount;

    /** 状态：0=处理中, 1=成功, 2=失败 */
    private Integer state;

    /** 创建时间 */
    private LocalDateTime createdAt;

    /** 完成时间 */
    private LocalDateTime finishTime;
}
