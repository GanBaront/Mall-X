package com.mall.mgr.repository;

import com.mall.mgr.entity.SysLog;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 后台操作日志（SysLog）的数据访问接口。
 * 对应数据库表：sys_log。所属模块：mall-mgr（平台管理服务）。
 */
public interface SysLogRepository extends JpaRepository<SysLog, Long> {
}
