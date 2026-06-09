package dev.squadx.controlpanel.event;

import dev.squadx.controlpanel.model.enums.SpecTaskStatus;

import java.time.Instant;

/**
 * Publicado após a reprojeção do estado de uma tarefa do Control Panel. Consumido para notificar
 * a UI (WebSocket). Não faz parte da interface selada {@code DomainEvent} do runtime de execução.
 */
public record SpecTaskProjectedEvent(
        Long specTaskId,
        Long changeId,
        Long projectId,
        SpecTaskStatus status,
        Instant occurredAt
) {
    public SpecTaskProjectedEvent(Long specTaskId, Long changeId, Long projectId, SpecTaskStatus status) {
        this(specTaskId, changeId, projectId, status, Instant.now());
    }
}
