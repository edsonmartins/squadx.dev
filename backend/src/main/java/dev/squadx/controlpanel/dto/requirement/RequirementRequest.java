package dev.squadx.controlpanel.dto.requirement;

import com.fasterxml.jackson.annotation.JsonProperty;
import dev.squadx.controlpanel.model.enums.RequirementType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RequirementRequest {

    @NotNull(message = "Change ID is required")
    @JsonProperty("change_id")
    private Long changeId;

    /** Referência estável dentro da mudança (ex.: "R1"); gerada se ausente. */
    @JsonProperty("requirement_id")
    private String requirementId;

    @NotNull(message = "Type is required")
    private RequirementType type;

    @NotBlank(message = "Title is required")
    private String title;

    private String description;

    /** Cenários de aceite; o serviço exige ao menos um (work-model R2). */
    @Valid
    private List<ScenarioRequest> scenarios;
}

