package dev.squadx.dto.notification;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NotificationConfigRequest {

    private String channel;
    private String webhookUrl;
    private String channelName;
    private Boolean notifyTaskCompleted;
    private Boolean notifyTaskFailed;
    private Boolean notifyApprovalRequired;
    private Boolean notifyAgentError;
    private Boolean enabled;
}
