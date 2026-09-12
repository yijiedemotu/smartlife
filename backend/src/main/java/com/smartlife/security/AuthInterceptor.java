package com.smartlife.security;

import com.smartlife.common.LoginUser;
import com.smartlife.common.ResponseUtil;
import com.smartlife.common.UserContext;
import com.smartlife.util.JwtUtil;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * 一级拦截器：JWT 登录校验 + 三端接口权限控制
 *
 * 权限矩阵（以 context-path 之后的路径为准）：
 *   /auth/**                免登录（登录/注册/商家入驻注册）
 *   /merchant/**            仅商家(role=2) 与 管理员(role=3) 可访问
 *   /admin/**               仅管理员(role=3) 可访问
 *   其余（/shop /order /cart /voucher /mate /sign /chat /user /apply ...）  登录即可访问
 *
 * 校验通过后将用户信息放入 ThreadLocal（线程隔离），请求结束清理。
 */
@Component
public class AuthInterceptor implements HandlerInterceptor {

    private final JwtUtil jwtUtil;

    public AuthInterceptor(JwtUtil jwtUtil) {
        this.jwtUtil = jwtUtil;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
            throws Exception {
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return true;
        }
        String token = resolveToken(request);
        if (!StringUtils.hasText(token)) {
            ResponseUtil.write(response, 401, "未登录，请先登录");
            return false;
        }
        LoginUser user = jwtUtil.parse(token);
        if (user == null) {
            ResponseUtil.write(response, 401, "登录已过期，请重新登录");
            return false;
        }
        // getRequestURI 含 context-path(/api)，需先剥离
        String path = request.getRequestURI().substring(request.getContextPath().length());

        // 商家端：商家本人 + 平台管理员（管理员具备平台级代管能力）
        if (path.startsWith("/merchant/") && !user.canOperateShop()) {
            ResponseUtil.write(response, 403, "该接口仅商家账号可访问");
            return false;
        }
        // 管理端：仅平台管理员
        if (path.startsWith("/admin/") && !user.isAdmin()) {
            ResponseUtil.write(response, 403, "该接口仅平台管理员可访问");
            return false;
        }
        // 用户端专属能力：只有普通用户能进购物车/找搭子/私信等社交点餐链路
        if (isUserOnlyPath(path) && user.isAdmin()) {
            ResponseUtil.write(response, 403, "平台管理员账号不支持用户端操作");
            return false;
        }
        UserContext.set(user);
        return true;
    }

    /** 用户端专属路径：商家/管理员账号（无用户购物车语义）不应调用 */
    private boolean isUserOnlyPath(String path) {
        return path.startsWith("/cart/") || path.startsWith("/mate/")
                || path.startsWith("/chat/") || path.startsWith("/sign/");
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler,
                                Exception ex) {
        // 必须清理 ThreadLocal，避免线程池复用导致数据串号/内存泄漏
        UserContext.clear();
    }

    public static String resolveToken(HttpServletRequest request) {
        String header = request.getHeader("Authorization");
        if (StringUtils.hasText(header) && header.startsWith("Bearer ")) {
            return header.substring(7);
        }
        return null;
    }
}
