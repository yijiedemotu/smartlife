package com.smartlife.ws;

import com.smartlife.common.LoginUser;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

/**
 * 订单/通知 WebSocket 处理器
 */
@Slf4j
@Component
public class OrderWsHandler extends TextWebSocketHandler {

    private final WsSessionManager sessionManager;

    public OrderWsHandler(WsSessionManager sessionManager) {
        this.sessionManager = sessionManager;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        LoginUser user = (LoginUser) session.getAttributes().get("user");
        if (user == null) {
            try {
                session.close(CloseStatus.NOT_ACCEPTABLE);
            } catch (Exception ignored) {
            }
            return;
        }
        sessionManager.register(user, session);
        log.info("WS 连接建立: user={} role={}", user.getNickname(), user.getRole());
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        sessionManager.remove(session);
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) {
        // 心跳/消息可扩展；当前推送为单向服务端->客户端
    }
}
