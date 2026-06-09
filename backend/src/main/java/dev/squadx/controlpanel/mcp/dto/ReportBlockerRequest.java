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
public class ReportBlockerRequest {

    @NotNull(message = "task_id is required")
    @JsonProperty("task_id")
    private Long taskId;

    @NotBlank(message = "reason is required")
    private String reason;
}
