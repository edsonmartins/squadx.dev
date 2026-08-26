package dev.squadx.controlpanel.dto.requirement;

import com.fasterxml.jackson.annotation.JsonProperty;
import dev.squadx.controlpanel.model.enums.RequirementType;
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
public class RequirementResponse {

    private Long id;

    @JsonProperty("change_id")
    private Long changeId;

    @JsonProperty("requirement_id")
    private String requirementId;

    private RequirementType type;
    private String title;
    private String description;

    private List<ScenarioResponse> scenarios;

    /** IDs das tarefas que referenciam este requisito (rastreabilidade R3). */
    @JsonProperty("task_refs")
    private List<Long> taskRefs;

    @JsonProperty("created_at")
    private Instant createdAt;

    @JsonProperty("updated_at")
    private Instant updatedAt;
}

