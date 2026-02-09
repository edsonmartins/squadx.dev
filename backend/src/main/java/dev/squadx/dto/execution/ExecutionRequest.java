package dev.squadx.dto.execution;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ExecutionRequest {

    @NotNull(message = "Task ID is required")
    @JsonProperty("task_id")
    private Long taskId;

    @JsonProperty("agent_id")
    private Long agentId;
}
