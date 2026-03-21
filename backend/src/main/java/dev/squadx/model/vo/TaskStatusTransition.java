package dev.squadx.model.vo;

import dev.squadx.model.enums.TaskStatus;

import java.util.Map;
import java.util.Set;

/**
 * Immutable value object that validates task status transitions.
 * Construction fails if the transition is not allowed.
 */
public record TaskStatusTransition(TaskStatus from, TaskStatus to) {

    private static final Map<TaskStatus, Set<TaskStatus>> ALLOWED = Map.of(
            TaskStatus.TODO, Set.of(TaskStatus.IN_PROGRESS, TaskStatus.CANCELLED),
            TaskStatus.IN_PROGRESS, Set.of(TaskStatus.IN_REVIEW, TaskStatus.BLOCKED, TaskStatus.DONE, TaskStatus.CANCELLED),
            TaskStatus.IN_REVIEW, Set.of(TaskStatus.DONE, TaskStatus.IN_PROGRESS, TaskStatus.CANCELLED),
            TaskStatus.BLOCKED, Set.of(TaskStatus.TODO, TaskStatus.IN_PROGRESS, TaskStatus.CANCELLED),
            TaskStatus.DONE, Set.of(),
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
