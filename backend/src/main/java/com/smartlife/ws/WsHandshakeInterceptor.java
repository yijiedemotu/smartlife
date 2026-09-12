package com.smartlife.ws;

import com.smartlife.common.LoginUser;
import com.smartlife.util.JwtUtil;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.socket.server.HandshakeInterceptor;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.web.socket.WebSocketHandler;

import java.util.Map;

/**
 * WebSocket 握手拦截器：校验 URL 携带的 token（浏览器 WS 无法自定义 Header）
 */
@Component
public class WsHandshakeInterceptor implements HandshakeInterceptor {

    private final JwtUtil jwtUtil;

    public WsHandshakeInterceptor(JwtUtil jwtUtil) {
        this.jwtUtil = jwtUtil;
    }

    @Override
    public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response,
                                   WebSocketHandler wsHandler, Map<String, Object> attributes) {
        String token = request.getURI().getQuery();
        if (StringUtils.hasText(token) && token.startsWith("token=")) {
            String value = token.substring("token=".length());
            // 兼容可能附带的其他参数
            int amp = value.indexOf('&');
            if (amp > 0) {
                value = value.substring(0, amp);
            }
            LoginUser user = jwtUtil.parse(value);
            if (user != null) {
                attributes.put("user", user);
                return true;
            }
        }
        return false;
    }

    @Override
    public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response,
                               WebSocketHandler wsHandler, Exception exception) {
    }
}
