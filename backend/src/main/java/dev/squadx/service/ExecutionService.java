package dev.squadx.service;

import dev.squadx.dto.common.PageResponse;
import dev.squadx.dto.execution.ExecutionRequest;
import dev.squadx.dto.execution.ExecutionResponse;
import dev.squadx.dto.execution.FollowUpResponse;
import dev.squadx.event.ExecutionCompletedEvent;
import dev.squadx.exception.BadRequestException;
import dev.squadx.exception.ForbiddenException;
import dev.squadx.exception.ResourceNotFoundException;
import dev.squadx.integration.BrainSentryClient;
import dev.squadx.model.*;
import dev.squadx.model.enums.ExecutionStatus;
import dev.squadx.model.enums.FollowUpStatus;
import dev.squadx.model.enums.TaskStatus;
import dev.squadx.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class ExecutionService {

    private final ExecutionRepository executionRepository;
    private final TaskRepository taskRepository;
    private final AgentRepository agentRepository;
    private final OrganizationMemberRepository memberRepository;
    private final WebSocketEventService webSocketEventService;
    private final BrainSentryClient brainSentryClient;
    private final ApplicationEventPublisher eventPublisher;
    private final SquadAgentResolver squadAgentResolver;
    private final RunAdmissionService runAdmissionService;
    private final FollowUpRequestRepository followUpRequestRepository;
    private final ApprovalService approvalService;

    @Transactional
    public ExecutionResponse startExecution(ExecutionRequest request, User currentUser) {
        Task task = taskRepository.findById(request.getTaskId())
                .orElseThrow(() -> new ResourceNotFoundException("Task not found"));

        validateUserAccess(task.getProject().getOrganization().getId(), currentUser.getId());

        // Admission seam (RFC-0005 §2): dedup duplicate triggers and queue follow-ups when a run
        // is already active, instead of racing or hard-failing.
        RunAdmissionService.AdmissionResult admission = runAdmissionService.admit(task, request, currentUser);
        switch (admission.action()) {
            case DROP_DUPLICATE -> {
                ExecutionResponse response = mapToResponse(admission.referencedExecution());
                response.setAdmission(admission.decision());
                return response;
            }
            case QUEUE_FOLLOW_UP -> {
                Execution active = admission.referencedExecution();
                appendAuditLog(active, "follow_up_request.queued", admission.decision().getReason());
                ExecutionResponse response = mapToResponse(active);
                response.setAdmission(admission.decision());
                return response;
            }
            default -> { /* START — fall through to create a run */ }
        }

        Agent agent = resolveAgent(task, request.getAgentId());

        // Create execution
        Execution execution = Execution.builder()
                .task(task)
                .agent(agent)
                .status(ExecutionStatus.PENDING)
                .idempotencyKey(admission.decision().getIdempotencyKey())
                .build();

        execution = executionRepository.save(execution);
        appendAuditLog(execution, "admission.decided", admission.decision().getReason());

        if (brainSentryClient.isEnabled()) {
            String sessionId = tryStartBrainSentrySession(task, execution, agent);
            if (sessionId != null && !sessionId.isBlank()) {
                execution.setBrainSentrySessionId(sessionId);
                execution = executionRepository.save(execution);
            }
        }

        // Update task status
        task.setStatus(TaskStatus.IN_PROGRESS);
        if (task.getStartedAt() == null) {
            task.setStartedAt(Instant.now());
        }
        taskRepository.save(task);

        ExecutionResponse response = mapToResponse(execution);
        response.setAdmission(admission.decision());

        // Notify via WebSocket
        dispatchTaskAssignment(task, execution, currentUser);
        notifyExecutionChange("created", response, task.getProject().getId());

        return response;
    }

    private Agent resolveAgent(Task task, Long requestedAgentId) {
        if (requestedAgentId != null) {
            return agentRepository.findById(requestedAgentId)
                    .orElseThrow(() -> new ResourceNotFoundException("Agent not found"));
        }
        if (task.getAssignedAgent() != null) {
            return task.getAssignedAgent();
        }
        if (task.getAssignedSquad() != null) {
            // Squad leader-delegation: resolve the squad's leader (or an online member).
            Agent agent = squadAgentResolver.resolve(task.getAssignedSquad());
            if (agent == null) {
                throw new BadRequestException("No agent available in the assigned squad");
            }
            return agent;
        }
        throw new BadRequestException("No agent specified for execution");
    }

    @Transactional
    public void handleDaemonTaskUpdate(Long taskId, Map<String, Object> payload) {
        Execution execution = executionRepository.findTopByTaskIdOrderByCreatedAtDesc(taskId)
                .orElseThrow(() -> new ResourceNotFoundException("Execution not found for task"));

        String type = String.valueOf(payload.get("type"));
        switch (type) {
            case "task_status" -> handleTaskStatusUpdate(execution, payload);
            case "task_completed" -> handleTaskCompleted(execution, payload);
            case "task_failed" -> handleTaskFailure(execution, payload, false);
            case "task_rejected" -> handleTaskFailure(execution, payload, true);
            default -> log.debug("Ignoring unsupported daemon task update type {}", type);
        }
    }

    public ExecutionResponse getById(Long id, User currentUser) {
        Execution execution = executionRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Execution not found"));

        validateUserAccess(execution.getTask().getProject().getOrganization().getId(), currentUser.getId());

        return mapToResponse(execution);
    }

    public PageResponse<ExecutionResponse> getByTaskId(Long taskId, Pageable pageable, User currentUser) {
        Task task = taskRepository.findById(taskId)
                .orElseThrow(() -> new ResourceNotFoundException("Task not found"));

        validateUserAccess(task.getProject().getOrganization().getId(), currentUser.getId());

        Page<ExecutionResponse> page = executionRepository.findByTaskId(taskId, pageable)
                .map(this::mapToResponse);

        return PageResponse.from(page);
    }

    public PageResponse<ExecutionResponse> getByProjectId(Long projectId, Pageable pageable, User currentUser) {
        Page<ExecutionResponse> page = executionRepository.findByProjectId(projectId, pageable)
                .map(this::mapToResponse);

        return PageResponse.from(page);
    }

    public PageResponse<ExecutionResponse> getByOrganizationId(Long organizationId, Pageable pageable, User currentUser) {
        validateUserAccess(organizationId, currentUser.getId());

        Page<ExecutionResponse> page = executionRepository.findByOrganizationId(organizationId, pageable)
                .map(this::mapToResponse);

        return PageResponse.from(page);
    }

    @Transactional
    public ExecutionResponse updateStatus(Long id, ExecutionStatus status, User currentUser) {
        Execution execution = executionRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Execution not found"));

        validateUserAccess(execution.getTask().getProject().getOrganization().getId(), currentUser.getId());

        execution.setStatus(status);

        if (status == ExecutionStatus.RUNNING && execution.getStartedAt() == null) {
            execution.setStartedAt(Instant.now());
        }

        if (status == ExecutionStatus.COMPLETED || status == ExecutionStatus.FAILED) {
            execution.setCompletedAt(Instant.now());

            // Update task status based on execution result
            Task task = execution.getTask();
            if (status == ExecutionStatus.COMPLETED) {
                task.setStatus(TaskStatus.IN_REVIEW);
            } else {
                task.setStatus(TaskStatus.BLOCKED);
            }
            taskRepository.save(task);

            tryCompleteBrainSentrySession(
                    execution,
                    status.name(),
                    execution.getResult() != null ? execution.getResult() : execution.getErrorMessage()
            );
        }

        execution = executionRepository.save(execution);
        publishExecutionCompletedEventIfNeeded(execution);

        ExecutionResponse response = mapToResponse(execution);
        notifyExecutionChange("status_changed", response, execution.getTask().getProject().getId());

        return response;
    }

    @Transactional
    public ExecutionResponse cancel(Long id, User currentUser) {
        Execution execution = executionRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Execution not found"));

        validateUserAccess(execution.getTask().getProject().getOrganization().getId(), currentUser.getId());

        if (execution.getStatus() == ExecutionStatus.COMPLETED ||
            execution.getStatus() == ExecutionStatus.FAILED ||
            execution.getStatus() == ExecutionStatus.CANCELLED) {
            throw new BadRequestException("Cannot cancel a finished execution");
        }

        execution.setStatus(ExecutionStatus.CANCELLED);
        execution.setCompletedAt(Instant.now());
        tryCompleteBrainSentrySession(
                execution,
                ExecutionStatus.CANCELLED.name(),
                execution.getErrorMessage()
        );
        execution = executionRepository.save(execution);

        ExecutionResponse response = mapToResponse(execution);
        notifyExecutionChange("cancelled", response, execution.getTask().getProject().getId());

        return response;
    }

    @Transactional
    public void addLog(Long executionId, String level, String message, String metadata) {
        addLog(executionId, null, level, null, null, message, metadata);
    }

    /**
     * Append an execution log/event with Attention Budget classification (RFC-0005 §1). Explicit
     * {@code visibility}/{@code importance} (sent by the client) win; otherwise they are derived
     * from {@code eventType} or the log {@code level}.
     */
    @Transactional
    public void addLog(Long executionId, String eventType, String level, String visibility,
                       String importance, String message, String metadata) {
        Execution execution = executionRepository.findById(executionId)
                .orElseThrow(() -> new ResourceNotFoundException("Execution not found"));

        RunEventMetadata.Metadata md = RunEventMetadata.resolve(visibility, importance, eventType, level);
        ExecutionLog log = ExecutionLog.builder()
                .execution(execution)
                .level(level != null ? level : "INFO")
                .visibility(md.visibility())
                .importance(md.importance())
                .message(message)
                .metadata(metadata)
                .build();

        execution.getLogs().add(log);
        executionRepository.save(execution);

        // Notify via WebSocket
        webSocketEventService.sendExecutionLog(executionId, log.getLevel(), md.visibility(), md.importance(), message);
    }

    /** Append an audit-only timeline event to an already-loaded execution (admission, promotion). */
    private void appendAuditLog(Execution execution, String eventType, String message) {
        RunEventMetadata.Metadata md = RunEventMetadata.resolve(null, null, eventType, "INFO");
        ExecutionLog log = ExecutionLog.builder()
                .execution(execution)
                .level("INFO")
                .visibility(md.visibility())
                .importance(md.importance())
                .message(message)
                .metadata("{\"event\":\"" + eventType + "\"}")
                .build();
        execution.getLogs().add(log);
        executionRepository.save(execution);
        webSocketEventService.sendExecutionLog(execution.getId(), "INFO", md.visibility(), md.importance(), message);
    }

    /**
     * Pending executions the client can claim by polling — a resilience fallback for
     * when the STOMP push was missed (NAT/firewall, reconnect gaps). Returns the same
     * payload shape as the {@code task_assigned} push, scoped to the user's orgs.
     */
    @Transactional(readOnly = true)
    public List<Map<String, Object>> getPendingAssignments(User currentUser) {
        return executionRepository
                .findPendingForUser(ExecutionStatus.PENDING, currentUser.getId(), PageRequest.of(0, 100))
                .stream()
                .map(e -> {
                    Map<String, Object> item = new HashMap<>();
                    item.put("task_id", e.getTask().getId());
                    item.put("task", buildTaskAssignmentPayload(e.getTask(), e));
                    return item;
                })
                .collect(Collectors.toList());
    }

    /**
     * Atomically claim a pending execution so only one polling client runs it.
     * Returns true if this caller won the claim (PENDING -> RUNNING).
     */
    @Transactional
    public boolean claimPending(Long executionId, User currentUser) {
        Execution execution = executionRepository.findById(executionId)
                .orElseThrow(() -> new ResourceNotFoundException("Execution not found"));
        validateUserAccess(execution.getTask().getProject().getOrganization().getId(), currentUser.getId());
        return executionRepository.claimExecution(
                executionId, ExecutionStatus.RUNNING, ExecutionStatus.PENDING, Instant.now()) > 0;
    }

    public Map<String, Object> getOrganizationMetrics(Long organizationId, User currentUser) {
        validateUserAccess(organizationId, currentUser.getId());

        Long totalInputTokens = executionRepository.sumInputTokensByOrganizationId(organizationId);
        Long totalOutputTokens = executionRepository.sumOutputTokensByOrganizationId(organizationId);
        Double totalCost = executionRepository.sumTotalCostByOrganizationId(organizationId);

        return Map.of(
                "total_input_tokens", totalInputTokens != null ? totalInputTokens : 0,
                "total_output_tokens", totalOutputTokens != null ? totalOutputTokens : 0,
                "total_cost", totalCost != null ? totalCost : 0.0
        );
    }

    private void validateUserAccess(Long organizationId, Long userId) {
        if (!memberRepository.existsByOrganizationIdAndUserId(organizationId, userId)) {
            throw new ForbiddenException("User does not have access to this organization");
        }
    }

    private void notifyExecutionChange(String action, ExecutionResponse execution, Long projectId) {
        switch (action) {
            case "created" -> webSocketEventService.sendExecutionStarted(projectId, execution.getTaskId(), execution.getId());
            case "status_changed" -> {
                // Completion notifications are emitted via ExecutionCompletedEvent listeners.
            }
            case "cancelled" -> webSocketEventService.sendExecutionCompleted(
                    projectId,
                    execution.getTaskId(),
                    execution.getId(),
                    ExecutionStatus.CANCELLED.name()
            );
        }
    }

    private String tryStartBrainSentrySession(Task task, Execution execution, Agent agent) {
        try {
            return brainSentryClient.startExecutionSession(
                    task.getProject().getOrganization().getId(),
                    execution.getId(),
                    task.getId(),
                    task.getProject().getId(),
                    agent.getId()
            );
        } catch (Exception e) {
            log.warn("BrainSentry start failed for execution {}: {}", execution.getId(), e.getMessage());
            return null;
        }
    }

    private void tryCompleteBrainSentrySession(Execution execution, String status, String summary) {
        try {
            brainSentryClient.completeExecutionSession(
                    execution.getTask().getProject().getOrganization().getId(),
                    execution.getBrainSentrySessionId(),
                    execution.getId(),
                    execution.getTask().getId(),
                    execution.getTask().getProject().getId(),
                    execution.getAgent() != null ? execution.getAgent().getId() : null,
                    status,
                    summary
            );
        } catch (Exception e) {
            log.warn("BrainSentry completion failed for execution {}: {}", execution.getId(), e.getMessage());
        }
    }

    private void dispatchTaskAssignment(Task task, Execution execution, User currentUser) {
        if (currentUser == null || currentUser.getEmail() == null || currentUser.getEmail().isBlank()) {
            return;
        }

        webSocketEventService.sendTaskAssignedToUser(
                currentUser.getEmail(),
                task.getId(),
                buildTaskAssignmentPayload(task, execution)
        );
    }

    private Map<String, Object> buildTaskAssignmentPayload(Task task, Execution execution) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("id", task.getId());
        payload.put("task_id", task.getId());
        payload.put("title", task.getTitle());
        payload.put("description", task.getDescription() != null && !task.getDescription().isBlank()
                ? task.getDescription()
                : task.getTitle());
        payload.put("status", task.getStatus().name());
        payload.put("priority", task.getPriority() != null ? task.getPriority().name() : null);
        payload.put("project_id", task.getProject().getId());
        payload.put("project_name", task.getProject().getName());
        payload.put("assigned_agent_id", task.getAssignedAgent() != null ? task.getAssignedAgent().getId() : null);
        payload.put("assigned_agent_name", task.getAssignedAgent() != null ? task.getAssignedAgent().getName() : null);
        Agent execAgent = execution.getAgent();
        payload.put("agent_id", execAgent != null ? execAgent.getId() : null);
        payload.put("agent_name", execAgent != null ? execAgent.getName() : null);
        payload.put("agent_type", execAgent != null && execAgent.getAgentType() != null
                ? execAgent.getAgentType().name() : null);
        payload.put("runtime_kind", execAgent != null && execAgent.getRuntimeKind() != null
                ? execAgent.getRuntimeKind().name() : "NATIVE");
        payload.put("cli_provider", execAgent != null && execAgent.getCliProvider() != null
                ? execAgent.getCliProvider().name() : null);
        payload.put("execution_id", execution.getId());
        payload.put("brain_sentry_session_id", execution.getBrainSentrySessionId());
        payload.put("tags", task.getTags());
        return payload;
    }

    private void handleTaskStatusUpdate(Execution execution, Map<String, Object> payload) {
        String status = String.valueOf(payload.get("status"));
        if (!"running".equalsIgnoreCase(status)) {
            return;
        }

        if (execution.getStatus() == ExecutionStatus.PENDING) {
            execution.setStatus(ExecutionStatus.RUNNING);
        }
        if (execution.getStartedAt() == null) {
            execution.setStartedAt(Instant.now());
        }
        executionRepository.save(execution);
    }

    @SuppressWarnings("unchecked")
    private void handleTaskCompleted(Execution execution, Map<String, Object> payload) {
        Map<String, Object> result = payload.get("result") instanceof Map<?, ?> map
                ? (Map<String, Object>) map
                : Map.of();

        execution.setStatus(ExecutionStatus.COMPLETED);
        if (execution.getStartedAt() == null) {
            execution.setStartedAt(Instant.now());
        }
        execution.setCompletedAt(Instant.now());
        execution.setResult(stringValue(result.get("final_result")));
        execution.setGitBranch(stringValue(result.get("git_branch")));
        execution.setGitCommit(stringValue(result.get("git_commit")));
        execution.setInputTokens(longValue(result.get("total_input_tokens")));
        execution.setOutputTokens(longValue(result.get("total_output_tokens")));
        execution.setTotalCost(doubleValue(result.get("total_cost")));

        Task task = execution.getTask();
        task.setStatus(TaskStatus.IN_REVIEW);
        taskRepository.save(task);

        executionRepository.save(execution);

        // Opt-in human-approval gate: a completed run on a task that requires approval parks at
        // IN_REVIEW with a PENDING approval; it only reaches DONE via ApprovalService.review.
        if (Boolean.TRUE.equals(task.getRequiresApproval())) {
            approvalService.autoCreateForCompletedExecution(task, execution);
        }

        publishExecutionCompletedEventIfNeeded(execution);
        promoteNextFollowUp(task);
    }

    private void handleTaskFailure(Execution execution, Map<String, Object> payload, boolean rejected) {
        execution.setStatus(ExecutionStatus.FAILED);
        if (execution.getStartedAt() == null) {
            execution.setStartedAt(Instant.now());
        }
        execution.setCompletedAt(Instant.now());
        execution.setErrorMessage(stringValue(payload.get(rejected ? "reason" : "error")));

        Task task = execution.getTask();
        task.setStatus(rejected ? TaskStatus.TODO : TaskStatus.BLOCKED);
        taskRepository.save(task);

        executionRepository.save(execution);
        publishExecutionCompletedEventIfNeeded(execution);
        promoteNextFollowUp(task);
    }

    private String stringValue(Object value) {
        return value != null ? String.valueOf(value) : null;
    }

    private Long longValue(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        if (value instanceof String text && !text.isBlank()) {
            return Long.valueOf(text);
        }
        return null;
    }

    private Double doubleValue(Object value) {
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        if (value instanceof String text && !text.isBlank()) {
            return Double.valueOf(text);
        }
        return null;
    }

    private void publishExecutionCompletedEventIfNeeded(Execution execution) {
        if (execution.getStatus() != ExecutionStatus.COMPLETED &&
            execution.getStatus() != ExecutionStatus.FAILED) {
            return;
        }

        eventPublisher.publishEvent(new ExecutionCompletedEvent(
                execution.getId(),
                execution.getTask().getId(),
                execution.getTask().getProject().getId(),
                execution.getTask().getProject().getOrganization().getId(),
                execution.getStatus()
        ));
    }

    /**
     * Promote the oldest pending follow-up for a task into a new PENDING execution once the active
     * run terminates (RFC-0005 §2.3). The new run is picked up by the client's polling/claim path.
     */
    private void promoteNextFollowUp(Task task) {
        followUpRequestRepository
                .findFirstByTaskIdAndStatusOrderByCreatedAtAsc(task.getId(), FollowUpStatus.PENDING)
                .ifPresent(followUp -> {
                    try {
                        Agent agent = resolveAgent(task, followUp.getRequestedAgentId());
                        Execution promoted = executionRepository.save(Execution.builder()
                                .task(task)
                                .agent(agent)
                                .status(ExecutionStatus.PENDING)
                                .build());
                        appendAuditLog(promoted, "run.created",
                                "Promoted from follow-up request #" + followUp.getId());
                        followUp.setStatus(FollowUpStatus.PROMOTED);
                        followUpRequestRepository.save(followUp);
                        log.info("Promoted follow-up {} to execution {} for task {}",
                                followUp.getId(), promoted.getId(), task.getId());
                    } catch (RuntimeException e) {
                        followUp.setStatus(FollowUpStatus.CANCELLED);
                        followUpRequestRepository.save(followUp);
                        log.warn("Cancelled follow-up {} for task {}: {}",
                                followUp.getId(), task.getId(), e.getMessage());
                    }
                });
    }

    /** Pending follow-up requests queued behind the active run for a task (RFC-0005 §2.3). */
    @Transactional(readOnly = true)
    public PageResponse<FollowUpResponse> getPendingFollowUps(Long taskId, Pageable pageable, User currentUser) {
        Task task = taskRepository.findById(taskId)
                .orElseThrow(() -> new ResourceNotFoundException("Task not found"));
        validateUserAccess(task.getProject().getOrganization().getId(), currentUser.getId());

        Page<FollowUpResponse> page = followUpRequestRepository
                .findByTaskIdAndStatus(taskId, FollowUpStatus.PENDING, pageable)
                .map(this::mapFollowUp);
        return PageResponse.from(page);
    }

    private FollowUpResponse mapFollowUp(FollowUpRequest followUp) {
        return FollowUpResponse.builder()
                .id(followUp.getId())
                .taskId(followUp.getTask().getId())
                .activeExecutionId(followUp.getActiveExecutionId())
                .requestedAgentId(followUp.getRequestedAgentId())
                .status(followUp.getStatus())
                .createdAt(followUp.getCreatedAt())
                .build();
    }

    private ExecutionResponse mapToResponse(Execution execution) {
        Long durationSeconds = null;
        if (execution.getStartedAt() != null && execution.getCompletedAt() != null) {
            durationSeconds = Duration.between(execution.getStartedAt(), execution.getCompletedAt()).getSeconds();
        }

        return ExecutionResponse.builder()
                .id(execution.getId())
                .taskId(execution.getTask().getId())
                .taskTitle(execution.getTask().getTitle())
                .agentId(execution.getAgent().getId())
                .agentName(execution.getAgent().getName())
                .agentType(execution.getAgent().getAgentType().name())
                .status(execution.getStatus())
                .startedAt(execution.getStartedAt())
                .completedAt(execution.getCompletedAt())
                .durationSeconds(durationSeconds)
                .containerId(execution.getContainerId())
                .brainSentrySessionId(execution.getBrainSentrySessionId())
                .inputTokens(execution.getInputTokens())
                .outputTokens(execution.getOutputTokens())
                .totalCost(execution.getTotalCost())
                .result(execution.getResult())
                .errorMessage(execution.getErrorMessage())
                .gitBranch(execution.getGitBranch())
                .gitCommit(execution.getGitCommit())
                .logs(execution.getLogs() != null ? execution.getLogs().stream()
                        .map(log -> ExecutionResponse.LogEntry.builder()
                                .id(log.getId())
                                .level(log.getLevel())
                                .visibility(log.getVisibility())
                                .importance(log.getImportance())
                                .message(log.getMessage())
                                .metadata(log.getMetadata())
                                .createdAt(log.getCreatedAt())
                                .build())
                        .collect(Collectors.toList()) : null)
                .createdAt(execution.getCreatedAt())
                .build();
    }
}
