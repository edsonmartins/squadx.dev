package dev.squadx.dto.execution;

import com.fasterxml.jackson.annotation.JsonProperty;
import dev.squadx.model.enums.FollowUpStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/** A durable follow-up request queued behind an active run (RFC-0005 §2.3). */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FollowUpResponse {

    private Long id;

    @JsonProperty("task_id")
    private Long taskId;

    @JsonProperty("active_execution_id")
    private Long activeExecutionId;

    @JsonProperty("requested_agent_id")
    private Long requestedAgentId;

    private FollowUpStatus status;

    @JsonProperty("created_at")
    private Instant createdAt;
}
