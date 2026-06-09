package dev.squadx.model.enums;

/**
 * Outcome of a single autopilot run.
 * <ul>
 *   <li>{@code SUCCESS} – the task (and execution, for RUN_TASK) was created/dispatched.</li>
 *   <li>{@code SKIPPED} – the admission gate blocked dispatch (e.g. no online agent).</li>
 *   <li>{@code FAILED} – an error occurred while firing the autopilot.</li>
 * </ul>
 */
public enum AutopilotRunStatus {
    SUCCESS,
    SKIPPED,
    FAILED
}
