package dev.squadx.dto.task;

import com.fasterxml.jackson.annotation.JsonProperty;
import dev.squadx.model.enums.TaskPriority;
import dev.squadx.model.enums.TaskStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
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
public class TaskRequest {

    @NotBlank(message = "Title is required")
    private String title;

    private String description;

    private TaskStatus status;

    private TaskPriority priority;

    @JsonProperty("story_points")
    private Integer storyPoints;

    @JsonProperty("estimated_hours")
    private Double estimatedHours;

    @JsonProperty("due_date")
    private Instant dueDate;

    @NotNull(message = "Project ID is required")
    @JsonProperty("project_id")
    private Long projectId;

    @JsonProperty("parent_task_id")
    private Long parentTaskId;

    @JsonProperty("assigned_agent_id")
    private Long assignedAgentId;

    private Set<String> tags;
}
