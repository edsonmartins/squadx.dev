package dev.squadx.websocket;

import dev.squadx.model.User;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;

import java.util.Map;

@Controller
@RequiredArgsConstructor
@Slf4j
public class WebSocketController {

    private final SimpMessagingTemplate messagingTemplate;

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
        // The actual update is handled by the REST API
        // This is for real-time broadcasting
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
