package dev.squadx.repository;

import dev.squadx.model.enums.ExecutionStatus;

/**
 * Projeção (agentId, agentName, status, total) usada pela agregação de taxa de
 * sucesso por agente em {@link ExecutionRepository#aggregateByAgentAndStatus}.
 * Insumo do critério de saída da fase interna (ADR-0011 T-0011-8).
 */
public record ExecutionMetric(Long agentId, String agentName, ExecutionStatus status, long total) {
}
