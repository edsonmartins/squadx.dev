package dev.squadx.dto.autopilot;

import com.fasterxml.jackson.annotation.JsonProperty;
import dev.squadx.model.enums.AutopilotExecutionMode;
import dev.squadx.model.enums.TaskPriority;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AutopilotRequest {

    @NotBlank(message = "Name is required")
    private String name;

    private String description;

    @NotBlank(message = "Cron expression is required")
    @JsonProperty("cron_expression")
    private String cronExpression;

    private String timezone;

    @JsonProperty("execution_mode")
    private AutopilotExecutionMode executionMode;

    @NotNull(message = "Project ID is required")
    @JsonProperty("project_id")
    private Long projectId;

    @JsonProperty("target_squad_id")
    private Long targetSquadId;

    @JsonProperty("target_agent_id")
    private Long targetAgentId;

    @NotBlank(message = "Task title is required")
    @JsonProperty("task_title")
    private String taskTitle;

    @JsonProperty("task_description")
    private String taskDescription;

    @JsonProperty("task_priority")
    private TaskPriority taskPriority;

    private Boolean enabled;
}
