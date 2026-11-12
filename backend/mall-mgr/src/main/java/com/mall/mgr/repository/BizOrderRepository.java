package com.mall.mgr.repository;

import com.mall.mgr.entity.BizOrder;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 购物订单（BizOrder）的数据访问接口。
 * 对应数据库表：biz_order。所属模块：mall-mgr（平台管理服务）。
 */
public interface BizOrderRepository extends JpaRepository<BizOrder, String> {
    /** 根据用户编号查询其全部订单，按创建时间倒序 */
    List<BizOrder> findByUserNumOrderByCreatedAtDesc(String userNum);
    /** 根据商户编号查询其店铺全部订单，按创建时间倒序 */
    List<BizOrder> findByShopNumOrderByCreatedAtDesc(String shopNum);
    /** 根据支付订单ID查询关联的业务订单 */
    List<BizOrder> findByPayOrderId(String payOrderId);
    /** 根据商户编号与订单状态查询订单列表 */
    List<BizOrder> findByShopNumAndState(String shopNum, Integer state);

    /**
     * 确认收货条件更新：仅当订单当前为 2-待收货 时置为 3-已完成。
     * 数据库层原子操作，防止并发重复确认导致商户资金双重入账。
     * @return 影响行数（0=状态已变化，本次不生效）
     */
    @Modifying
    @Query("UPDATE BizOrder b SET b.state = 3, b.finishTime = :now, b.updatedAt = :now WHERE b.orderNum = :orderNum AND b.state = 2")
    int confirmReceipt(@Param("orderNum") String orderNum, @Param("now") LocalDateTime now);

    /**
     * 取消订单条件更新：仅当订单当前为 0-待支付 时置为 4-已关闭。
     * 数据库层原子操作，防止并发下重复取消/取消与支付竞态。
     * @return 影响行数（0=状态已变化，本次不生效）
     */
    @Modifying
    @Query("UPDATE BizOrder b SET b.state = 4, b.updatedAt = :now WHERE b.orderNum = :orderNum AND b.state = 0")
    int cancelIfUnpaid(@Param("orderNum") String orderNum, @Param("now") LocalDateTime now);
}
