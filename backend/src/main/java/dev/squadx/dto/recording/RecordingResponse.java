package dev.squadx.dto.recording;

import com.fasterxml.jackson.annotation.JsonProperty;
import dev.squadx.model.enums.RecordingStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RecordingResponse {

    private Long id;

    @JsonProperty("session_id")
    private Long sessionId;

    @JsonProperty("s3_key")
    private String s3Key;

    @JsonProperty("s3_bucket")
    private String s3Bucket;

    @JsonProperty("upload_url")
    private String uploadUrl;

    @JsonProperty("playback_url")
    private String playbackUrl;

    @JsonProperty("duration_seconds")
    private Integer durationSeconds;

    @JsonProperty("file_size_bytes")
    private Long fileSizeBytes;

    private RecordingStatus status;

    @JsonProperty("started_at")
    private Instant startedAt;

    @JsonProperty("completed_at")
    private Instant completedAt;

    @JsonProperty("created_at")
    private Instant createdAt;
}
