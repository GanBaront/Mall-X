package com.mall.mgr.common;

import java.util.Map;

/**
 * 统一响应体（mall-mgr 平台管理服务公共工具）
 * <p>封装：{ id, message, ...业务数据 }。id = "0" 表示成功（指导书接口规范），
 * 前端 invoke 以 String(id)==='0' 判断成功；id = "-1" 表示失败。
 * 业务数据统一放在 LinkedHashMap 中返回，保证 key 顺序稳定。
 */
public class R {

    /** 成功响应（仅提示语，无业务数据）：id="0" */
    public static java.util.LinkedHashMap<String, Object> ok(String message) {
        return ok(message, null);
    }

    /**
     * 成功响应（含业务数据）：id="0"
     * @param message 提示语
     * @param data    业务数据（可为 null；非 null 时整体合并进响应体）
     * @return 统一响应体 LinkedHashMap
     */
    public static java.util.LinkedHashMap<String, Object> ok(String message, Map<String, Object> data) {
        java.util.LinkedHashMap<String, Object> body = new java.util.LinkedHashMap<>();
        body.put("id", "0");
        body.put("message", message);
        if (data != null) {
            body.putAll(data);
        }
        return body;
    }

    /** 失败响应：id="-1"，前端以 id 非 "0" 判定失败并展示 message */
    public static java.util.LinkedHashMap<String, Object> fail(String message) {
        java.util.LinkedHashMap<String, Object> body = new java.util.LinkedHashMap<>();
        body.put("id", "-1");
        body.put("message", message);
        return body;
    }

    /** 分页结构：total/pages/current/size/records */
    public static void putPage(java.util.LinkedHashMap<String, Object> body, long total, int pageNum, int pageSize, Object records) {
        body.put("total", total);
        body.put("pages", pageSize == 0 ? 0 : (int) Math.ceil((double) total / pageSize));
        body.put("current", pageNum);
        body.put("size", pageSize);
        body.put("records", records);
    }
}
