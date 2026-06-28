package dev.squadx.model.enums;

/** Why an admission decision was reached. See RFC-0005 §2. */
public enum RunAdmissionReasonCode {
    NEW_EVENT,
    DUPLICATE_SOURCE_EVENT,
    ACTIVE_RUN_SAME_TASK,
    ACTOR_NOT_ALLOWED_FOR_WRITE,
    POLICY_REJECTED
}
