package dev.squadx.model.vo;

import dev.squadx.model.enums.TaskStatus;

import java.util.Map;
import java.util.Set;

/**
 * Immutable value object that validates task status transitions.
 * Construction fails if the transition is not allowed.
 *
 * <p>Nota (T-0011-7 / ADR-0011): manteve-se <code>IN_REVIEW → DONE</code> de propósito.
 * No fluxo clássico (domínio <em>Task</em>), o único produtor de <code>DONE</code> é o
 * <code>ApprovalService.review</code>, que valida esta transição via
 * {@link #isValid}. É exatamente o portão de aprovação humana que o ADR-0004 defende
 * ("concluída só via revisão"). O <code>Pass 5</code> do Control Panel usa a máquina
 * separada <code>SpecTaskStatus</code>; fechar aqui quebraria a aprovação de tasks sem
 * trazer o efeito pretendido.</p>
 */
public record TaskStatusTransition(TaskStatus from, TaskStatus to) {

    private static final Map<TaskStatus, Set<TaskStatus>> ALLOWED = Map.of(
            TaskStatus.TODO, Set.of(TaskStatus.IN_PROGRESS, TaskStatus.CANCELLED),
            TaskStatus.IN_PROGRESS, Set.of(TaskStatus.IN_REVIEW, TaskStatus.BLOCKED, TaskStatus.DONE, TaskStatus.CANCELLED),
            TaskStatus.IN_REVIEW, Set.of(TaskStatus.DONE, TaskStatus.IN_PROGRESS, TaskStatus.CANCELLED),
            TaskStatus.BLOCKED, Set.of(TaskStatus.TODO, TaskStatus.IN_PROGRESS, TaskStatus.CANCELLED),
            TaskStatus.DONE, Set.of(TaskStatus.IN_PROGRESS),
            TaskStatus.CANCELLED, Set.of(TaskStatus.TODO)
    );

    public TaskStatusTransition {
        Set<TaskStatus> allowed = ALLOWED.getOrDefault(from, Set.of());
        if (!allowed.contains(to)) {
            throw new IllegalStateException(
                    "Invalid task status transition: " + from + " -> " + to +
                    ". Allowed from " + from + ": " + allowed);
        }
    }

    /**
     * Check if a transition is valid without throwing.
     */
    public static boolean isValid(TaskStatus from, TaskStatus to) {
        return ALLOWED.getOrDefault(from, Set.of()).contains(to);
    }
}
