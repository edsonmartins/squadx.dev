package dev.squadx.model.enums;

/**
 * What the dispatcher decided to do with an incoming execution request before creating a run.
 * See RFC-0005 §2.
 */
public enum RunAdmissionAction {
    START,
    DROP_DUPLICATE,
    QUEUE_FOLLOW_UP,
    NEEDS_HUMAN_DECISION
}
