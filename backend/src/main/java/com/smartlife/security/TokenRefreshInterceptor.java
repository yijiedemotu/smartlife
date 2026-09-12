package com.smartlife.security;

import com.smartlife.common.LoginUser;
import com.smartlife.common.UserContext;
import com.smartlife.util.JwtUtil;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * 二级拦截器：Token 有效期滑动续期
 * 剩余有效期低于阈值时签发新 Token 并通过响应头 X-Auth-Refresh 返回，
 * 前端 axios 拦截器自动无缝替换，用户无感知
 */
@Component
public class TokenRefreshInterceptor implements HandlerInterceptor {

    public static final String REFRESH_HEADER = "X-Auth-Refresh";

    private final JwtUtil jwtUtil;

    public TokenRefreshInterceptor(JwtUtil jwtUtil) {
        this.jwtUtil = jwtUtil;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        LoginUser user = UserContext.get();
        if (user == null) {
            return true;
        }
        String token = AuthInterceptor.resolveToken(request);
        if (token != null && jwtUtil.needRefresh(token)) {
            response.setHeader(REFRESH_HEADER, jwtUtil.createToken(user));
        }
        return true;
    }
}
