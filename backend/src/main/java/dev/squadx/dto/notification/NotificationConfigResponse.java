package dev.squadx.dto.notification;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class NotificationConfigResponse {

    private Long id;

    @JsonProperty("organization_id")
    private Long organizationId;

    private String channel;

    @JsonProperty("webhook_url")
    private String webhookUrl;

    @JsonProperty("channel_name")
    private String channelName;

    @JsonProperty("notify_task_completed")
    private boolean notifyTaskCompleted;

    @JsonProperty("notify_task_failed")
    private boolean notifyTaskFailed;

    @JsonProperty("notify_approval_required")
    private boolean notifyApprovalRequired;

    @JsonProperty("notify_agent_error")
    private boolean notifyAgentError;

    private boolean enabled;

    @JsonProperty("created_at")
    private Instant createdAt;

    @JsonProperty("updated_at")
    private Instant updatedAt;
}
