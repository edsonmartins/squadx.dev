package dev.squadx.service;

import java.util.Map;

/**
 * Default visibility/importance for an execution log/event (Attention Budget — ADR-0007, RFC-0005 §1).
 *
 * <p>Resolution order: (1) explicit metadata sent by the client wins; (2) a known event type maps
 * via {@link #BY_TYPE}; (3) otherwise fall back by log level. Unknown types/levels resolve to the
 * safe default {@code human/normal} — never hide an event by accident.
 */
public final class RunEventMetadata {

    public static final String VIS_HUMAN = "human";
    public static final String VIS_AUDIT = "audit";
    public static final String VIS_DEBUG = "debug";

    public static final String IMP_LOW = "low";
    public static final String IMP_NORMAL = "normal";
    public static final String IMP_HIGH = "high";
    public static final String IMP_BLOCKING = "blocking";

    public record Metadata(String visibility, String importance) {}

    private static final Metadata HUMAN_NORMAL = new Metadata(VIS_HUMAN, IMP_NORMAL);

    /** Known event types → metadata. Mirrors opentag thread-runtime-design Delta 7. */
    private static final Map<String, Metadata> BY_TYPE = Map.ofEntries(
            Map.entry("run.created", new Metadata(VIS_AUDIT, IMP_NORMAL)),
            Map.entry("admission.decided", new Metadata(VIS_AUDIT, IMP_NORMAL)),
            Map.entry("follow_up_request.queued", new Metadata(VIS_AUDIT, IMP_NORMAL)),
            Map.entry("subtask.started", new Metadata(VIS_DEBUG, IMP_LOW)),
            Map.entry("tool.log", new Metadata(VIS_DEBUG, IMP_LOW)),
            Map.entry("agent.prompt", new Metadata(VIS_AUDIT, IMP_LOW)),
            Map.entry("context_packet.generated", new Metadata(VIS_AUDIT, IMP_LOW)),
            Map.entry("run.escalated", new Metadata(VIS_HUMAN, IMP_BLOCKING)),
            Map.entry("run.blocked", new Metadata(VIS_HUMAN, IMP_BLOCKING)),
            Map.entry("run.completed", new Metadata(VIS_HUMAN, IMP_HIGH)),
            Map.entry("run.failed", new Metadata(VIS_HUMAN, IMP_HIGH)),
            Map.entry("cost.budget_exceeded", new Metadata(VIS_HUMAN, IMP_BLOCKING))
    );

    private RunEventMetadata() {}

    /** Default metadata for a known event type, or null if the type is not recognized. */
    public static Metadata forType(String type) {
        return type == null ? null : BY_TYPE.get(type);
    }

    /**
     * Default metadata for a log level. Internal/diagnostic levels are audit; warnings/errors are
     * human-facing. Unknown level → safe default {@code human/normal}.
     */
    public static Metadata forLevel(String level) {
        if (level == null) return HUMAN_NORMAL;
        return switch (level.toUpperCase()) {
            case "TRACE", "DEBUG" -> new Metadata(VIS_DEBUG, IMP_LOW);
            case "INFO" -> new Metadata(VIS_AUDIT, IMP_NORMAL);
            case "WARN", "WARNING" -> new Metadata(VIS_HUMAN, IMP_NORMAL);
            case "ERROR", "FATAL" -> new Metadata(VIS_HUMAN, IMP_HIGH);
            default -> HUMAN_NORMAL;
        };
    }

    /**
     * Resolve metadata from optional explicit values, an optional event type, and a log level,
     * applying the resolution order described on the class.
     */
    public static Metadata resolve(String explicitVisibility, String explicitImportance, String type, String level) {
        Metadata base = forType(type);
        if (base == null) base = forLevel(level);
        String visibility = explicitVisibility != null && !explicitVisibility.isBlank()
                ? explicitVisibility : base.visibility();
        String importance = explicitImportance != null && !explicitImportance.isBlank()
                ? explicitImportance : base.importance();
        return new Metadata(visibility, importance);
    }
}
