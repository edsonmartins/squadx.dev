package dev.squadx.model.enums;

/** Lifecycle of a durable follow-up request queued behind an active run. See RFC-0005 §2.3. */
public enum FollowUpStatus {
    PENDING,
    PROMOTED,
    CANCELLED
}
