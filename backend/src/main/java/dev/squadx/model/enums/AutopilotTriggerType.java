package dev.squadx.model.enums;

/**
 * What caused an autopilot run.
 * {@code CRON} – fired by the schedule. {@code MANUAL} – triggered on demand via the API.
 * {@code WEBHOOK} – fired via the autopilot's public webhook URL.
 */
public enum AutopilotTriggerType {
    CRON,
    MANUAL,
    WEBHOOK
}
