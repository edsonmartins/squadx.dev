package dev.squadx.model;

import dev.squadx.model.enums.ExecutionStatus;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "executions")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Execution extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "task_id", nullable = false)
    private Task task;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "agent_id", nullable = false)
    private Agent agent;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private ExecutionStatus status = ExecutionStatus.PENDING;

    @Column(name = "started_at")
    private Instant startedAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Column(name = "container_id")
    private String containerId;

    @Column(name = "brain_sentry_session_id")
    private String brainSentrySessionId;

    @Column(name = "input_tokens")
    @Builder.Default
    private Long inputTokens = 0L;

    @Column(name = "output_tokens")
    @Builder.Default
    private Long outputTokens = 0L;

    @Column(name = "total_cost")
    @Builder.Default
    private Double totalCost = 0.0;

    @Column(columnDefinition = "TEXT")
    private String result;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    @Column(name = "git_branch")
    private String gitBranch;

    @Column(name = "git_commit")
    private String gitCommit;

    /**
     * Idempotency key for admission dedup (RFC-0005 §2.1). Unique per task when non-null; a replay
     * with the same key resolves to {@code drop_duplicate} instead of creating a new run.
     */
    @Column(name = "idempotency_key")
    private String idempotencyKey;

    @OneToMany(mappedBy = "execution", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("createdAt ASC")
    @Builder.Default
    private List<ExecutionLog> logs = new ArrayList<>();

    @OneToMany(mappedBy = "execution", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<ExecutionArtifact> artifacts = new ArrayList<>();
}
