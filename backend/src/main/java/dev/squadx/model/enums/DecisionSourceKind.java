package dev.squadx.model.enums;

/**
 * Tipo de documento de decisão que originou uma {@link dev.squadx.model.Task}
 * (RFC-0007, ADR-0010 Git-first). {@code NONE} = tarefa sem origem no board spec-native.
 */
public enum DecisionSourceKind {
    ADR,
    RFC,
    CHANGE,
    NONE
}
