package dev.squadx.dto.execution;

import com.fasterxml.jackson.annotation.JsonProperty;
import dev.squadx.model.enums.RunAdmissionAction;
import dev.squadx.model.enums.RunAdmissionReasonCode;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * The dispatcher's decision about an incoming execution request (RFC-0005 §2). Embedded in the
 * start-execution response so clients can read the outcome (e.g. {@code drop_duplicate},
 * {@code queue_follow_up}) without inferring it from HTTP status.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RunAdmissionDecision {

    private RunAdmissionAction action;

    private String reason;

    @JsonProperty("reason_code")
    private RunAdmissionReasonCode reasonCode;

    @JsonProperty("decided_at")
    private Instant decidedAt;

    @JsonProperty("active_execution_id")
    private Long activeExecutionId;

    @JsonProperty("idempotency_key")
    private String idempotencyKey;

    @JsonProperty("follow_up_request_id")
    private Long followUpRequestId;
}
