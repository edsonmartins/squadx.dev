package dev.squadx.websocket;

import dev.squadx.model.User;
import dev.squadx.service.AgentService;
import dev.squadx.service.ExecutionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;

import java.util.Collection;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

@Controller
@RequiredArgsConstructor
@Slf4j
public class WebSocketController {

    private final SimpMessagingTemplate messagingTemplate;
    private final ExecutionService executionService;
    private final AgentService agentService;

    @MessageMapping("/client/register")
    public void registerClient(
            @Payload Map<String, Object> payload,
            @AuthenticationPrincipal User user
    ) {
        log.info("Client registered over WebSocket for user {} with payload keys {}", user.getEmail(), payload.keySet());
    }

    @MessageMapping("/client/heartbeat")
    public void heartbeat(
            @Payload Map<String, Object> payload,
            @AuthenticationPrincipal User user
    ) {
        Set<Long> activeAgentIds = numericIds(payload.get("active_agent_ids"));
        Set<Long> agentIds = numericIds(payload.get("agent_ids"));
        Object legacyAgentId = payload.get("agent_id");
        if (legacyAgentId instanceof Number number) {
            agentIds.add(number.longValue());
            activeAgentIds.add(number.longValue());
        }
        for (Long agentId : agentIds) {
            agentService.heartbeat(agentId, user, activeAgentIds.contains(agentId));
        }
        log.debug("Client heartbeat received from user {}", user.getEmail());
    }

    private Set<Long> numericIds(Object value) {
        Set<Long> ids = new HashSet<>();
        if (value instanceof Collection<?> values) {
            for (Object item : values) {
                if (item instanceof Number number) {
                    ids.add(number.longValue());
                } else if (item instanceof String text) {
                    try {
                        ids.add(Long.valueOf(text));
                    } catch (NumberFormatException ignored) {
                        log.debug("Ignoring invalid agent id in heartbeat: {}", text);
                    }
                }
            }
        }
        return ids;
    }

    @MessageMapping("/tasks/{projectId}/subscribe")
    public void subscribeToProject(
            @DestinationVariable Long projectId,
            @AuthenticationPrincipal User user
    ) {
        log.info("User {} subscribed to project {} tasks", user.getEmail(), projectId);
    }

    @MessageMapping("/tasks/{taskId}/status")
    public void updateTaskStatus(
            @DestinationVariable Long taskId,
            @Payload Map<String, Object> payload,
            @AuthenticationPrincipal User user
    ) {
        log.info("User {} updating task {} status", user.getEmail(), taskId);
        executionService.handleDaemonTaskUpdate(taskId, payload);
    }

    @MessageMapping("/live/{sessionCode}/join")
    public void joinLiveSession(
            @DestinationVariable String sessionCode,
            @AuthenticationPrincipal User user
    ) {
        log.info("User {} joined live session {}", user.getEmail(), sessionCode);
        messagingTemplate.convertAndSend(
                "/topic/live/" + sessionCode + "/participants",
                Map.of(
                        "action", "joined",
                        "userId", user.getId(),
                        "userName", user.getFullName()
                )
        );
    }

    @MessageMapping("/live/{sessionCode}/leave")
    public void leaveLiveSession(
            @DestinationVariable String sessionCode,
            @AuthenticationPrincipal User user
    ) {
        log.info("User {} left live session {}", user.getEmail(), sessionCode);
        messagingTemplate.convertAndSend(
                "/topic/live/" + sessionCode + "/participants",
                Map.of(
                        "action", "left",
                        "userId", user.getId(),
                        "userName", user.getFullName()
                )
        );
    }

    @MessageMapping("/live/{sessionCode}/chat")
    public void sendChatMessage(
            @DestinationVariable String sessionCode,
            @Payload Map<String, Object> payload,
            @AuthenticationPrincipal User user
    ) {
        Map<String, Object> message = Map.of(
                "userId", user.getId(),
                "userName", user.getFullName(),
                "content", payload.get("content"),
                "timestamp", System.currentTimeMillis()
        );

        messagingTemplate.convertAndSend(
                "/topic/live/" + sessionCode + "/chat",
                message
        );
    }

    @MessageMapping("/execution/{executionId}/logs")
    public void subscribeToExecutionLogs(
            @DestinationVariable Long executionId,
            @AuthenticationPrincipal User user
    ) {
        log.info("User {} subscribed to execution {} logs", user.getEmail(), executionId);
    }
}
