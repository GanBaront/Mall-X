package com.mall.mgr.common;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/** 内存分页工具（mall-mgr 平台管理服务公共工具）：数据规模为课程演示级别，过滤统一在后端完成后分页 */
public class PageUtil {

    /**
     * 对已完成过滤的列表做内存分页，并映射为视图对象（VO）。
     * @param all      过滤后的全量列表
     * @param params   含 pageNum（默认 1）、pageSize（默认 10）的查询参数
     * @param voMapper 单条记录到 VO 的转换函数
     * @return 当前页的 VO 列表
     */
    public static <T, V> List<V> paginate(List<T> all, Map<String, Object> params, Function<T, V> voMapper) {
        int pageNum = intVal(params.get("pageNum"), 1);
        int pageSize = intVal(params.get("pageSize"), 10);
        if (pageSize < 1) pageSize = 10; // 负/零 pageSize 会导致 subList 区间非法，回退默认值
        int from = Math.max(0, (pageNum - 1) * pageSize);
        // 页码越界时钳制到列表末尾，防止 subList(from > size) 抛 IndexOutOfBoundsException
        if (from > all.size()) from = all.size();
        int to = Math.min(all.size(), from + pageSize);
        return all.subList(from, to).stream().map(voMapper).collect(Collectors.toList());
    }

    /** 返回总条数（此处直接返回已过滤列表长度） */
    public static long total(int size) {
        return size;
    }

    /** 安全解析整型参数：null 或非法字符串时返回默认值 def */
    public static int intVal(Object o, int def) {
        try {
            return o == null ? def : Integer.parseInt(String.valueOf(o));
        } catch (NumberFormatException e) {
            return def;
        }
    }
}
