package dev.squadx.dto.recording;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class StartRecordingRequest {

    @NotNull(message = "Session ID is required")
    @JsonProperty("session_id")
    private Long sessionId;
}
