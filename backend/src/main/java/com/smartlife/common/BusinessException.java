package com.smartlife.common;

/**
 * 业务异常：由全局异常处理器统一转换为 Result
 */
public class BusinessException extends RuntimeException {

    public BusinessException(String message) {
        super(message);
    }
}
