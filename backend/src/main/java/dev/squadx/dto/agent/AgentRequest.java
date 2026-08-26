package dev.squadx.dto.agent;

import com.fasterxml.jackson.annotation.JsonProperty;
import dev.squadx.dto.validation.OnCreate;
import dev.squadx.model.enums.AgentRuntimeKind;
import dev.squadx.model.enums.AgentType;
import dev.squadx.model.enums.CliProvider;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Set;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AgentRequest {

    // Required on create only; update is partial and validates with the Default group.
    @NotBlank(message = "Name is required", groups = OnCreate.class)
    private String name;

    @NotNull(message = "Agent type is required", groups = OnCreate.class)
    @JsonProperty("agent_type")
    private AgentType agentType;

    @JsonProperty("runtime_kind")
    private AgentRuntimeKind runtimeKind;

    @JsonProperty("cli_provider")
    private CliProvider cliProvider;

    private String description;

    @JsonProperty("model_id")
    private String modelId;

    @JsonProperty("system_prompt")
    private String systemPrompt;

    @JsonProperty("max_tokens")
    private Integer maxTokens;

    private Double temperature;

    @NotNull(message = "Squad ID is required", groups = OnCreate.class)
    @JsonProperty("squad_id")
    private Long squadId;

    @JsonProperty("harness_id")
    private Long harnessId;

    private Set<String> capabilities;
}
