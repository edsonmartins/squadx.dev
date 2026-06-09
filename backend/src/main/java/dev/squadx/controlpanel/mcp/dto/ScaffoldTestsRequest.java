package dev.squadx.controlpanel.mcp.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ScaffoldTestsRequest {

    /** Opcional: restringe a um requisito; ausente → todos os requisitos da mudança. */
    @JsonProperty("requirement_id")
    private Long requirementId;
}
