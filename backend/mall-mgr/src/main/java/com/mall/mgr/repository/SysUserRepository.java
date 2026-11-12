package com.mall.mgr.repository;

import com.mall.mgr.entity.SysUser;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

/**
 * 系统用户（SysUser）的数据访问接口。
 * 对应数据库表：sys_user。所属模块：mall-mgr（平台管理服务）。
 */
public interface SysUserRepository extends JpaRepository<SysUser, Long> {
    /** 根据登录账号查询系统用户（用于登录与权限校验） */
    Optional<SysUser> findByUsername(String username);
}
