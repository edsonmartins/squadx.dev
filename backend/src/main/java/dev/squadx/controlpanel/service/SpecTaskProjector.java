package dev.squadx.controlpanel.service;

import dev.squadx.controlpanel.model.SpecEvent;
import dev.squadx.controlpanel.model.enums.Pass5Result;
import dev.squadx.controlpanel.model.enums.SpecTaskStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Projeta o estado de uma tarefa a partir da sequência (ordenada) de eventos (ADR-0002, RFC-0003).
 * Função pura e determinística: reprocessar os mesmos eventos reconstrói o mesmo estado.
 * Transições inválidas (segundo {@link SpecTaskStateMachine}) são ignoradas.
 */
@Component
@RequiredArgsConstructor
public class SpecTaskProjector {

    private final SpecTaskStateMachine stateMachine;

    public record ProjectedState(
            SpecTaskStatus status,
            String blockerReason,
            String reviseReason,
            Pass5Result pass5
    ) {}

    public ProjectedState project(List<SpecEvent> orderedEvents) {
        SpecTaskStatus status = SpecTaskStatus.A_FAZER;
        SpecTaskStatus activeBeforeBlock = SpecTaskStatus.A_FAZER;
        String blockerReason = null;
        String reviseReason = null;
        Pass5Result pass5 = Pass5Result.PENDING;

        for (SpecEvent e : orderedEvents) {
            switch (e.getType()) {
                case STARTED -> {
                    if (stateMachine.canTransition(status, SpecTaskStatus.EM_CURSO)) {
                        status = SpecTaskStatus.EM_CURSO;
                    }
                }
                case IMPLEMENTED -> {
                    // Afirmação do agente/dev; não muda o board (RFC-0003).
                }
                case PR_OPENED -> {
                    if (stateMachine.canTransition(status, SpecTaskStatus.EM_VALIDACAO)) {
                        status = SpecTaskStatus.EM_VALIDACAO;
                    }
                }
                case BLOCKED -> {
                    if (stateMachine.canTransition(status, SpecTaskStatus.BLOQUEADA)) {
                        activeBeforeBlock = status;
                        status = SpecTaskStatus.BLOQUEADA;
                        blockerReason = e.getPayload();
                    }
                }
                case UNBLOCKED -> {
                    if (status == SpecTaskStatus.BLOQUEADA) {
                        SpecTaskStatus target = (activeBeforeBlock == SpecTaskStatus.BLOQUEADA
                                || activeBeforeBlock == SpecTaskStatus.CONCLUIDA)
                                ? SpecTaskStatus.EM_CURSO : activeBeforeBlock;
                        status = stateMachine.canTransition(SpecTaskStatus.BLOQUEADA, target)
                                ? target : SpecTaskStatus.EM_CURSO;
                        blockerReason = null;
                    }
                }
                case PASS5_APPROVED -> {
                    if (stateMachine.canTransition(status, SpecTaskStatus.CONCLUIDA)) {
                        status = SpecTaskStatus.CONCLUIDA;
                        pass5 = Pass5Result.PASS;
                        reviseReason = null;
                    }
                }
                case PASS5_CHANGES -> {
                    if (stateMachine.canTransition(status, SpecTaskStatus.AJUSTES)) {
                        status = SpecTaskStatus.AJUSTES;
                        pass5 = Pass5Result.FAIL;
                        reviseReason = e.getPayload();
                    }
                }
            }
        }
        return new ProjectedState(status, blockerReason, reviseReason, pass5);
    }
}
