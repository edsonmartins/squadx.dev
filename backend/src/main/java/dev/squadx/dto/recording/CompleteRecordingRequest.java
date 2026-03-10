package dev.squadx.dto.recording;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.Min;
import lombok.Data;

@Data
public class CompleteRecordingRequest {

    @JsonProperty("file_size_bytes")
    @Min(value = 0, message = "File size must be non-negative")
    private Long fileSizeBytes;

    @JsonProperty("duration_seconds")
    @Min(value = 0, message = "Duration must be non-negative")
    private Integer durationSeconds;
}
