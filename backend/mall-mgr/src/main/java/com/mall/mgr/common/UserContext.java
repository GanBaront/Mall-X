package com.mall.mgr.common;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Component;

/** 当前登录用户上下文（mall-mgr 平台管理服务公共工具）：从网关透传的请求头中获取登录用户信息 */
@Component
public class UserContext {

    /** 获取登录账号（来自网关透传请求头 X-Mall-Username） */
    public String username(HttpServletRequest request) {
        return request.getHeader("X-Mall-Username");
    }

    /** 获取登录角色（来自网关透传请求头 X-Mall-Role：admin/merchant/user） */
    public String role(HttpServletRequest request) {
        return request.getHeader("X-Mall-Role");
    }

    /** 商户登录时为 shopNum；用户登录时为 userNum */
    public String userNum(HttpServletRequest request) {
        return request.getHeader("X-Mall-User-Num");
    }
}
