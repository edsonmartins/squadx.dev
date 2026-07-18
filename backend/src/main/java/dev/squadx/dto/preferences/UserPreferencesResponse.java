package dev.squadx.dto.preferences;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import dev.squadx.model.enums.LiveViewQuality;
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
public class UserPreferencesResponse {

    @JsonProperty("email_notifications")
    private boolean emailNotifications;

    @JsonProperty("push_notifications")
    private boolean pushNotifications;

    @JsonProperty("execution_alerts")
    private boolean executionAlerts;

    @JsonProperty("live_session_alerts")
    private boolean liveSessionAlerts;

    @JsonProperty("auto_start_live")
    private boolean autoStartLive;

    @JsonProperty("default_quality")
    private LiveViewQuality defaultQuality;

    @JsonProperty("max_viewers")
    private int maxViewers;

    @JsonProperty("updated_at")
    private Instant updatedAt;
}
