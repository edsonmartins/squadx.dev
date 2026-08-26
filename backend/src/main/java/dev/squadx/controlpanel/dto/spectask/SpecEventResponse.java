package dev.squadx.controlpanel.dto.spectask;

import dev.squadx.controlpanel.model.enums.EventSource;
import dev.squadx.controlpanel.model.enums.TaskEventType;
import lombok.Builder;

import java.time.Instant;

@Builder
public record SpecEventResponse(Long id, TaskEventType type, EventSource source,
                                String sourceRef, String payload, Instant occurredAt,
                                Instant receivedAt) {}
