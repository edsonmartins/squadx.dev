package dev.squadx.controlpanel.dto.harness;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HarnessRequest {

    @NotNull(message = "organization_id is required")
    @JsonProperty("organization_id")
    private Long organizationId;

    @NotBlank(message = "key is required")
    private String key;

    @NotBlank(message = "name is required")
    private String name;

    private String vendor;

    /** Modelos LLM disponíveis para este harness. */
    private List<String> models;

    /** Agente (assignee) ao qual este harness está vinculado (opcional). */
    @JsonProperty("agent_id")
    private Long agentId;
}
