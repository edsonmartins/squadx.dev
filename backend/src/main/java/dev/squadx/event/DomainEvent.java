package dev.squadx.event;

import java.time.Instant;

/**
 * Marker interface for all domain events in SquadX.
 */
public sealed interface DomainEvent permits
        TaskCreatedEvent,
        TaskStatusChangedEvent,
        TaskDeletedEvent,
        AgentStateChangedEvent,
        ExecutionCompletedEvent {

    Instant occurredAt();
}
