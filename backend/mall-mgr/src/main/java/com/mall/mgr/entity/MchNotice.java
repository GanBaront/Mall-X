package com.mall.mgr.entity;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

/**
 * 商户通知实体。
 * 平台向商户推送的各类通知（转账/退款/系统/审核/订单）。对应数据库表：mch_notice。所属模块：mall-mgr（平台管理服务）。
 */
@Data
@Entity
@Table(name = "mch_notice")
public class MchNotice {
    /** 通知ID（主键，自增） */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 商户/店铺编号 */
    private String shopNum;

    /** 商户名称 */
    private String shopName;

    /** 通知标题 */
    private String title;

    @Column(length = 1000)
    /** 通知内容 */
    private String content;

    /** 转账通知/退款通知/系统通知/审核通知/订单通知 */
    private String noticeType;

    /** 1-已读 0-未读 */
    private Integer state;

    /** 创建时间 */
    private LocalDateTime createdAt;
}
