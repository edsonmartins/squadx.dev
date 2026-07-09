package dev.squadx.dto.execution;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import dev.squadx.model.enums.ExecutionStatus;
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
public class ExecutionResponse {

    private Long id;

    @JsonProperty("task_id")
    private Long taskId;

    @JsonProperty("task_title")
    private String taskTitle;

    @JsonProperty("agent_id")
    private Long agentId;

    @JsonProperty("agent_name")
    private String agentName;

    @JsonProperty("agent_type")
    private String agentType;

    private ExecutionStatus status;

    @JsonProperty("started_at")
    private Instant startedAt;

    @JsonProperty("completed_at")
    private Instant completedAt;

    @JsonProperty("duration_seconds")
    private Long durationSeconds;

    @JsonProperty("container_id")
    private String containerId;

    @JsonProperty("brain_sentry_session_id")
    private String brainSentrySessionId;

    @JsonProperty("input_tokens")
    private Long inputTokens;

    @JsonProperty("output_tokens")
    private Long outputTokens;

    @JsonProperty("total_cost")
    private Double totalCost;

    private String result;

    @JsonProperty("error_message")
    private String errorMessage;

    @JsonProperty("git_branch")
    private String gitBranch;

    @JsonProperty("git_commit")
    private String gitCommit;

    private List<LogEntry> logs;

    /** Admission outcome for this request (RFC-0005 §2). Only populated on start-execution. */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private RunAdmissionDecision admission;

    @JsonProperty("created_at")
    private Instant createdAt;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class LogEntry {
        private Long id;
        private String level;

        /** Attention Budget channel (RFC-0005 §1): human | audit | debug. */
        private String visibility;

        /** Attention Budget rank (RFC-0005 §1): low | normal | high | blocking. */
        private String importance;

        private String message;
        private String metadata;

        @JsonProperty("created_at")
        private Instant createdAt;
    }
}
