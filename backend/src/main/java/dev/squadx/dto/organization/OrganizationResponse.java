package dev.squadx.dto.organization;

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
public class OrganizationResponse {

    private Long id;
    private String name;
    private String slug;
    private String description;

    @JsonProperty("logo_url")
    private String logoUrl;

    @JsonProperty("is_active")
    private boolean isActive;

    @JsonProperty("members_count")
    private Integer membersCount;

    @JsonProperty("projects_count")
    private Integer projectsCount;

    @JsonProperty("squads_count")
    private Integer squadsCount;

    @JsonProperty("created_at")
    private Instant createdAt;

    @JsonProperty("updated_at")
    private Instant updatedAt;
}
