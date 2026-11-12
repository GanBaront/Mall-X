package com.mall.mgr.entity;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

/**
 * 服务商（ISV）实体。
 * 平台侧对接的第三方服务商/机构信息。对应数据库表：sys_isv。所属模块：mall-mgr（平台管理服务）。
 */
@Data
@Entity
@Table(name = "sys_isv")
public class Isv {
    /** 服务商编号（主键） */
    @Id
    private String isvNum;

    /** 服务商名称 */
    private String isvName;

    /** 联系电话 */
    private String linkPhone;

    /** 联系人 */
    private String contact;

    /** 应用ID */
    private String appId;

    /** 状态（如 0=禁用, 1=启用） */
    private Integer state;

    /** 创建时间 */
    private LocalDateTime createdAt;
}
