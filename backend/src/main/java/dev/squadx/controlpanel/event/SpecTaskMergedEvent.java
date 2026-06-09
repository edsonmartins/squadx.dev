package dev.squadx.controlpanel.event;

import java.time.Instant;

/**
 * Publicado quando o PR de uma tarefa é mergeado (gatilho do Pass 5). Carrega o número do PR e o
 * head sha para a validação buscar o diff (RFC-0004).
 */
public record SpecTaskMergedEvent(
        Long specTaskId,
        String prRef,
        String headSha,
        String prNumber,
        Instant occurredAt
) {
    public SpecTaskMergedEvent(Long specTaskId, String prRef, String headSha, String prNumber) {
        this(specTaskId, prRef, headSha, prNumber, Instant.now());
    }
}
