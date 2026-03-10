package dev.squadx.dto.approval;

import com.fasterxml.jackson.annotation.JsonProperty;
import dev.squadx.model.enums.ApprovalStatus;
import dev.squadx.model.enums.ApprovalType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ApprovalResponse {

    private Long id;

    @JsonProperty("task_id")
    private Long taskId;

    @JsonProperty("task_title")
    private String taskTitle;

    @JsonProperty("execution_id")
    private Long executionId;

    @JsonProperty("requested_by_id")
    private Long requestedById;

    @JsonProperty("requested_by_name")
    private String requestedByName;

    @JsonProperty("reviewer_id")
    private Long reviewerId;

    @JsonProperty("reviewer_name")
    private String reviewerName;

    private ApprovalStatus status;

    @JsonProperty("approval_type")
    private ApprovalType approvalType;

    private String title;
    private String description;

    @JsonProperty("changes_summary")
    private String changesSummary;

    @JsonProperty("review_comment")
    private String reviewComment;

    @JsonProperty("reviewed_at")
    private Instant reviewedAt;

    @JsonProperty("expires_at")
    private Instant expiresAt;

    @JsonProperty("created_at")
    private Instant createdAt;
}
