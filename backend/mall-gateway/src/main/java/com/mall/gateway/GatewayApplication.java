package com.mall.gateway;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Mall-X 网关服务（mall-gateway 模块）。
 * 平台统一入口，负责请求路由、JWT 鉴权、IP 限流。
 * 部署要求：以 HTTPS 对外提供服务，端口 10010（https://localhost:10010）。
 * 配套全局过滤器见 AuthRateLimitFilter。
 */
@SpringBootApplication
public class GatewayApplication {
    /**
     * 网关启动入口。
     *
     * @param args Spring Boot 启动参数
     */
    public static void main(String[] args) {
        SpringApplication.run(GatewayApplication.class, args);
    }
}
