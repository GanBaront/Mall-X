package com.mall.mgr.common;

import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.LinkedHashMap;

/**
 * 全局异常处理器（mall-mgr 平台管理服务公共工具）
 * <p>使用 @RestControllerAdvice 统一拦截控制器抛出的异常，转化为统一响应体 R.fail(...)。
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /** 业务异常：直接以异常 message 作为失败提示，id="-1" */
    @ExceptionHandler(BizException.class)
    public LinkedHashMap<String, Object> handleBiz(BizException e) {
        return R.fail(e.getMessage());
    }

    /** 兜底异常：记录错误日志，返回通用“系统繁忙”提示，避免敏感信息外泄 */
    @ExceptionHandler(Exception.class)
    public LinkedHashMap<String, Object> handleOther(Exception e) {
        log.error("接口异常", e);
        return R.fail("系统繁忙，请稍后再试");
    }
}
