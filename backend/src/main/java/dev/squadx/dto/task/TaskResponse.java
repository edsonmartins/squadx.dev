package dev.squadx.dto.task;

import com.fasterxml.jackson.annotation.JsonProperty;
import dev.squadx.model.enums.TaskPriority;
import dev.squadx.model.enums.TaskStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;
import java.util.Set;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TaskResponse {

    private Long id;
    private String title;
    private String description;
    private TaskStatus status;
    private TaskPriority priority;

    @JsonProperty("story_points")
    private Integer storyPoints;

    @JsonProperty("estimated_hours")
    private Double estimatedHours;

    @JsonProperty("actual_hours")
    private Double actualHours;

    @JsonProperty("due_date")
    private Instant dueDate;

    @JsonProperty("started_at")
    private Instant startedAt;

    @JsonProperty("completed_at")
    private Instant completedAt;

    @JsonProperty("order_index")
    private Integer orderIndex;

    @JsonProperty("project_id")
    private Long projectId;

    @JsonProperty("project_name")
    private String projectName;

    @JsonProperty("assigned_agent_id")
    private Long assignedAgentId;

    @JsonProperty("assigned_agent_name")
    private String assignedAgentName;

    @JsonProperty("assigned_squad_id")
    private Long assignedSquadId;

    @JsonProperty("assigned_squad_name")
    private String assignedSquadName;

    @JsonProperty("execution_id")
    private Long executionId;

    @JsonProperty("brain_sentry_session_id")
    private String brainSentrySessionId;

    @JsonProperty("parent_task_id")
    private Long parentTaskId;

    @JsonProperty("subtasks_count")
    private Integer subtasksCount;

    @JsonProperty("created_by_id")
    private Long createdById;

    @JsonProperty("created_by_name")
    private String createdByName;

    private Set<String> tags;

    private Boolean blocked;

    @JsonProperty("blocked_by_ids")
    private List<Long> blockedByIds;

    @JsonProperty("dependent_ids")
    private List<Long> dependentIds;

    @JsonProperty("created_at")
    private Instant createdAt;

    @JsonProperty("updated_at")
    private Instant updatedAt;
}
