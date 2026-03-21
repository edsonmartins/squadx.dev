package dev.squadx.event;

import dev.squadx.dto.task.TaskResponse;
import dev.squadx.model.enums.ExecutionStatus;
import dev.squadx.model.enums.TaskStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class DomainEventTest {

    @Test
    @DisplayName("TaskCreatedEvent should set occurredAt automatically")
    void taskCreatedEventAutoTimestamp() {
        Instant before = Instant.now();
        var event = new TaskCreatedEvent(1L, 2L, 3L, TaskResponse.builder().id(1L).build());
        assertThat(event.occurredAt()).isAfterOrEqualTo(before);
        assertThat(event.taskId()).isEqualTo(1L);
        assertThat(event.projectId()).isEqualTo(2L);
        assertThat(event.organizationId()).isEqualTo(3L);
    }

    @Test
    @DisplayName("TaskStatusChangedEvent should carry old and new status")
    void taskStatusChangedEvent() {
        var event = new TaskStatusChangedEvent(1L, 2L, 3L,
                TaskStatus.TODO, TaskStatus.IN_PROGRESS,
                TaskResponse.builder().id(1L).build());
        assertThat(event.oldStatus()).isEqualTo(TaskStatus.TODO);
        assertThat(event.newStatus()).isEqualTo(TaskStatus.IN_PROGRESS);
        assertThat(event.occurredAt()).isNotNull();
    }

    @Test
    @DisplayName("TaskDeletedEvent should set occurredAt automatically")
    void taskDeletedEvent() {
        var event = new TaskDeletedEvent(1L, 2L, 3L);
        assertThat(event.taskId()).isEqualTo(1L);
        assertThat(event.occurredAt()).isNotNull();
    }

    @Test
    @DisplayName("AgentStateChangedEvent should carry states")
    void agentStateChangedEvent() {
        var event = new AgentStateChangedEvent(1L, 2L, "RUNNING", "DEAD");
        assertThat(event.oldState()).isEqualTo("RUNNING");
        assertThat(event.newState()).isEqualTo("DEAD");
    }

    @Test
    @DisplayName("ExecutionCompletedEvent should carry all fields")
    void executionCompletedEvent() {
        var event = new ExecutionCompletedEvent(1L, 2L, 3L, 4L, ExecutionStatus.COMPLETED);
        assertThat(event.executionId()).isEqualTo(1L);
        assertThat(event.status()).isEqualTo(ExecutionStatus.COMPLETED);
    }

    @Test
    @DisplayName("All events implement DomainEvent sealed interface")
    void allEventsImplementDomainEvent() {
        DomainEvent e1 = new TaskCreatedEvent(1L, 1L, 1L, null);
        DomainEvent e2 = new TaskStatusChangedEvent(1L, 1L, 1L, TaskStatus.TODO, TaskStatus.IN_PROGRESS, null);
        DomainEvent e3 = new TaskDeletedEvent(1L, 1L, 1L);
        DomainEvent e4 = new AgentStateChangedEvent(1L, 1L, "A", "B");
        DomainEvent e5 = new ExecutionCompletedEvent(1L, 1L, 1L, 1L, ExecutionStatus.COMPLETED);

        assertThat(e1.occurredAt()).isNotNull();
        assertThat(e2.occurredAt()).isNotNull();
        assertThat(e3.occurredAt()).isNotNull();
        assertThat(e4.occurredAt()).isNotNull();
        assertThat(e5.occurredAt()).isNotNull();
    }
}
