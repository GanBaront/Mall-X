package com.mall.mgr.repository;

import com.mall.mgr.entity.Isv;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 服务商（Isv）的数据访问接口。
 * 对应数据库表：sys_isv。所属模块：mall-mgr（平台管理服务）。
 */
public interface IsvRepository extends JpaRepository<Isv, String> {
}
