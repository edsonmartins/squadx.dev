package dev.squadx.controlpanel.dto.change;

import com.fasterxml.jackson.annotation.JsonProperty;
import dev.squadx.controlpanel.model.enums.ChangePhase;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChangeRequest {

    @NotNull(message = "Project ID is required")
    @JsonProperty("project_id")
    private Long projectId;

    private String module;

    private ChangePhase phase;
}

