package dev.squadx.controlpanel.dto.spectask;

import com.fasterxml.jackson.annotation.JsonProperty;
import dev.squadx.controlpanel.model.enums.AssigneeType;
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
public class SpecTaskRequest {

    @NotNull(message = "Change ID is required")
    @JsonProperty("change_id")
    private Long changeId;

    /** Requisito de origem (rastreabilidade R3). */
    @JsonProperty("requirement_id")
    private Long requirementId;

    @NotBlank(message = "Title is required")
    private String title;

    @JsonProperty("assignee_type")
    private AssigneeType assigneeType;

    @JsonProperty("assigned_user_id")
    private Long assignedUserId;

    @JsonProperty("assigned_agent_id")
    private Long assignedAgentId;
}

