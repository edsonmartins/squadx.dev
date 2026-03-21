package dev.squadx.websocket;

import dev.squadx.service.EventBufferService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionConnectEvent;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tracks active WebSocket sessions per user.
 * Auto-drains buffered events when a user reconnects.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class WebSocketSessionTracker {

    private final ConcurrentHashMap<Long, Set<String>> userSessions = new ConcurrentHashMap<>();
    private final EventBufferService eventBufferService;
    private final SimpMessagingTemplate messagingTemplate;

    @EventListener
    public void onConnect(SessionConnectEvent event) {
        // Extract user ID from session attributes if available
        Long userId = extractUserId(event);
        if (userId == null) return;

        String sessionId = event.getMessage().getHeaders().get("simpSessionId", String.class);
        if (sessionId == null) return;

        userSessions.computeIfAbsent(userId, k -> ConcurrentHashMap.newKeySet()).add(sessionId);
        log.debug("WebSocket connected: user={}, session={}", userId, sessionId);

        // Auto-drain buffered events on reconnect
        if (eventBufferService.hasBufferedEvents(userId)) {
            var events = eventBufferService.drainEvents(userId);
            for (var eventPayload : events) {
                messagingTemplate.convertAndSendToUser(
                        userId.toString(), "/queue/buffered", eventPayload);
            }
            log.info("Drained {} buffered events for reconnected user={}", events.size(), userId);
        }
    }

    @EventListener
    public void onDisconnect(SessionDisconnectEvent event) {
        String sessionId = event.getSessionId();
        userSessions.forEach((userId, sessions) -> {
            if (sessions.remove(sessionId)) {
                if (sessions.isEmpty()) {
                    userSessions.remove(userId);
                }
                log.debug("WebSocket disconnected: user={}, session={}", userId, sessionId);
            }
        });
    }

    public boolean isConnected(Long userId) {
        Set<String> sessions = userSessions.get(userId);
        return sessions != null && !sessions.isEmpty();
    }

    public int connectedUserCount() {
        return userSessions.size();
    }

    private Long extractUserId(SessionConnectEvent event) {
        try {
            var headers = event.getMessage().getHeaders();
            var user = headers.get("simpUser");
            if (user instanceof org.springframework.security.core.Authentication auth) {
                var principal = auth.getPrincipal();
                if (principal instanceof dev.squadx.model.User u) {
                    return u.getId();
                }
            }
        } catch (Exception e) {
            log.trace("Could not extract user ID from WebSocket connect event", e);
        }
        return null;
    }
}
