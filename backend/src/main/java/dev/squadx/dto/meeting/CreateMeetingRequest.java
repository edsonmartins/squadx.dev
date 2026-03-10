package dev.squadx.dto.meeting;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CreateMeetingRequest {

    @NotNull
    @JsonProperty("organization_id")
    private Long organizationId;

    @NotBlank
    private String title;

    private String description;

    @NotNull
    @JsonProperty("scheduled_at")
    private Instant scheduledAt;

    @JsonProperty("duration_minutes")
    private Integer durationMinutes;

    @JsonProperty("meeting_url")
    private String meetingUrl;

    @JsonProperty("attendee_ids")
    private List<Long> attendeeIds;
}
