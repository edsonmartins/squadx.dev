package dev.squadx.controlpanel.mcp.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import dev.squadx.controlpanel.model.enums.SpecTaskStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateTaskStatusResponse {

    private boolean ok;

    @JsonProperty("task_id")
    private Long taskId;

    /** Status projetado resultante (RFC-0001 §4.3). */
    private SpecTaskStatus status;
}
