package dev.squadx.dto.artifact;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class PublishArtifactRequest {
    @NotBlank
    @Size(max = 160)
    @JsonProperty("artifact_key")
    private String artifactKey;

    @NotBlank
    @Size(max = 64)
    private String type;

    @NotBlank
    @Size(max = 32)
    private String format;

    @NotBlank
    @Size(max = 255)
    private String name;

    @Size(max = 128)
    @JsonProperty("git_revision")
    private String gitRevision;

    @Size(max = 128)
    @JsonProperty("base_revision")
    private String baseRevision;

    @Size(max = 160)
    @JsonProperty("artifact_group")
    private String artifactGroup;

    @Size(max = 32)
    @JsonProperty("view_role")
    private String viewRole;

    @JsonProperty("evidence_json")
    private String evidenceJson;

    @NotBlank
    @Size(max = 10_000_000)
    private String content;
}
