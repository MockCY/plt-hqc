package com.qinglian.fitness.presence;

import com.qinglian.fitness.auth.SessionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

import java.util.Map;
import java.util.OptionalLong;

@Component
public class PresenceHandshakeInterceptor implements HandshakeInterceptor {

    public static final String USER_ID_ATTRIBUTE = PresenceHandshakeInterceptor.class.getName() + ".userId";
    private static final Logger log = LoggerFactory.getLogger(PresenceHandshakeInterceptor.class);

    private final SessionService sessionService;

    public PresenceHandshakeInterceptor(SessionService sessionService) {
        this.sessionService = sessionService;
    }

    @Override
    public boolean beforeHandshake(
        ServerHttpRequest request,
        ServerHttpResponse response,
        WebSocketHandler wsHandler,
        Map<String, Object> attributes
    ) {
        String remoteAddress = String.valueOf(request.getRemoteAddress());
        log.info("Presence WebSocket handshake received: remote={}, uri={}", remoteAddress, request.getURI());

        String authorization = request.getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        if (authorization == null || !authorization.startsWith("Bearer ")) {
            log.warn("Presence WebSocket handshake rejected: missing bearer token, remote={}", remoteAddress);
            response.setStatusCode(HttpStatus.UNAUTHORIZED);
            return false;
        }
        OptionalLong userId = sessionService.verify(authorization.substring(7).trim());
        if (userId.isEmpty()) {
            log.warn("Presence WebSocket handshake rejected: invalid bearer token, remote={}", remoteAddress);
            response.setStatusCode(HttpStatus.UNAUTHORIZED);
            return false;
        }
        attributes.put(USER_ID_ATTRIBUTE, userId.getAsLong());
        log.info("Presence WebSocket handshake accepted: userId={}, remote={}", userId.getAsLong(), remoteAddress);
        return true;
    }

    @Override
    public void afterHandshake(
        ServerHttpRequest request,
        ServerHttpResponse response,
        WebSocketHandler wsHandler,
        Exception exception
    ) {
        if (exception != null) {
            log.warn("Presence WebSocket handshake failed after validation: remote={}, error={}",
                request.getRemoteAddress(), exception.getMessage());
        }
    }
}
