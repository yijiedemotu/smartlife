package com.smartlife.config;

import com.smartlife.security.AuthInterceptor;
import com.smartlife.security.TokenRefreshInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Web MVC 配置：注册二级拦截器链
 * 顺序：AuthInterceptor(登录校验) -> TokenRefreshInterceptor(Token 滑动续期)
 * WebSocket 握手路径 /ws/** 走独立握手拦截器，不经过 MVC 链
 */
@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    /**
     * 免登录白名单：认证类接口（含商家入驻注册）+ 文档 + WebSocket 握手
     * 其余接口一律要求携带有效 JWT，由 AuthInterceptor 按 /merchant、/admin 前缀做端侧权限控制
     */
    private static final String[] EXCLUDE = {
            "/auth/login", "/auth/register", "/auth/register/merchant",
            "/ws", "/ws/**", "/doc.html", "/webjars/**", "/v2/api-docs",
            "/swagger-resources/**", "/error", "/favicon.ico"
    };

    private final AuthInterceptor authInterceptor;
    private final TokenRefreshInterceptor refreshInterceptor;

    public WebMvcConfig(AuthInterceptor authInterceptor, TokenRefreshInterceptor refreshInterceptor) {
        this.authInterceptor = authInterceptor;
        this.refreshInterceptor = refreshInterceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(authInterceptor)
                .addPathPatterns("/**")
                .excludePathPatterns(EXCLUDE);
        registry.addInterceptor(refreshInterceptor)
                .addPathPatterns("/**")
                .excludePathPatterns(EXCLUDE);
    }
}
