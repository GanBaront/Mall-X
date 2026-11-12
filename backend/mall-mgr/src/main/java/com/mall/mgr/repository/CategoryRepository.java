package com.mall.mgr.repository;

import com.mall.mgr.entity.Category;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

/**
 * 商品类目（Category）的数据访问接口。
 * 对应数据库表：pms_category。所属模块：mall-mgr（平台管理服务）。
 */
public interface CategoryRepository extends JpaRepository<Category, Long> {
    /** 根据父级类目ID查询其全部子类目，按ID升序（用于加载某个一级类目下的二级类目） */
    List<Category> findByParentIdOrderByIdAsc(Long parentId);
    /** 根据父级类目ID查询其全部子类目 */
    List<Category> findByParentId(Long parentId);
    /** 根据类目名称查询（同名可能有多条，用于商品分类归属校验/子树展开） */
    List<Category> findByName(String name);
}
