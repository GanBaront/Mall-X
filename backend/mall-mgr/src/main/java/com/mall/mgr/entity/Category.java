package com.mall.mgr.entity;

import jakarta.persistence.*;
import lombok.Data;

/**
 * 商品类目实体（两级类目树：一级类目 + 二级类目）。
 * 用于商品分类管理。对应数据库表：pms_category。所属模块：mall-mgr（平台管理服务）。
 */
@Data
@Entity
@Table(name = "pms_category")
public class Category {
    /** 类目ID（主键，自增） */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 类目名称 */
    private String name;

    /** 0-一级类目 */
    private Long parentId;

    /** 排序值，越小越靠前 */
    private Integer sort;

    /** 类目状态（如 0=禁用, 1=启用） */
    private Integer state;
}
