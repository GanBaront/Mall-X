package com.mall.mgr.entity;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

/**
 * 系统用户实体（管理员/普通用户共用一张表，role 区分；商户独立 mch_shop 表）。
 * 对应数据库表：sys_user。所属模块：mall-mgr（平台管理服务）。
 * 角色（role）：ROLE_SUPER_ADMIN=超级管理员, ROLE_OPS=运营, ROLE_AUDIT=审核, ROLE_USER=普通用户。
 */
@Data
@Entity
@Table(name = "sys_user")
public class SysUser {
    /** 用户ID（主键，自增） */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 登录账号（唯一） */
    @Column(unique = true)
    private String username;

    /** 登录密码 */
    private String password;

    /** 真实姓名 */
    private String realName;

    /** 昵称（购物端个人中心展示用，为空时前端回退显示账号） */
    private String nickname;

    /** 头像图片访问路径（如 /upload/avatar_xxx.jpg，相对网关根路径；未上传为空） */
    private String avatar;

    /** ROLE_SUPER_ADMIN / ROLE_OPS / ROLE_AUDIT / ROLE_USER */
    private String role;

    /** 1-正常 0-禁用 */
    private Integer state;

    /** 创建时间 */
    private LocalDateTime createdAt;
}
