package dev.squadx.dto.highlight;

import com.fasterxml.jackson.annotation.JsonProperty;
import dev.squadx.model.enums.HighlightType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HighlightResponse {

    private Long id;

    @JsonProperty("recording_id")
    private Long recordingId;

    @JsonProperty("highlight_type")
    private HighlightType highlightType;

    @JsonProperty("timestamp_seconds")
    private Integer timestampSeconds;

    private String title;

    private String description;

    private Float confidence;

    private String metadata;

    @JsonProperty("created_at")
    private Instant createdAt;
}
