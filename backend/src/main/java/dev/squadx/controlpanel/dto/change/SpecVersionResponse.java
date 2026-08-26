package dev.squadx.controlpanel.dto.change;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Value;
import java.time.Instant;

@Value @Builder
public class SpecVersionResponse {
    Long id;
    String version;
    boolean current;
    String summary;
    @JsonProperty("author_id") Long authorId;
    @JsonProperty("commit_sha") String commitSha;
    @JsonProperty("created_at") Instant createdAt;
}
