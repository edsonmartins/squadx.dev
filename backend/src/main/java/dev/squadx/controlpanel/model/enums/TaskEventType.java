package dev.squadx.controlpanel.model.enums;

/**
 * Tipos de evento que alimentam a projeção do estado da tarefa (RFC-0003). {@code IMPLEMENTED} é
 * apenas afirmação ("terminei de codar") e não muda o estado do board sozinho.
 */
public enum TaskEventType {
    STARTED,
    IMPLEMENTED,
    PR_OPENED,
    BLOCKED,
    UNBLOCKED,
    PASS5_APPROVED,
    PASS5_CHANGES
}

