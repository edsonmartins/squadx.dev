package dev.squadx.event;

import dev.squadx.dto.task.TaskResponse;

import java.time.Instant;

public record TaskCreatedEvent(
        Long taskId,
        Long projectId,
        Long organizationId,
        TaskResponse task,
        Instant occurredAt
) implements DomainEvent {

    public TaskCreatedEvent(Long taskId, Long projectId, Long organizationId, TaskResponse task) {
        this(taskId, projectId, organizationId, task, Instant.now());
    }
}
