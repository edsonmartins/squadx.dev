package dev.squadx.dto.project;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProjectRequest {

    @NotBlank(message = "Name is required")
    private String name;

    private String description;

    @JsonProperty("repository_url")
    private String repositoryUrl;

    @JsonProperty("default_branch")
    private String defaultBranch;

    @JsonProperty("organization_id")
    private Long organizationId;

    @JsonProperty("squad_id")
    private Long squadId;
}
