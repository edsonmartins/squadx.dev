package dev.squadx.controlpanel.service;

import dev.squadx.controlpanel.model.enums.SpecTaskStatus;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

import static dev.squadx.controlpanel.model.enums.SpecTaskStatus.*;

/**
 * Máquina de estados da tarefa do Control Panel (ADR-0004). Componente puro, reutilizável tanto
 * pelo serviço de transições quanto pelo futuro projetor de eventos (execution-tracking).
 *
 * <pre>
 * a_fazer ─▶ em_curso ─▶ em_validacao ─▶ concluida
 *                              │
 *                              └─▶ ajustes ─▶ em_curso (reabre)
 * qualquer estado ativo ◀─▶ bloqueada
 * </pre>
 *
 * {@code CONCLUIDA} e {@code AJUSTES} são alvos exclusivos do Pass 5 ({@link #isPass5Only}).
 */
@Component
public class SpecTaskStateMachine {

    /** Alvos que somente o Pass 5 pode atribuir. */
    public static final Set<SpecTaskStatus> PASS5_ONLY = EnumSet.of(CONCLUIDA, AJUSTES);

    private static final Map<SpecTaskStatus, Set<SpecTaskStatus>> TRANSITIONS =
            new EnumMap<>(SpecTaskStatus.class);

    static {
        TRANSITIONS.put(A_FAZER, EnumSet.of(EM_CURSO, BLOQUEADA));
        TRANSITIONS.put(EM_CURSO, EnumSet.of(EM_VALIDACAO, BLOQUEADA));
        TRANSITIONS.put(EM_VALIDACAO, EnumSet.of(CONCLUIDA, AJUSTES, BLOQUEADA));
        TRANSITIONS.put(AJUSTES, EnumSet.of(EM_CURSO, BLOQUEADA));
        TRANSITIONS.put(BLOQUEADA, EnumSet.of(A_FAZER, EM_CURSO)); // desbloqueio para estado ativo
        TRANSITIONS.put(CONCLUIDA, EnumSet.noneOf(SpecTaskStatus.class)); // terminal
    }

    public Set<SpecTaskStatus> allowedTargets(SpecTaskStatus from) {
        return TRANSITIONS.getOrDefault(from, EnumSet.noneOf(SpecTaskStatus.class));
    }

    public boolean canTransition(SpecTaskStatus from, SpecTaskStatus to) {
        return from != null && to != null && allowedTargets(from).contains(to);
    }

    public boolean isPass5Only(SpecTaskStatus to) {
        return PASS5_ONLY.contains(to);
    }
}
