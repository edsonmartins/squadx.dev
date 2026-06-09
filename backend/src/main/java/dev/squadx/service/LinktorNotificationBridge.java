package dev.squadx.service;

import dev.squadx.event.AgentStateChangedEvent;
import dev.squadx.event.ExecutionCompletedEvent;
import dev.squadx.event.TaskStatusChangedEvent;
import dev.squadx.integration.LinktorClient;
import dev.squadx.model.enums.ExecutionStatus;
import dev.squadx.model.enums.TaskStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.LinkedHashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class LinktorNotificationBridge {

    private final LinktorClient linktorClient;

    @TransactionalEventListener
    public void onTaskStatusChanged(TaskStatusChangedEvent event) {
        if (event.organizationId() == null) {
            return;
        }

        if (event.newStatus() == TaskStatus.DONE || event.newStatus() == TaskStatus.BLOCKED || event.newStatus() == TaskStatus.IN_REVIEW) {
            String taskTitle = event.task() != null ? event.task().getTitle() : "Task #" + event.taskId();
            String projectName = event.task() != null ? event.task().getProjectName() : "Unknown Project";
            String title = "Task update: " + taskTitle;
            String body = "Project: %s\nStatus: %s -> %s".formatted(projectName, event.oldStatus(), event.newStatus());
            send(event.organizationId(), title, body, Map.of(
                    "event", "task_status_changed",
                    "taskId", event.taskId(),
                    "projectId", event.projectId(),
                    "newStatus", event.newStatus()
            ));
        }
    }

    @TransactionalEventListener
    public void onExecutionCompleted(ExecutionCompletedEvent event) {
        if (event.organizationId() == null) {
            return;
        }

        if (event.status() == ExecutionStatus.COMPLETED || event.status() == ExecutionStatus.FAILED) {
            String title = "Execution update: #" + event.executionId();
            String body = "Task: %s\nProject: %s\nStatus: %s".formatted(event.taskId(), event.projectId(), event.status());
            send(event.organizationId(), title, body, Map.of(
                    "event", "execution_completed",
                    "executionId", event.executionId(),
                    "taskId", event.taskId(),
                    "projectId", event.projectId(),
                    "status", event.status()
            ));
        }
    }

    @TransactionalEventListener
    public void onAgentStateChanged(AgentStateChangedEvent event) {
        if (event.organizationId() == null || !"DEAD".equals(event.newState())) {
            return;
        }

        String title = "Agent incident: #" + event.agentId();
        String body = "Agent state changed from %s to %s".formatted(event.oldState(), event.newState());
        send(event.organizationId(), title, body, Map.of(
                "event", "agent_state_changed",
                "agentId", event.agentId(),
                "oldState", event.oldState(),
                "newState", event.newState()
        ));
    }

    private void send(Long organizationId, String title, String body, Map<String, Object> metadata) {
        Map<String, Object> safeMetadata = new LinkedHashMap<>(metadata);
        safeMetadata.put("organizationId", organizationId);

        boolean delivered = linktorClient.sendOperationalMessage(organizationId, title, body, safeMetadata);
        if (!delivered) {
            log.debug("Linktor bridge skipped message for org {} with title {}", organizationId, title);
        }
    }
}
