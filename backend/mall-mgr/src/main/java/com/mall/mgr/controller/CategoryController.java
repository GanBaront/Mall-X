package com.mall.mgr.controller;

import com.mall.mgr.common.R;
import com.mall.mgr.entity.Category;
import com.mall.mgr.repository.CategoryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 商品类目公共接口（mall-mgr 平台管理服务）。
 * <p>所属模块：mall-mgr；接口前缀：/api/category（游客可访问，网关白名单放行）。
 * 供商户端「新增/编辑商品」选择分类、用户端「首页/分类 Tab」动态加载分类导航使用，
 * 数据与管理员端「商品类目」管理页（/admin/category/*）同源，保证三端类目一致。
 * <p>接口清单：
 * - POST /api/category/list   启用中的类目树（一级 + 二级，按 sort 升序）
 */
@RestController
@RequiredArgsConstructor
public class CategoryController {

    private final CategoryRepository categoryRepository;

    /**
     * 启用中的类目树（POST /api/category/list）。
     * <p>仅返回 state=1（启用）的类目，按 parentId=0 为根递归构建 children，同级按 sort 升序。
     * 返回：records 为类目树数组（id/name/sort/state/children）。
     */
    @PostMapping("/api/category/list")
    public LinkedHashMap<String, Object> list() {
        // 仅启用中的类目进入可选/展示范围（禁用类目不对外开放）
        List<Category> enabled = categoryRepository.findAll().stream()
                .filter(c -> c.getState() == null || c.getState() == 1)
                .collect(Collectors.toList());
        List<Map<String, Object>> roots = enabled.stream()
                .filter(c -> c.getParentId() == null || c.getParentId() == 0)
                .sorted(Comparator.comparingInt(c -> c.getSort() == null ? 0 : c.getSort()))
                .map(root -> node(root, enabled))
                .collect(Collectors.toList());
        LinkedHashMap<String, Object> resp = R.ok("查询成功");
        resp.put("records", roots);
        return resp;
    }

    /** 递归构建类目节点（含 children，同级按 sort 升序） */
    private Map<String, Object> node(Category c, List<Category> enabled) {
        Map<String, Object> vo = new HashMap<>();
        vo.put("id", c.getId());
        vo.put("name", c.getName());
        vo.put("sort", c.getSort());
        vo.put("state", c.getState());
        List<Map<String, Object>> children = enabled.stream()
                .filter(x -> c.getId().equals(x.getParentId()))
                .sorted(Comparator.comparingInt(x -> x.getSort() == null ? 0 : x.getSort()))
                .map(child -> node(child, enabled))
                .collect(Collectors.toList());
        vo.put("children", children);
        return vo;
    }
}
