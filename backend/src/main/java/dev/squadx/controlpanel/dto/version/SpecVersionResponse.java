package dev.squadx.controlpanel.dto.version;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SpecVersionResponse {

    private Long id;
    private int version;
    private boolean current;
    private String summary;
    private String commit;

    @JsonProperty("change_id")
    private Long changeId;

    @JsonProperty("author_id")
    private Long authorId;

    @JsonProperty("author_name")
    private String authorName;

    @JsonProperty("created_at")
    private Instant createdAt;
}
