package com.mall.mgr.repository;

import com.mall.mgr.entity.PayChannel;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 支付渠道配置（PayChannel）的数据访问接口。
 * 对应数据库表：pay_channel。所属模块：mall-mgr（平台管理服务）。
 */
public interface PayChannelRepository extends JpaRepository<PayChannel, String> {
}
