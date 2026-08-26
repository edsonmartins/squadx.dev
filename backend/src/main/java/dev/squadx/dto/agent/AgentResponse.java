package dev.squadx.dto.agent;

import com.fasterxml.jackson.annotation.JsonProperty;
import dev.squadx.model.enums.AgentRuntimeKind;
import dev.squadx.model.enums.AgentType;
import dev.squadx.model.enums.CliProvider;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.Set;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AgentResponse {

    private Long id;
    private String name;

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

    @JsonProperty("is_active")
    private boolean isActive;

    @JsonProperty("squad_id")
    private Long squadId;

    @JsonProperty("squad_name")
    private String squadName;

    @JsonProperty("harness_id")
    private Long harnessId;

    @JsonProperty("harness_name")
    private String harnessName;

    @JsonProperty("harness_model")
    private String harnessModel;

    private Set<String> capabilities;

    @JsonProperty("executions_count")
    private Integer executionsCount;

    @JsonProperty("created_at")
    private Instant createdAt;

    @JsonProperty("updated_at")
    private Instant updatedAt;
}
