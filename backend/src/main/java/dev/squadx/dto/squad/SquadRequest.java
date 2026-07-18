package dev.squadx.dto.squad;

import com.fasterxml.jackson.annotation.JsonProperty;
import dev.squadx.model.enums.SandboxEgressPolicy;
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
public class SquadRequest {

    @NotBlank(message = "Name is required")
    private String name;

    private String description;

    @NotNull(message = "Organization ID is required")
    @JsonProperty("organization_id")
    private Long organizationId;

    @JsonProperty("leader_agent_id")
    private Long leaderAgentId;

    /**
     * Egress policy for this squad's agents (RFC-0006). Null leaves it unchanged on
     * update, and takes the entity default (AGENT_DEFAULT) on create — a squad is never
     * created without a policy.
     */
    @JsonProperty("sandbox_egress_policy")
    private SandboxEgressPolicy sandboxEgressPolicy;
}
