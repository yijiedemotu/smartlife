package com.smartlife.common;

import lombok.Data;

/**
 * 统一响应体：code=1 成功，其他为失败/未授权
 */
@Data
public class Result<T> {

    private Integer code;
    private String msg;
    private T data;

    public static <T> Result<T> ok() {
        return build(1, "ok", null);
    }

    public static <T> Result<T> ok(T data) {
        return build(1, "ok", data);
    }

    public static <T> Result<T> fail(String msg) {
        return build(0, msg, null);
    }

    public static <T> Result<T> build(Integer code, String msg, T data) {
        Result<T> r = new Result<>();
        r.setCode(code);
        r.setMsg(msg);
        r.setData(data);
        return r;
    }
}
