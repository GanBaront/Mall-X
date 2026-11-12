package com.mall.mgr.repository;

import com.mall.mgr.entity.MchNotice;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import java.util.List;

/**
 * 商户通知（MchNotice）的数据访问接口。
 * 对应数据库表：mch_notice。所属模块：mall-mgr（平台管理服务）。
 */
public interface MchNoticeRepository extends JpaRepository<MchNotice, Long>, JpaSpecificationExecutor<MchNotice> {
    /** 根据商户编号查询其全部通知，按创建时间倒序 */
    List<MchNotice> findByShopNumOrderByCreatedAtDesc(String shopNum);
}
