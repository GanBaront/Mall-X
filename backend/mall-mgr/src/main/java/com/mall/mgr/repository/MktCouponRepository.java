package com.mall.mgr.repository;

import com.mall.mgr.entity.MktCoupon;
import org.springframework.data.jpa.repository.JpaRepository;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 营销优惠券（MktCoupon）的数据访问接口。
 * 对应数据库表：mkt_coupon。所属模块：mall-mgr（平台管理服务）。
 */
public interface MktCouponRepository extends JpaRepository<MktCoupon, Long> {

    /** 我的全部卡券（按获得时间倒序） */
    List<MktCoupon> findByUserNumOrderByCreatedAtDesc(String userNum);

    /** 某用户在给定时间之后获得的券（用于每日抽奖次数限制） */
    List<MktCoupon> findByUserNumAndCreatedAtAfter(String userNum, LocalDateTime after);

    /** 按核销订单号查券（订单取消/超时关闭时退回券） */
    List<MktCoupon> findByUsedOrderNum(String usedOrderNum);

    /** 某用户的未使用卡券（下单选券时展示） */
    List<MktCoupon> findByUserNumAndState(String userNum, Integer state);
}
