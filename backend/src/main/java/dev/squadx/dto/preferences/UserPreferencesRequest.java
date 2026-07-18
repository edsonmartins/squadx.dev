package dev.squadx.dto.preferences;

import com.fasterxml.jackson.annotation.JsonProperty;
import dev.squadx.model.enums.LiveViewQuality;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Full-replace update of a user's preferences. Every field is required so an omitted
 * flag can never silently flip a toggle off (boxed types + {@code @NotNull} instead of
 * primitives, which would default a missing field to {@code false}).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserPreferencesRequest {

    @NotNull
    @JsonProperty("email_notifications")
    private Boolean emailNotifications;

    @NotNull
    @JsonProperty("push_notifications")
    private Boolean pushNotifications;

    @NotNull
    @JsonProperty("execution_alerts")
    private Boolean executionAlerts;

    @NotNull
    @JsonProperty("live_session_alerts")
    private Boolean liveSessionAlerts;

    @NotNull
    @JsonProperty("auto_start_live")
    private Boolean autoStartLive;

    @NotNull
    @JsonProperty("default_quality")
    private LiveViewQuality defaultQuality;

    @NotNull
    @Min(1)
    @Max(50)
    @JsonProperty("max_viewers")
    private Integer maxViewers;
}
