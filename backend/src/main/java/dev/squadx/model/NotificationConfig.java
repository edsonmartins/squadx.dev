package dev.squadx.model;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "notification_configs")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class NotificationConfig extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "organization_id", nullable = false)
    private Organization organization;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private NotificationChannel channel;

    @Column(name = "webhook_url", nullable = false)
    private String webhookUrl;

    @Column(name = "channel_name")
    private String channelName;

    @Column(name = "notify_task_completed")
    @Builder.Default
    private boolean notifyTaskCompleted = true;

    @Column(name = "notify_task_failed")
    @Builder.Default
    private boolean notifyTaskFailed = true;

    @Column(name = "notify_approval_required")
    @Builder.Default
    private boolean notifyApprovalRequired = true;

    @Column(name = "notify_agent_error")
    @Builder.Default
    private boolean notifyAgentError = true;

    @Column(nullable = false)
    @Builder.Default
    private boolean enabled = true;
}
