package dev.squadx.dto.template;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ApplyTemplateRequest {

    @JsonProperty("project_id")
    private Long projectId;

    @NotNull(message = "Organization ID is required")
    @JsonProperty("organization_id")
    private Long organizationId;

    @JsonProperty("squad_name")
    private String squadName;

    private String goal;
}
