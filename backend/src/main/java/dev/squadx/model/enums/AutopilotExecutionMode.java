package dev.squadx.model.enums;

/**
 * How an autopilot acts when it fires.
 * <ul>
 *   <li>{@code CREATE_TASK} – only creates a task on the board (someone/something runs it later).</li>
 *   <li>{@code RUN_TASK} – creates the task and immediately dispatches it to the target agent.</li>
 * </ul>
 */
public enum AutopilotExecutionMode {
    CREATE_TASK,
    RUN_TASK
}
