package com.mall.mgr.repository;

import com.mall.mgr.entity.Merchant;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.Optional;

/**
 * 入驻商户（Merchant）的数据访问接口。
 * 对应数据库表：mch_shop。所属模块：mall-mgr（平台管理服务）。
 */
public interface MerchantRepository extends JpaRepository<Merchant, String>, JpaSpecificationExecutor<Merchant> {
    /** 根据登录账号查询商户（用于登录与唯一性校验） */
    Optional<Merchant> findByUsername(String username);
    /** 根据商户状态分页查询商户列表（如查询待审核/正常/禁用商户） */
    Page<Merchant> findByShopState(Integer shopState, Pageable pageable);

    /**
     * 提现扣款条件更新：仅当余额充足（balance >= amount）时原子扣减余额并累计已提现。
     * 数据库层原子操作，防止并发两笔提现同时读到原余额造成双花。
     * @return 影响行数（0=余额不足，提现失败）
     */
    @Modifying
    @Query("UPDATE Merchant m SET m.balance = m.balance - :amount, m.withdrawn = COALESCE(m.withdrawn, 0) + :amount WHERE m.shopNum = :shopNum AND m.balance >= :amount")
    int withdrawIfEnough(@Param("shopNum") String shopNum, @Param("amount") BigDecimal amount);
}
