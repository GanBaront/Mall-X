package com.mall.mgr.entity;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

/**
 * 后台操作日志实体 —— 记录管理员在平台后台的关键操作行为。
 * 对应数据库表：sys_log。所属模块：mall-mgr（平台管理服务）。
 */
@Data
@Entity
@Table(name = "sys_log")
public class SysLog {
    /** 日志ID（主键，自增） */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 操作人用户名 */
    private String username;

    /** 操作模块 */
    private String module;

    /** 操作动作 */
    private String action;

    /** 操作来源IP */
    private String ip;

    /** 成功/失败 */
    private String result;

    /** 创建时间 */
    private LocalDateTime createdAt;
}
