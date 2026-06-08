package dev.squadx.dto.autopilot;

import com.fasterxml.jackson.annotation.JsonProperty;
import dev.squadx.model.enums.AutopilotExecutionMode;
import dev.squadx.model.enums.TaskPriority;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AutopilotResponse {

    private Long id;
    private String name;
    private String description;

    @JsonProperty("cron_expression")
    private String cronExpression;

    private String timezone;

    @JsonProperty("execution_mode")
    private AutopilotExecutionMode executionMode;

    @JsonProperty("organization_id")
    private Long organizationId;

    @JsonProperty("project_id")
    private Long projectId;

    @JsonProperty("project_name")
    private String projectName;

    @JsonProperty("target_squad_id")
    private Long targetSquadId;

    @JsonProperty("target_squad_name")
    private String targetSquadName;

    @JsonProperty("target_agent_id")
    private Long targetAgentId;

    @JsonProperty("target_agent_name")
    private String targetAgentName;

    @JsonProperty("task_title")
    private String taskTitle;

    @JsonProperty("task_description")
    private String taskDescription;

    @JsonProperty("task_priority")
    private TaskPriority taskPriority;

    private Boolean enabled;

    @JsonProperty("last_run_at")
    private Instant lastRunAt;

    @JsonProperty("next_run_at")
    private Instant nextRunAt;

    @JsonProperty("run_count")
    private Integer runCount;

    @JsonProperty("webhook_token")
    private String webhookToken;

    @JsonProperty("created_by_id")
    private Long createdById;

    @JsonProperty("created_by_name")
    private String createdByName;

    @JsonProperty("created_at")
    private Instant createdAt;

    @JsonProperty("updated_at")
    private Instant updatedAt;
}
