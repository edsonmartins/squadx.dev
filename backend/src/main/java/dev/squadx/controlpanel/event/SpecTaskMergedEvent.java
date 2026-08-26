package dev.squadx.controlpanel.event;

import java.time.Instant;

/**
 * Publicado quando o PR de uma tarefa é mergeado (gatilho do Pass 5). O consumidor real (rodar a
 * validação) chega na capability {@code pass5-validation}; aqui é apenas o gancho.
 */
public record SpecTaskMergedEvent(
        Long specTaskId,
        String prRef,
        String headSha,
        Instant occurredAt
) {
    public SpecTaskMergedEvent(Long specTaskId, String prRef, String headSha) {
        this(specTaskId, prRef, headSha, Instant.now());
    }
}

