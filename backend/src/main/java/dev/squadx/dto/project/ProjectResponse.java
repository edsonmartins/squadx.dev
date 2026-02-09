package dev.squadx.dto.project;

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
public class ProjectResponse {

    private Long id;
    private String name;
    private String slug;
    private String description;

    @JsonProperty("repository_url")
    private String repositoryUrl;

    @JsonProperty("default_branch")
    private String defaultBranch;

    @JsonProperty("is_active")
    private boolean isActive;

    @JsonProperty("organization_id")
    private Long organizationId;

    @JsonProperty("organization_name")
    private String organizationName;

    @JsonProperty("squad_id")
    private Long squadId;

    @JsonProperty("squad_name")
    private String squadName;

    @JsonProperty("tasks_count")
    private Integer tasksCount;

    @JsonProperty("created_at")
    private Instant createdAt;

    @JsonProperty("updated_at")
    private Instant updatedAt;
}
