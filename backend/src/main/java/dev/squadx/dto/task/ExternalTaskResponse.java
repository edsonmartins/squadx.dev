package dev.squadx.dto.task;

import com.fasterxml.jackson.annotation.JsonProperty;

public record ExternalTaskResponse(
        @JsonProperty("task_id") Long taskId,
        @JsonProperty("created") boolean created,
        @JsonProperty("execution_id") Long executionId,
        @JsonProperty("auto_start_status") String autoStartStatus
) {}
