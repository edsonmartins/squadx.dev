package dev.squadx.dto.artifact;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.*;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ExecutionArtifactResponse {
    private Long id;
    @JsonProperty("execution_id") private Long executionId;
    @JsonProperty("artifact_key") private String artifactKey;
    private String type;
    private String format;
    private String name;
    @JsonProperty("git_revision") private String gitRevision;
    @JsonProperty("base_revision") private String baseRevision;
    @JsonProperty("artifact_group") private String artifactGroup;
    @JsonProperty("view_role") private String viewRole;
    @JsonProperty("checksum_sha256") private String checksumSha256;
    @JsonProperty("evidence_json") private String evidenceJson;
    @JsonInclude(JsonInclude.Include.NON_NULL) private String content;
    @JsonProperty("created_at") private Instant createdAt;
    @JsonProperty("updated_at") private Instant updatedAt;
}
