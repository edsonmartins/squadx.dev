package dev.squadx.event;

import java.time.Instant;

/** A new run was admitted and persisted. IDs are context only and must never become metric tags. */
public record ExecutionStartedEvent(
        Long executionId,
        Instant occurredAt
) implements DomainEvent {

    public ExecutionStartedEvent(Long executionId) {
        this(executionId, Instant.now());
    }
}
