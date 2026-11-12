package com.mall.mgr.common;

/** 业务异常（mall-mgr 平台管理服务公共工具）：统一由 GlobalExceptionHandler 转成 {"id":"-1","message":...} */
public class BizException extends RuntimeException {
    public BizException(String message) {
        super(message);
    }
}
