package dev.squadx.dto.squad;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SquadResponse {

    private Long id;
    private String name;
    private String description;

    @JsonProperty("is_active")
    private boolean isActive;

    @JsonProperty("organization_id")
    private Long organizationId;

    @JsonProperty("organization_name")
    private String organizationName;

    @JsonProperty("agents_count")
    private Integer agentsCount;

    @JsonProperty("projects_count")
    private Integer projectsCount;

    private List<AgentSummary> agents;

    @JsonProperty("created_at")
    private Instant createdAt;

    @JsonProperty("updated_at")
    private Instant updatedAt;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AgentSummary {
        private Long id;
        private String name;

        @JsonProperty("agent_type")
        private String agentType;

        @JsonProperty("is_active")
        private boolean isActive;
    }
}
