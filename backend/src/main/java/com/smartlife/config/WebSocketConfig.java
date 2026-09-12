package com.smartlife.config;

import com.smartlife.ws.OrderWsHandler;
import com.smartlife.ws.WsHandshakeInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {

    private final OrderWsHandler orderWsHandler;
    private final WsHandshakeInterceptor handshakeInterceptor;

    public WebSocketConfig(OrderWsHandler orderWsHandler, WsHandshakeInterceptor handshakeInterceptor) {
        this.orderWsHandler = orderWsHandler;
        this.handshakeInterceptor = handshakeInterceptor;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(orderWsHandler, "/ws")
                .addInterceptors(handshakeInterceptor)
                .setAllowedOrigins("*");
    }
}
