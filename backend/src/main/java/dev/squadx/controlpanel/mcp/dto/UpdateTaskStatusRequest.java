package dev.squadx.controlpanel.mcp.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
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
public class UpdateTaskStatusRequest {

    @NotNull(message = "task_id is required")
    @JsonProperty("task_id")
    private Long taskId;

    /** Apenas "em_curso" ou "implementado" (RFC-0001 §4.3). */
    @NotBlank(message = "status is required")
    private String status;

    private String note;
}
