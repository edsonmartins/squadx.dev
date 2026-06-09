package dev.squadx.service;

import dev.squadx.dto.task.TaskResponse;
import dev.squadx.event.AgentStateChangedEvent;
import dev.squadx.event.ExecutionCompletedEvent;
import dev.squadx.event.TaskStatusChangedEvent;
import dev.squadx.integration.LinktorClient;
import dev.squadx.model.enums.ExecutionStatus;
import dev.squadx.model.enums.TaskStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LinktorNotificationBridgeTest {

    @Mock
    private LinktorClient linktorClient;

    @Test
    @DisplayName("should bridge task completion updates to linktor")
    void shouldBridgeTaskCompletionUpdates() {
        when(linktorClient.sendOperationalMessage(eq(7L), eq("Task update: Ship alpha"), eq("Project: Core\nStatus: IN_PROGRESS -> IN_REVIEW"), org.mockito.ArgumentMatchers.anyMap()))
                .thenReturn(true);

        LinktorNotificationBridge bridge = new LinktorNotificationBridge(linktorClient);
        TaskResponse task = TaskResponse.builder().id(11L).title("Ship alpha").projectName("Core").build();

        bridge.onTaskStatusChanged(new TaskStatusChangedEvent(11L, 3L, 7L, TaskStatus.IN_PROGRESS, TaskStatus.IN_REVIEW, task));

        ArgumentCaptor<Map<String, Object>> metadataCaptor = ArgumentCaptor.forClass(Map.class);
        verify(linktorClient).sendOperationalMessage(eq(7L), eq("Task update: Ship alpha"),
                eq("Project: Core\nStatus: IN_PROGRESS -> IN_REVIEW"), metadataCaptor.capture());
        assertThat(metadataCaptor.getValue()).containsEntry("event", "task_status_changed");
        assertThat(metadataCaptor.getValue()).containsEntry("organizationId", 7L);
    }

    @Test
    @DisplayName("should bridge failed executions to linktor")
    void shouldBridgeFailedExecutions() {
        when(linktorClient.sendOperationalMessage(eq(7L), eq("Execution update: #42"), eq("Task: 11\nProject: 3\nStatus: FAILED"), org.mockito.ArgumentMatchers.anyMap()))
                .thenReturn(true);

        LinktorNotificationBridge bridge = new LinktorNotificationBridge(linktorClient);

        bridge.onExecutionCompleted(new ExecutionCompletedEvent(42L, 11L, 3L, 7L, ExecutionStatus.FAILED));

        verify(linktorClient).sendOperationalMessage(eq(7L), eq("Execution update: #42"),
                eq("Task: 11\nProject: 3\nStatus: FAILED"), org.mockito.ArgumentMatchers.anyMap());
    }

    @Test
    @DisplayName("should only notify dead agent transitions")
    void shouldOnlyNotifyDeadAgentTransitions() {
        when(linktorClient.sendOperationalMessage(eq(7L), eq("Agent incident: #5"), eq("Agent state changed from ACTIVE to DEAD"), org.mockito.ArgumentMatchers.anyMap()))
                .thenReturn(true);

        LinktorNotificationBridge bridge = new LinktorNotificationBridge(linktorClient);

        bridge.onAgentStateChanged(new AgentStateChangedEvent(5L, 7L, "ACTIVE", "DEAD"));
        verify(linktorClient).sendOperationalMessage(eq(7L), eq("Agent incident: #5"),
                eq("Agent state changed from ACTIVE to DEAD"), org.mockito.ArgumentMatchers.anyMap());
    }

    @Test
    @DisplayName("should ignore non terminal task updates")
    void shouldIgnoreNonTerminalTaskUpdates() {
        LinktorNotificationBridge bridge = new LinktorNotificationBridge(linktorClient);
        TaskResponse task = TaskResponse.builder().id(11L).title("Ship alpha").projectName("Core").build();

        bridge.onTaskStatusChanged(new TaskStatusChangedEvent(11L, 3L, 7L, TaskStatus.TODO, TaskStatus.IN_PROGRESS, task));

        verifyNoInteractions(linktorClient);
    }
}
