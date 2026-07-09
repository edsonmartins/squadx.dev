package dev.squadx.model;

import dev.squadx.model.enums.FollowUpStatus;
import jakarta.persistence.*;
import lombok.*;

/**
 * A trigger that arrived while a run was already active for the same task (RFC-0005 §2.3). It is
 * persisted instead of starting concurrent work, and promoted to a new execution when the active
 * run terminates.
 */
@Entity
@Table(name = "follow_up_requests")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FollowUpRequest extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "task_id", nullable = false)
    private Task task;

    @Column(name = "organization_id", nullable = false)
    private Long organizationId;

    @Column(name = "active_execution_id")
    private Long activeExecutionId;

    @Column(name = "requested_agent_id")
    private Long requestedAgentId;

    @Column(name = "requested_by_email")
    private String requestedByEmail;

    @Column(name = "source_payload", columnDefinition = "TEXT")
    private String sourcePayload;

    @Column(columnDefinition = "TEXT")
    private String decision;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private FollowUpStatus status = FollowUpStatus.PENDING;
}
