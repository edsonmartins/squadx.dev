package dev.squadx.event;

import java.time.Instant;

public record AgentStateChangedEvent(
        Long agentId,
        Long organizationId,
        String oldState,
        String newState,
        Instant occurredAt
) implements DomainEvent {

    public AgentStateChangedEvent(Long agentId, Long organizationId, String oldState, String newState) {
        this(agentId, organizationId, oldState, newState, Instant.now());
    }
}
