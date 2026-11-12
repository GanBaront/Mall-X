package com.mall.alipay;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Mall-X 模拟支付宝服务（mall-alipay 模块）。
 * 对外端口 10012，承担支付渠道职责：渠道下单、主动对账、异步通知转发、模拟收银台与退款。
 * 课程环境无商户密钥时，以「模拟渠道 + 浏览器收银台」实现完整
 * 下单 -> 支付 -> 回调 -> 记账链路；接入真实支付宝时仅需替换 AlipayController 中的渠道实现。
 */
@SpringBootApplication
public class AlipayApplication {
    /**
     * 模拟支付宝服务启动入口。
     *
     * @param args Spring Boot 启动参数
     */
    public static void main(String[] args) {
        SpringApplication.run(AlipayApplication.class, args);
    }
}
