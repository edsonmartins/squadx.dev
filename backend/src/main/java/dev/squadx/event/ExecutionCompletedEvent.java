package dev.squadx.event;

import dev.squadx.model.enums.ExecutionStatus;

import java.time.Instant;

public record ExecutionCompletedEvent(
        Long executionId,
        Long taskId,
        Long projectId,
        Long organizationId,
        ExecutionStatus status,
        Instant occurredAt
) implements DomainEvent {

    public ExecutionCompletedEvent(Long executionId, Long taskId, Long projectId,
                                    Long organizationId, ExecutionStatus status) {
        this(executionId, taskId, projectId, organizationId, status, Instant.now());
    }
}
