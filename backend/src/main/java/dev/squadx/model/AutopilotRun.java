package dev.squadx.model;

import dev.squadx.model.enums.AutopilotRunStatus;
import dev.squadx.model.enums.AutopilotTriggerType;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

/**
 * History record of a single autopilot firing (cron or manual).
 */
@Entity
@Table(name = "autopilot_runs")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AutopilotRun {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "autopilot_id", nullable = false)
    private Autopilot autopilot;

    @Enumerated(EnumType.STRING)
    @Column(name = "trigger_type", nullable = false)
    private AutopilotTriggerType triggerType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AutopilotRunStatus status;

    @Column(name = "created_task_id")
    private Long createdTaskId;

    @Column(name = "execution_id")
    private Long executionId;

    @Column(columnDefinition = "TEXT")
    private String message;

    @Column(name = "triggered_at", nullable = false)
    @Builder.Default
    private Instant triggeredAt = Instant.now();
}
