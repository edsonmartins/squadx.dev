package dev.squadx.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.squadx.dto.execution.ExecutionRequest;
import dev.squadx.dto.execution.RunAdmissionDecision;
import dev.squadx.model.Execution;
import dev.squadx.model.FollowUpRequest;
import dev.squadx.model.Task;
import dev.squadx.model.User;
import dev.squadx.model.enums.ExecutionStatus;
import dev.squadx.model.enums.FollowUpStatus;
import dev.squadx.model.enums.RunAdmissionAction;
import dev.squadx.model.enums.RunAdmissionReasonCode;
import dev.squadx.repository.ExecutionRepository;
import dev.squadx.repository.FollowUpRequestRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Admission seam for execution requests (RFC-0005 §2). Decides — before a run is created — whether
 * to start, drop a duplicate (idempotent replay), or queue a durable follow-up because a run is
 * already active for the same task. Keeps dedup/follow-up out of the route handler and makes the
 * decision independently testable.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class RunAdmissionService {

    private static final List<ExecutionStatus> ACTIVE_STATUSES =
            List.of(ExecutionStatus.PENDING, ExecutionStatus.RUNNING);

    private final ExecutionRepository executionRepository;
    private final FollowUpRequestRepository followUpRequestRepository;
    private final ObjectMapper objectMapper;

    /** Outcome of an admission decision plus the run/follow-up it references, when applicable. */
    public record AdmissionResult(
            RunAdmissionDecision decision,
            Execution referencedExecution,
            FollowUpRequest followUp
    ) {
        public RunAdmissionAction action() {
            return decision.getAction();
        }
    }

    /**
     * Decide what to do with an incoming execution request for an already-access-checked task.
     * The caller (ExecutionService) resolves the agent and creates the run only for {@code START}.
     */
    public AdmissionResult admit(Task task, ExecutionRequest request, User currentUser) {
        String key = trimToNull(request.getIdempotencyKey());

        // 1. Idempotent replay: same key already produced a run for this task.
        if (key != null) {
            Optional<Execution> existing = executionRepository.findByTaskIdAndIdempotencyKey(task.getId(), key);
            if (existing.isPresent()) {
                Execution run = existing.get();
                RunAdmissionDecision decision = decision(
                        RunAdmissionAction.DROP_DUPLICATE,
                        "Source event already created a run.",
                        RunAdmissionReasonCode.DUPLICATE_SOURCE_EVENT,
                        run.getId(), key, null);
                return new AdmissionResult(decision, run, null);
            }
        }

        // 2. A run is already active for this thread → queue durable follow-up work.
        List<Execution> active = executionRepository.findByTaskIdAndStatusIn(task.getId(), ACTIVE_STATUSES);
        if (!active.isEmpty()) {
            Execution activeRun = active.get(0);
            RunAdmissionDecision decision = decision(
                    RunAdmissionAction.QUEUE_FOLLOW_UP,
                    "A run is already active for this task; queued as follow-up work.",
                    RunAdmissionReasonCode.ACTIVE_RUN_SAME_TASK,
                    activeRun.getId(), key, null);

            FollowUpRequest followUp = FollowUpRequest.builder()
                    .task(task)
                    .organizationId(task.getProject().getOrganization().getId())
                    .activeExecutionId(activeRun.getId())
                    .requestedAgentId(request.getAgentId())
                    .requestedByEmail(currentUser != null ? currentUser.getEmail() : null)
                    .sourcePayload(toJson(Map.of(
                            "task_id", request.getTaskId(),
                            "agent_id", String.valueOf(request.getAgentId()),
                            "idempotency_key", String.valueOf(key))))
                    .decision(toJson(decision))
                    .status(FollowUpStatus.PENDING)
                    .build();
            followUp = followUpRequestRepository.save(followUp);
            decision.setFollowUpRequestId(followUp.getId());

            return new AdmissionResult(decision, activeRun, followUp);
        }

        // 3. Clear to start a new run.
        RunAdmissionDecision decision = decision(
                RunAdmissionAction.START,
                "Source event accepted and ready to create a run.",
                RunAdmissionReasonCode.NEW_EVENT,
                null, key, null);
        return new AdmissionResult(decision, null, null);
    }

    private RunAdmissionDecision decision(RunAdmissionAction action, String reason,
                                          RunAdmissionReasonCode code, Long activeExecutionId,
                                          String key, Long followUpId) {
        return RunAdmissionDecision.builder()
                .action(action)
                .reason(reason)
                .reasonCode(code)
                .decidedAt(Instant.now())
                .activeExecutionId(activeExecutionId)
                .idempotencyKey(key)
                .followUpRequestId(followUpId)
                .build();
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            log.warn("Failed to serialize admission metadata: {}", e.getMessage());
            return null;
        }
    }

    private static String trimToNull(String value) {
        if (value == null) return null;
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
