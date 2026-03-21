package dev.squadx.event;

import java.time.Instant;

public record TaskDeletedEvent(
        Long taskId,
        Long projectId,
        Long organizationId,
        Instant occurredAt
) implements DomainEvent {

    public TaskDeletedEvent(Long taskId, Long projectId, Long organizationId) {
        this(taskId, projectId, organizationId, Instant.now());
    }
}
