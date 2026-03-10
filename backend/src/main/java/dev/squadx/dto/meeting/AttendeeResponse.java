package dev.squadx.dto.meeting;

import com.fasterxml.jackson.annotation.JsonProperty;
import dev.squadx.model.enums.RsvpStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AttendeeResponse {

    @JsonProperty("user_id")
    private Long userId;

    @JsonProperty("user_name")
    private String userName;

    @JsonProperty("user_email")
    private String userEmail;

    @JsonProperty("rsvp_status")
    private RsvpStatus rsvpStatus;
}
