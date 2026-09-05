package com.qinglian.fitness.presence;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;
import java.util.UUID;

@Component
public class PresenceWebSocketHandler extends TextWebSocketHandler {

    private static final Logger log = LoggerFactory.getLogger(PresenceWebSocketHandler.class);
    private static final String VISIT_KEY = PresenceWebSocketHandler.class.getName() + ".visitKey";
    private final PresenceService presenceService;

    public PresenceWebSocketHandler(PresenceService presenceService) {
        this.presenceService = presenceService;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        long userId = userId(session);
        // Container session IDs can be reused after a restart; persisted visits need a unique key.
        session.getAttributes().put(VISIT_KEY, UUID.randomUUID().toString());
        presenceService.connected(userId, visitKey(session));
        log.info("Presence WebSocket connected: userId={}, sessionId={}, remote={}",
            userId, session.getId(), session.getRemoteAddress());
        session.sendMessage(new TextMessage("connected"));
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        if ("ping".equalsIgnoreCase(message.getPayload().trim())) {
            presenceService.connected(userId(session), visitKey(session));
            session.sendMessage(new TextMessage("pong"));
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        long userId = userId(session);
        presenceService.disconnected(userId, visitKey(session));
        log.info("Presence WebSocket closed: userId={}, sessionId={}, code={}, reason={}",
            userId, session.getId(), status.getCode(), status.getReason());
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) throws Exception {
        long userId = userId(session);
        presenceService.disconnected(userId, visitKey(session));
        log.warn("Presence WebSocket transport error: userId={}, sessionId={}, error={}",
            userId, session.getId(), exception.getMessage());
        if (session.isOpen()) {
            session.close(CloseStatus.SERVER_ERROR);
        }
    }

    private String visitKey(WebSocketSession session) {
        return (String) session.getAttributes().get(VISIT_KEY);
    }

    private long userId(WebSocketSession session) {
        Object value = session.getAttributes().get(PresenceHandshakeInterceptor.USER_ID_ATTRIBUTE);
        if (value instanceof Long userId) {
            return userId;
        }
        throw new IllegalStateException("Authenticated WebSocket user is missing from session");
    }
}
