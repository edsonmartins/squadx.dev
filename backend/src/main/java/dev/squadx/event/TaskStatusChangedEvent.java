package dev.squadx.event;

import dev.squadx.dto.task.TaskResponse;
import dev.squadx.model.enums.TaskStatus;

import java.time.Instant;

public record TaskStatusChangedEvent(
        Long taskId,
        Long projectId,
        Long organizationId,
        TaskStatus oldStatus,
        TaskStatus newStatus,
        TaskResponse task,
        Instant occurredAt
) implements DomainEvent {

    public TaskStatusChangedEvent(Long taskId, Long projectId, Long organizationId,
                                   TaskStatus oldStatus, TaskStatus newStatus, TaskResponse task) {
        this(taskId, projectId, organizationId, oldStatus, newStatus, task, Instant.now());
    }
}
