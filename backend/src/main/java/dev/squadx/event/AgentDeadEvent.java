package dev.squadx.event;

import java.time.Instant;

/** Emitted once when an unresponsive agent crosses the DEAD lifecycle boundary. */
public record AgentDeadEvent(Long agentId, Instant occurredAt) implements DomainEvent {

    public AgentDeadEvent(Long agentId) {
        this(agentId, Instant.now());
    }
}
