package dev.squadx.dto.autopilot;

import com.fasterxml.jackson.annotation.JsonProperty;
import dev.squadx.model.enums.AutopilotRunStatus;
import dev.squadx.model.enums.AutopilotTriggerType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AutopilotRunResponse {

    private Long id;

    @JsonProperty("autopilot_id")
    private Long autopilotId;

    @JsonProperty("trigger_type")
    private AutopilotTriggerType triggerType;

    private AutopilotRunStatus status;

    @JsonProperty("created_task_id")
    private Long createdTaskId;

    @JsonProperty("execution_id")
    private Long executionId;

    private String message;

    @JsonProperty("triggered_at")
    private Instant triggeredAt;
}
