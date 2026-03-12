package dev.squadx.dto.highlight;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GenerateHighlightsRequest {

    @NotNull
    @JsonProperty("recording_id")
    private Long recordingId;

    @JsonProperty("execution_logs")
    private List<String> executionLogs;
}
