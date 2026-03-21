package dev.squadx.dto.notification;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NotificationConfigRequest {

    @NotBlank(message = "Channel is required")
    private String channel;

    @NotBlank(message = "Webhook URL is required")
    private String webhookUrl;

    private String channelName;
    private Boolean notifyTaskCompleted;
    private Boolean notifyTaskFailed;
    private Boolean notifyApprovalRequired;
    private Boolean notifyAgentError;
    private Boolean enabled;
}
