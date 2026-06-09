package dev.squadx.controlpanel.dto.spectask;

import com.fasterxml.jackson.annotation.JsonProperty;
import dev.squadx.controlpanel.model.enums.AssigneeType;
import dev.squadx.controlpanel.model.enums.Pass5Result;
import dev.squadx.controlpanel.model.enums.SpecTaskStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SpecTaskResponse {

    private Long id;
    private String title;
    private SpecTaskStatus status;

    @JsonProperty("change_id")
    private Long changeId;

    @JsonProperty("requirement_id")
    private Long requirementId;

    /** Referência estável do requisito de origem (ex.: "R1"). */
    @JsonProperty("requirement_ref")
    private String requirementRef;

    @JsonProperty("assignee_type")
    private AssigneeType assigneeType;

    @JsonProperty("assigned_user_id")
    private Long assignedUserId;

    @JsonProperty("assigned_user_name")
    private String assignedUserName;

    @JsonProperty("assigned_agent_id")
    private Long assignedAgentId;

    @JsonProperty("assigned_agent_name")
    private String assignedAgentName;

    private Pass5Result pass5;

    @JsonProperty("blocker_reason")
    private String blockerReason;

    @JsonProperty("revise_reason")
    private String reviseReason;

    @JsonProperty("created_at")
    private Instant createdAt;

    @JsonProperty("updated_at")
    private Instant updatedAt;
}
