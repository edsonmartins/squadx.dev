package dev.squadx.dto.meeting;

import com.fasterxml.jackson.annotation.JsonProperty;
import dev.squadx.model.enums.MeetingStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MeetingResponse {

    private Long id;
    private String title;
    private String description;

    @JsonProperty("scheduled_at")
    private Instant scheduledAt;

    @JsonProperty("duration_minutes")
    private Integer durationMinutes;

    @JsonProperty("meeting_url")
    private String meetingUrl;

    private MeetingStatus status;

    @JsonProperty("organization_id")
    private Long organizationId;

    @JsonProperty("created_by_id")
    private Long createdById;

    @JsonProperty("created_by_name")
    private String createdByName;

    private List<AttendeeResponse> attendees;

    @JsonProperty("created_at")
    private Instant createdAt;
}
