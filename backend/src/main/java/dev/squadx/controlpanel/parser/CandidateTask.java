package dev.squadx.controlpanel.parser;

/**
 * Tarefa candidata derivada de um documento de decisão (RFC-0007, T-0010-2).
 *
 * @param id         identificador da tarefa na decisão (ex.: "T-0011-6")
 * @param title      título/descrição extraído
 * @param priority   prioridade (P0|P1|P2...) — pode ser vazio
 * @param sourceKind tipo da decisão (ADR | RFC | CHANGE)
 * @param sourceRef  âncora estável: "path#id"
 */
public record CandidateTask(String id, String title, String priority,
                            String sourceKind, String sourceRef) {
}
