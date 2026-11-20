package com.company.ems.realtime;

import com.company.ems.system.entity.SysConfig;
import com.company.ems.system.mapper.SysConfigMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Holds one socket per browser tab and fans events out to every active session of
 * a user. Only heartbeat messages are accepted from clients; all business events
 * are produced server-side after the corresponding transaction commits.
 */
@Component
public class RealtimeWebSocketHandler extends TextWebSocketHandler {

    private static final Logger log = LoggerFactory.getLogger(RealtimeWebSocketHandler.class);

    private final ObjectMapper objectMapper;
    private final SysConfigMapper configMapper;
    private final Map<Long, Set<WebSocketSession>> sessions = new ConcurrentHashMap<>();

    public RealtimeWebSocketHandler(ObjectMapper objectMapper, SysConfigMapper configMapper) {
        this.objectMapper = objectMapper;
        this.configMapper = configMapper;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        Long userId = userId(session);
        if (userId == null) {
            session.close(CloseStatus.POLICY_VIOLATION);
            return;
        }
        int maxConnections = intConfig("realtime.maxConnectionsPerUser", 5);
        Set<WebSocketSession> userSessions = sessions.computeIfAbsent(userId,
                key -> ConcurrentHashMap.newKeySet());
        if (maxConnections > 0 && userSessions.size() >= maxConnections) {
            send(session, RealtimeEvent.of("TOO_MANY_CONNECTIONS",
                    Map.of("maxConnections", maxConnections)));
            session.close(CloseStatus.POLICY_VIOLATION.withReason("too many connections"));
            return;
        }
        userSessions.add(session);
        send(session, RealtimeEvent.of("CONNECTED", Map.of(
                "userId", userId,
                "heartbeatSeconds", intConfig("realtime.heartbeatSeconds", 25))));
        log.debug("realtime connected userId={} sessions={}", userId, userSessions.size());
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        String payload = message.getPayload() == null ? "" : message.getPayload().trim();
        if ("PING".equalsIgnoreCase(payload) || payload.contains("\"PING\"")) {
            send(session, RealtimeEvent.of("PONG", Map.of()));
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        remove(session);
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) {
        log.debug("realtime transport error: {}", exception.getMessage());
        remove(session);
        try {
            if (session.isOpen()) {
                session.close(CloseStatus.SERVER_ERROR);
            }
        } catch (IOException ignored) {
            // socket is already unusable
        }
    }

    public void send(Long userId, RealtimeEvent event) {
        if (userId == null || event == null) {
            return;
        }
        Set<WebSocketSession> userSessions = sessions.get(userId);
        if (userSessions == null || userSessions.isEmpty()) {
            return;
        }
        for (WebSocketSession session : userSessions) {
            if (!session.isOpen()) {
                userSessions.remove(session);
                continue;
            }
            if (tokenExpired(session)) {
                userSessions.remove(session);
                try {
                    session.close(CloseStatus.POLICY_VIOLATION.withReason("token expired"));
                } catch (IOException ignored) {
                    // already closed
                }
                continue;
            }
            try {
                send(session, event);
            } catch (IOException ex) {
                log.debug("realtime send failed userId={}: {}", userId, ex.getMessage());
                userSessions.remove(session);
                try {
                    session.close(CloseStatus.SERVER_ERROR);
                } catch (IOException ignored) {
                    // already closed
                }
            }
        }
        if (userSessions.isEmpty()) {
            sessions.remove(userId, userSessions);
        }
    }

    public int connectionCount(Long userId) {
        Set<WebSocketSession> userSessions = sessions.get(userId);
        return userSessions == null ? 0 : userSessions.size();
    }

    private void send(WebSocketSession session, RealtimeEvent event) throws IOException {
        if (!session.isOpen()) {
            return;
        }
        String json = objectMapper.writeValueAsString(event);
        synchronized (session) {
            if (session.isOpen()) {
                session.sendMessage(new TextMessage(json));
            }
        }
    }

    private void remove(WebSocketSession session) {
        Long userId = userId(session);
        if (userId == null) {
            return;
        }
        Set<WebSocketSession> userSessions = sessions.get(userId);
        if (userSessions == null) {
            return;
        }
        userSessions.remove(session);
        if (userSessions.isEmpty()) {
            sessions.remove(userId, userSessions);
        }
        log.debug("realtime disconnected userId={} sessions={}", userId, userSessions.size());
    }

    private Long userId(WebSocketSession session) {
        Object value = session.getAttributes().get(RealtimeHandshakeInterceptor.ATTR_USER_ID);
        return value instanceof Long id ? id : null;
    }

    private boolean tokenExpired(WebSocketSession session) {
        Object value = session.getAttributes().get(RealtimeHandshakeInterceptor.ATTR_TOKEN_EXPIRES_AT);
        return value instanceof Long expiresAt && System.currentTimeMillis() >= expiresAt;
    }

    private int intConfig(String key, int defaultValue) {
        SysConfig config = configMapper.selectByKey(key);
        if (config == null || config.getConfigValue() == null) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(config.getConfigValue());
        } catch (NumberFormatException ex) {
            return defaultValue;
        }
    }
}
