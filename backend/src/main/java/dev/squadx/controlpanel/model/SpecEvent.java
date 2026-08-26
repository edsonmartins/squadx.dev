package dev.squadx.controlpanel.model;

import dev.squadx.controlpanel.model.enums.EventSource;
import dev.squadx.controlpanel.model.enums.TaskEventType;
import dev.squadx.model.BaseEntity;
import dev.squadx.model.User;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

/**
 * Evento append-only de uma tarefa. O estado da tarefa é projeção destes eventos (ADR-0002,
 * RFC-0003). {@code dedupKey} garante idempotência da ingestão.
 */
@Entity
@Table(name = "spec_events")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SpecEvent extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "spec_task_id", nullable = false)
    private SpecTask specTask;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "actor_user_id")
    private User actor;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TaskEventType type;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private EventSource source;

    @Column(name = "source_ref")
    private String sourceRef;

    @Column(columnDefinition = "TEXT")
    private String payload;

    @Column(name = "dedup_key", nullable = false, unique = true)
    private String dedupKey;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    @Column(name = "received_at", nullable = false)
    @Builder.Default
    private Instant receivedAt = Instant.now();
}
