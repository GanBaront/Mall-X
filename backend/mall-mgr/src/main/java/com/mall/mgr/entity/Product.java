package com.mall.mgr.entity;

import jakarta.persistence.*;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 商品实体 —— 商户在平台店铺上架销售的商品。
 * 对应数据库表：pms_product。所属模块：mall-mgr（平台管理服务）。
 */
@Data
@Entity
@Table(name = "pms_product")
public class Product {
    /** 商品ID（主键，自增） */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 商品名称 */
    private String name;

    /** 商品价格 */
    private BigDecimal price;

    /** 库存数量 */
    private Integer stock;

    /** 销量 */
    private Integer sales;

    /** 类目名称 */
    private String category;

    /* 1-上架 0-下架 */
    private Integer state;

    /** 商品描述 */
    @Column(name = "descr")
    private String descr;

    /** 商品图片 */
    private String image;

    /** 商户/店铺编号 */
    private String shopNum;

    /** 创建时间 */
    private LocalDateTime createdAt;
}
