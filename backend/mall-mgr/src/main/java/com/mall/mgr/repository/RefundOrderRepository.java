package com.mall.mgr.repository;

import com.mall.mgr.entity.RefundOrder;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 退款订单（RefundOrder）的数据访问接口。
 * 对应数据库表：refund_order。所属模块：mall-mgr（平台管理服务）。
 * 退款状态（refundState）：0=审核中, 1=退款成功, 2=退款失败。
 */
public interface RefundOrderRepository extends JpaRepository<RefundOrder, String>, JpaSpecificationExecutor<RefundOrder> {
    /** 根据支付订单ID查询其全部退款单 */
    List<RefundOrder> findByPayOrderId(String payOrderId);
    /** 根据商户编号分页查询退款单列表 */
    Page<RefundOrder> findByShopNum(String shopNum, Pageable pageable);
    /** 根据商户编号查询其全部退款单 */
    List<RefundOrder> findByShopNum(String shopNum);
    /** 根据退款状态与退款时间区间查询退款单（用于统计某时段内特定状态的退款） */
    List<RefundOrder> findByRefundStateAndRefundTimeBetween(Integer refundState, LocalDateTime start, LocalDateTime end);
    /** 根据退款状态查询退款单列表 */
    List<RefundOrder> findByRefundState(Integer refundState);

    /** 统计指定时间区间内退款成功（refundState=1）的退款总金额 */
    @Query("select coalesce(sum(r.refundMoney), 0) from RefundOrder r where r.refundState = 1 and r.refundTime between :start and :end")
    Double sumRefundedBetween(LocalDateTime start, LocalDateTime end);
}
