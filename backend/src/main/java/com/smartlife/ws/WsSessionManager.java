package com.smartlife.ws;

import com.smartlife.common.LoginUser;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.io.IOException;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;

/**
 * WebSocket 会话管理（按角色分桶，保证三端消息互不串扰）
 *
 *   userSessions     : userId -> sessions（用户端，多端登录保留多个会话）
 *   merchantSessions : merchantId -> sessions（商家端，按店铺隔离新单提醒）
 *   adminSessions    : 平台管理员（平台级广播，如秒杀异常告警）
 */
@Slf4j
@Component
public class WsSessionManager {

    private final Map<Long, Set<WebSocketSession>> userSessions = new ConcurrentHashMap<>();

    private final Map<Long, Set<WebSocketSession>> merchantSessions = new ConcurrentHashMap<>();

    private final Set<WebSocketSession> adminSessions = new CopyOnWriteArraySet<>();

    public void register(LoginUser user, WebSocketSession session) {
        if (user.isAdmin()) {
            adminSessions.add(session);
        } else if (user.isMerchant()) {
            merchantSessions.computeIfAbsent(user.getId(), k -> new CopyOnWriteArraySet<>()).add(session);
        } else {
            userSessions.computeIfAbsent(user.getId(), k -> new CopyOnWriteArraySet<>()).add(session);
        }
    }

    public void remove(WebSocketSession session) {
        adminSessions.remove(session);
        userSessions.values().forEach(set -> set.remove(session));
        merchantSessions.values().forEach(set -> set.remove(session));
    }

    public void sendToUser(Long userId, String payload) {
        Set<WebSocketSession> sessions = userSessions.get(userId);
        if (sessions != null) {
            sessions.forEach(s -> send(s, payload));
        }
    }

    /** 新订单只推给订单所属店铺的商家 */
    public void sendToMerchant(Long merchantId, String payload) {
        Set<WebSocketSession> sessions = merchantSessions.get(merchantId);
        if (sessions != null) {
            sessions.forEach(s -> send(s, payload));
        }
    }

    /** 平台广播（管理员） */
    public void broadcastToAdmin(String payload) {
        adminSessions.forEach(s -> send(s, payload));
    }

    public int onlineUserCount() {
        return (int) userSessions.values().stream().mapToLong(Set::size).sum()
                + (int) merchantSessions.values().stream().mapToLong(Set::size).sum()
                + adminSessions.size();
    }

    public int onlineMerchantCount() {
        return (int) merchantSessions.values().stream().mapToLong(Set::size).sum();
    }

    private void send(WebSocketSession session, String payload) {
        if (session.isOpen()) {
            try {
                synchronized (session) {
                    session.sendMessage(new TextMessage(payload));
                }
            } catch (IOException e) {
                log.warn("WS 推送失败: {}", e.getMessage());
            }
        }
    }
}
