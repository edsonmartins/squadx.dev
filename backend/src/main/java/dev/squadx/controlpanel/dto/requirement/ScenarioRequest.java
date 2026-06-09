package dev.squadx.controlpanel.dto.requirement;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ScenarioRequest {

    @NotBlank(message = "Scenario name is required")
    private String name;

    @NotBlank(message = "WHEN is required")
    @JsonProperty("when")
    private String when;

    @NotBlank(message = "THEN is required")
    @JsonProperty("then")
    private String then;
}
