package dev.squadx.controlpanel.dto.requirement;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ScenarioResponse {

    private Long id;
    private String name;

    @JsonProperty("when")
    private String when;

    @JsonProperty("then")
    private String then;

    private boolean covered;
}

