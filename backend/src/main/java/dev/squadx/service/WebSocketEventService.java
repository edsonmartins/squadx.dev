package dev.squadx.service;

import dev.squadx.event.ExecutionCompletedEvent;
import dev.squadx.event.TaskCreatedEvent;
import dev.squadx.event.TaskDeletedEvent;
import dev.squadx.event.TaskStatusChangedEvent;
import dev.squadx.model.enums.ExecutionStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class WebSocketEventService {

    private final SimpMessagingTemplate messagingTemplate;

    // ── Domain Event Listeners ──────────────────────────────

    @TransactionalEventListener
    public void onTaskCreated(TaskCreatedEvent event) {
        sendTaskCreated(event.projectId(), event.task());
    }

    @TransactionalEventListener
    public void onTaskStatusChanged(TaskStatusChangedEvent event) {
        sendTaskUpdated(event.projectId(), event.taskId(), event.task());
    }

    @TransactionalEventListener
    public void onTaskDeleted(TaskDeletedEvent event) {
        sendTaskDeleted(event.projectId(), event.taskId());
    }

    @TransactionalEventListener
    public void onExecutionCompleted(ExecutionCompletedEvent event) {
        if (event.status() == ExecutionStatus.COMPLETED || event.status() == ExecutionStatus.FAILED) {
            sendExecutionCompleted(event.projectId(), event.taskId(), event.executionId(), event.status().name());
        }
    }

    // ── WebSocket Broadcast Methods ─────────────────────────

    public void sendTaskCreated(Long projectId, Object task) {
        log.debug("Broadcasting task_created event for project {}", projectId);
        messagingTemplate.convertAndSend(
                "/topic/projects/" + projectId + "/tasks",
                Map.of(
                        "type", "task_created",
                        "task", task
                )
        );
    }

    public void sendTaskUpdated(Long projectId, Long taskId, Object task) {
        log.debug("Broadcasting task_updated event for task {}", taskId);
        messagingTemplate.convertAndSend(
                "/topic/projects/" + projectId + "/tasks",
                Map.of(
                        "type", "task_updated",
                        "taskId", taskId,
                        "data", task
                )
        );

        messagingTemplate.convertAndSend(
                "/topic/tasks/" + taskId,
                Map.of(
                        "type", "task_updated",
                        "taskId", taskId,
                        "data", task
                )
        );
    }

    public void sendTaskDeleted(Long projectId, Long taskId) {
        log.debug("Broadcasting task_deleted event for task {}", taskId);
        messagingTemplate.convertAndSend(
                "/topic/projects/" + projectId + "/tasks",
                Map.of(
                        "type", "task_deleted",
                        "taskId", taskId
                )
        );
    }

    public void sendTaskAssignedToUser(String username, Long taskId, Map<String, Object> taskPayload) {
        log.debug("Sending task_assigned event for task {} to user {}", taskId, username);
        messagingTemplate.convertAndSendToUser(
                username,
                "/queue/tasks",
                Map.of(
                        "type", "task_assigned",
                        "task_id", taskId,
                        "task", taskPayload
                )
        );
    }

    public void sendExecutionStarted(Long projectId, Long taskId, Long executionId) {
        log.debug("Broadcasting execution_started event for execution {}", executionId);
        messagingTemplate.convertAndSend(
                "/topic/projects/" + projectId + "/tasks",
                Map.of(
                        "type", "execution_started",
                        "taskId", taskId,
                        "executionId", executionId
                )
        );
    }

    public void sendExecutionCompleted(Long projectId, Long taskId, Long executionId, String status) {
        log.debug("Broadcasting execution_completed event for execution {}", executionId);
        messagingTemplate.convertAndSend(
                "/topic/projects/" + projectId + "/tasks",
                Map.of(
                        "type", "execution_completed",
                        "taskId", taskId,
                        "executionId", executionId,
                        "status", status
                )
        );
    }

    public void sendExecutionLog(Long executionId, String level, String message) {
        log.trace("Broadcasting execution log for execution {}", executionId);
        messagingTemplate.convertAndSend(
                "/topic/executions/" + executionId + "/logs",
                Map.of(
                        "type", "execution_log",
                        "executionId", executionId,
                        "level", level,
                        "message", message,
                        "timestamp", System.currentTimeMillis()
                )
        );
    }

    public void sendLiveSessionStarted(String sessionCode, Long sessionId) {
        log.debug("Broadcasting live session started for session {}", sessionCode);
        messagingTemplate.convertAndSend(
                "/topic/live/" + sessionCode,
                Map.of(
                        "type", "session_started",
                        "sessionId", sessionId,
                        "code", sessionCode
                )
        );
    }

    public void sendLiveSessionEnded(String sessionCode, Long sessionId) {
        log.debug("Broadcasting live session ended for session {}", sessionCode);
        messagingTemplate.convertAndSend(
                "/topic/live/" + sessionCode,
                Map.of(
                        "type", "session_ended",
                        "sessionId", sessionId,
                        "code", sessionCode
                )
        );
    }

    public void sendParticipantJoined(String sessionCode, Long userId, String userName, int currentViewers) {
        log.debug("Broadcasting participant joined for session {}", sessionCode);
        messagingTemplate.convertAndSend(
                "/topic/live/" + sessionCode + "/participants",
                Map.of(
                        "action", "joined",
                        "userId", userId,
                        "userName", userName,
                        "currentViewers", currentViewers
                )
        );
    }

    public void sendParticipantLeft(String sessionCode, Long userId, String userName, int currentViewers) {
        log.debug("Broadcasting participant left for session {}", sessionCode);
        messagingTemplate.convertAndSend(
                "/topic/live/" + sessionCode + "/participants",
                Map.of(
                        "action", "left",
                        "userId", userId,
                        "userName", userName,
                        "currentViewers", currentViewers
                )
        );
    }

    public void sendOrganizationEvent(Long organizationId, String eventType, Object data) {
        log.debug("Broadcasting {} event for organization {}", eventType, organizationId);
        messagingTemplate.convertAndSend(
                "/topic/organizations/" + organizationId,
                Map.of(
                        "type", eventType,
                        "data", data
                )
        );
    }

    public void sendControlGranted(String sessionCode, Long userId) {
        log.debug("Broadcasting control_granted for session {} to user {}", sessionCode, userId);
        messagingTemplate.convertAndSend(
                "/topic/live/" + sessionCode,
                Map.of(
                        "type", "control_granted",
                        "userId", userId
                )
        );
    }

    public void sendControlRevoked(String sessionCode, Long userId) {
        log.debug("Broadcasting control_revoked for session {} from user {}", sessionCode, userId);
        messagingTemplate.convertAndSend(
                "/topic/live/" + sessionCode,
                Map.of(
                        "type", "control_revoked",
                        "userId", userId
                )
        );
    }
}
