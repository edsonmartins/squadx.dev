package dev.squadx.dto.approval;

import com.fasterxml.jackson.annotation.JsonProperty;
import dev.squadx.model.enums.ApprovalType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CreateApprovalRequest {

    @NotNull
    @JsonProperty("task_id")
    private Long taskId;

    @JsonProperty("execution_id")
    private Long executionId;

    @JsonProperty("approval_type")
    private ApprovalType approvalType;

    @NotBlank
    private String title;

    private String description;

    @JsonProperty("changes_summary")
    private String changesSummary;
}
