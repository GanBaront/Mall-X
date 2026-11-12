package com.mall.mgr.service;

import com.mall.mgr.entity.PayOrder;
import com.mall.mgr.entity.RefundOrder;
import com.mall.mgr.repository.PayOrderRepository;
import com.mall.mgr.repository.RefundOrderRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 订单超时自动关闭定时任务（mall-mgr 平台管理服务）。
 * 职责：周期性触发 PayService.closeTimeoutOrders，将超过配置时长仍未支付的订单自动置为关闭，
 * 释放库存占用并同步关闭用户侧履约单。
 * 说明：生产环境用 RocketMQ 延迟消息精确触发，课程环境以 Spring @Scheduled 定时任务等价实现。
 */
@Component
@RequiredArgsConstructor
public class OrderScheduler {

    private final PayService payService;

    @Value("${mall.order.pay-timeout-minutes:30}")
    private int timeoutMinutes;

    /**
     * 定时扫描并关闭超时未支付订单。
     * 每 60 秒（fixedDelay）触发一次，将超时时长 timeoutMinutes（默认 30 分钟，由
     * mall.order.pay-timeout-minutes 配置）交给 PayService 执行关单逻辑。
     * 生产环境应改用 RocketMQ 延迟消息精确触发，此处以定时任务等价实现。
     */
    @Scheduled(fixedDelay = 60_000)
    public void closeTimeoutOrders() {
        payService.closeTimeoutOrders(timeoutMinutes);
    }
}
