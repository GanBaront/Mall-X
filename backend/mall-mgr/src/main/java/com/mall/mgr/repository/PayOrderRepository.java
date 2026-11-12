package com.mall.mgr.repository;

import com.mall.mgr.entity.PayOrder;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 支付订单（PayOrder）的数据访问接口。
 * 对应数据库表：pay_order。所属模块：mall-mgr（平台管理服务）。
 * 支付状态（orderState）：-1=待支付, 0=支付成功, 1=支付失败, 2=已退款, 3=订单关闭。
 */
public interface PayOrderRepository extends JpaRepository<PayOrder, String>, JpaSpecificationExecutor<PayOrder> {
    /** 根据商户侧订单号查询支付订单（用于支付回调时定位订单） */
    java.util.Optional<PayOrder> findByMchOrderNo(String mchOrderNo);
    /** 根据商户编号分页查询支付订单列表 */
    Page<PayOrder> findByShopNum(String shopNum, Pageable pageable);
    /** 查询处于指定状态且创建时间早于给定时间的订单（用于超时关单等定时任务） */
    List<PayOrder> findByOrderStateAndCreatedAtBefore(Integer orderState, LocalDateTime time);
    /** 根据支付时间区间查询支付成功的订单 */
    List<PayOrder> findByPayTimeBetween(LocalDateTime start, LocalDateTime end);
    /** 根据商户编号与支付时间区间查询订单 */
    List<PayOrder> findByShopNumAndPayTimeBetween(String shopNum, LocalDateTime start, LocalDateTime end);

    /**
     * 超时关单条件更新：仅当订单仍为 -1-待支付 时置为 3-关闭。
     * 数据库层原子操作，避免与支付回调竞态（回调刚置 0 的订单不会被关单任务覆盖）。
     * @return 影响行数（0=状态已变化，本次不生效）
     */
    @Modifying
    @Query("UPDATE PayOrder p SET p.orderState = 3, p.updatedAt = :now WHERE p.payOrderId = :payOrderId AND p.orderState = -1")
    int closeIfUnpaid(@Param("payOrderId") String payOrderId, @Param("now") LocalDateTime now);

    /**
     * 取消订单时关闭支付单条件更新：仅当支付单仍为 -1-待支付 时置为 3-关闭。
     * 防止取消订单后用户仍可完成支付、回调把已关闭订单复活。
     * @return 影响行数（0=状态已变化，本次不生效）
     */
    @Modifying
    @Query("UPDATE PayOrder p SET p.orderState = 3, p.updatedAt = :now WHERE p.payOrderId = :payOrderId AND p.orderState = -1")
    int closeIfUnpaidById(@Param("payOrderId") String payOrderId, @Param("now") LocalDateTime now);
}
