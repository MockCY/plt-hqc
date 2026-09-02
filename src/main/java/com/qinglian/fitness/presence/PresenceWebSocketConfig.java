package com.qinglian.fitness.presence;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

@Configuration
@EnableWebSocket
public class PresenceWebSocketConfig implements WebSocketConfigurer {

    private final PresenceWebSocketHandler handler;
    private final PresenceHandshakeInterceptor handshakeInterceptor;

    public PresenceWebSocketConfig(
        PresenceWebSocketHandler handler,
        PresenceHandshakeInterceptor handshakeInterceptor
    ) {
        this.handler = handler;
        this.handshakeInterceptor = handshakeInterceptor;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(handler, "/ws/presence")
            .addInterceptors(handshakeInterceptor)
            .setAllowedOriginPatterns("*");
    }
}
