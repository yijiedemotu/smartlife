package com.smartlife.common;

import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

/**
 * 拦截器等非 Controller 场景下直接写出统一 JSON
 */
public class ResponseUtil {

    public static void write(HttpServletResponse response, Integer code, String msg) throws IOException {
        response.setStatus(200);
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write(JsonUtils.toJson(Result.build(code, msg, null)));
    }
}
