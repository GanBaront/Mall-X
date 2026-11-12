package com.mall.mgr.repository;

import com.mall.mgr.entity.PayApp;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

/**
 * 商户支付应用（PayApp）的数据访问接口。
 * 对应数据库表：mch_app。所属模块：mall-mgr（平台管理服务）。
 */
public interface PayAppRepository extends JpaRepository<PayApp, String> {
    /** 根据商户编号查询其名下的全部支付应用 */
    List<PayApp> findByShopNum(String shopNum);
}
