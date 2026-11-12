package com.mall.mgr.repository;

import com.mall.mgr.entity.PayTransfer;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import java.util.List;

/**
 * 转账/提现订单（PayTransfer）的数据访问接口。
 * 对应数据库表：pay_transfer。所属模块：mall-mgr（平台管理服务）。
 * 状态（state）：0=处理中, 1=成功, 2=失败。
 */
public interface PayTransferRepository extends JpaRepository<PayTransfer, String>, JpaSpecificationExecutor<PayTransfer> {
    /** 根据商户编号查询其全部转账/提现记录，按创建时间倒序 */
    List<PayTransfer> findByShopNumOrderByCreatedAtDesc(String shopNum);
}
