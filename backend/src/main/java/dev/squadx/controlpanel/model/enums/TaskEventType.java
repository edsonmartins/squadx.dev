package dev.squadx.controlpanel.model.enums;

/**
 * Tipos de evento que alimentam a projeção do estado da tarefa (RFC-0003). {@code IMPLEMENTED} e
 * {@code REVIEW_COMMENT} são apenas afirmações/feedback e não mudam o estado do board sozinhos.
 */
public enum TaskEventType {
    STARTED,
    IMPLEMENTED,
    PR_OPENED,
    BLOCKED,
    UNBLOCKED,
    PASS5_APPROVED,
    PASS5_CHANGES,
    REVIEW_COMMENT
}
