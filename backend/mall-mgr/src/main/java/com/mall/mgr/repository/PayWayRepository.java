package com.mall.mgr.repository;

import com.mall.mgr.entity.PayWay;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 支付方式（PayWay）的数据访问接口。
 * 对应数据库表：pay_way。所属模块：mall-mgr（平台管理服务）。
 */
public interface PayWayRepository extends JpaRepository<PayWay, String> {
}
