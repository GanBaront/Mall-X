package com.mall.mgr;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Mall-X 业务微服务
 * 承载认证、商户管理、商品、订单、支付、退款、统计、系统管理全部业务接口。
 */
@SpringBootApplication
@EnableScheduling
public class MgrApplication {
    public static void main(String[] args) {
        SpringApplication.run(MgrApplication.class, args);
    }
}
