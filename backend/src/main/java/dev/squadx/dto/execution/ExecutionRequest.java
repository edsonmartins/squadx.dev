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

    /**
     * Optional idempotency key for admission dedup (RFC-0005 §2.1). When two requests carry the same
     * key for the same task, only the first creates a run; the rest resolve to {@code drop_duplicate}.
     */
    @JsonProperty("idempotency_key")
    private String idempotencyKey;
}
