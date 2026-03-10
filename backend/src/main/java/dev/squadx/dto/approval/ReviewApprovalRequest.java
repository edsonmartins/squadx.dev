package dev.squadx.dto.approval;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ReviewApprovalRequest {

    @NotNull
    private Boolean approved;

    @JsonProperty("review_comment")
    private String reviewComment;
}
