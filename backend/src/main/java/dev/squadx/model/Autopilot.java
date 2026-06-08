package dev.squadx.model;

import dev.squadx.model.enums.AutopilotExecutionMode;
import dev.squadx.model.enums.TaskPriority;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

/**
 * A scheduled/recurring job that auto-creates (and optionally runs) tasks.
 * One JobRunr recurring job is registered per enabled autopilot.
 */
@Entity
@Table(name = "autopilots")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Autopilot extends BaseEntity {

    @Column(nullable = false)
    private String name;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(name = "cron_expression", nullable = false)
    private String cronExpression;

    @Column(nullable = false)
    @Builder.Default
    private String timezone = "UTC";

    @Enumerated(EnumType.STRING)
    @Column(name = "execution_mode", nullable = false)
    @Builder.Default
    private AutopilotExecutionMode executionMode = AutopilotExecutionMode.CREATE_TASK;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "organization_id", nullable = false)
    private Organization organization;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "project_id", nullable = false)
    private Project project;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "target_squad_id")
    private Squad targetSquad;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "target_agent_id")
    private Agent targetAgent;

    @Column(name = "task_title", nullable = false)
    private String taskTitle;

    @Column(name = "task_description", columnDefinition = "TEXT")
    private String taskDescription;

    @Enumerated(EnumType.STRING)
    @Column(name = "task_priority", nullable = false)
    @Builder.Default
    private TaskPriority taskPriority = TaskPriority.MEDIUM;

    @Column(nullable = false)
    @Builder.Default
    private boolean enabled = true;

    @Column(name = "last_run_at")
    private Instant lastRunAt;

    @Column(name = "next_run_at")
    private Instant nextRunAt;

    @Column(name = "run_count", nullable = false)
    @Builder.Default
    private Integer runCount = 0;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by_id")
    private User createdBy;
}
