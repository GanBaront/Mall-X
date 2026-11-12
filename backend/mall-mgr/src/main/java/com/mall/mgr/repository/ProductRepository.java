package com.mall.mgr.repository;

import com.mall.mgr.entity.Product;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import java.util.List;

/**
 * 商品（Product）的数据访问接口。
 * 对应数据库表：pms_product。所属模块：mall-mgr（平台管理服务）。
 * 继承 JpaSpecificationExecutor 以支持分类/关键词组合条件下的数据库分页查询。
 */
public interface ProductRepository extends JpaRepository<Product, Long>, JpaSpecificationExecutor<Product> {
    /** 根据商户编号查询该店铺的全部商品 */
    List<Product> findByShopNum(String shopNum);
    /** 根据商品名称模糊查询（名称包含关键字） */
    List<Product> findByNameContaining(String name);
    /** 根据商品上架状态查询（1=上架, 0=下架） */
    List<Product> findByState(Integer state);
}
