package dev.squadx.service;

import dev.squadx.dto.common.PageResponse;
import dev.squadx.dto.execution.ExecutionRequest;
import dev.squadx.dto.execution.ExecutionResponse;
import dev.squadx.event.ExecutionCompletedEvent;
import dev.squadx.exception.BadRequestException;
import dev.squadx.exception.ForbiddenException;
import dev.squadx.exception.ResourceNotFoundException;
import dev.squadx.integration.BrainSentryClient;
import dev.squadx.model.*;
import dev.squadx.model.enums.ExecutionStatus;
import dev.squadx.model.enums.TaskStatus;
import dev.squadx.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
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

    @Transactional
    public ExecutionResponse startExecution(ExecutionRequest request, User currentUser) {
        Task task = taskRepository.findById(request.getTaskId())
                .orElseThrow(() -> new ResourceNotFoundException("Task not found"));

        validateUserAccess(task.getProject().getOrganization().getId(), currentUser.getId());

        // Check if task already has a running execution
        List<Execution> runningExecutions = executionRepository.findByTaskIdAndStatus(
                task.getId(), ExecutionStatus.RUNNING);
        if (!runningExecutions.isEmpty()) {
            throw new BadRequestException("Task already has a running execution");
        }

        // Get agent
        Agent agent;
        if (request.getAgentId() != null) {
            agent = agentRepository.findById(request.getAgentId())
                    .orElseThrow(() -> new ResourceNotFoundException("Agent not found"));
        } else if (task.getAssignedAgent() != null) {
            agent = task.getAssignedAgent();
        } else if (task.getAssignedSquad() != null) {
            // Squad leader-delegation: resolve the squad's leader (or an online member).
            agent = squadAgentResolver.resolve(task.getAssignedSquad());
            if (agent == null) {
                throw new BadRequestException("No agent available in the assigned squad");
            }
        } else {
            throw new BadRequestException("No agent specified for execution");
        }

        // Create execution
        Execution execution = Execution.builder()
                .task(task)
                .agent(agent)
                .status(ExecutionStatus.PENDING)
                .build();

        execution = executionRepository.save(execution);

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

        // Notify via WebSocket
        dispatchTaskAssignment(task, execution, currentUser);
        notifyExecutionChange("created", response, task.getProject().getId());

        return response;
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
        Execution execution = executionRepository.findById(executionId)
                .orElseThrow(() -> new ResourceNotFoundException("Execution not found"));

        ExecutionLog log = ExecutionLog.builder()
                .execution(execution)
                .level(level)
                .message(message)
                .metadata(metadata)
                .build();

        execution.getLogs().add(log);
        executionRepository.save(execution);

        // Notify via WebSocket
        webSocketEventService.sendExecutionLog(executionId, level, message);
    }

    /**
     * Pending executions the client can claim by polling — a resilience fallback for
     * when the STOMP push was missed (NAT/firewall, reconnect gaps). Returns the same
     * payload shape as the {@code task_assigned} push, scoped to the user's orgs.
     */
    @Transactional(readOnly = true)
    public List<Map<String, Object>> getPendingAssignments(User currentUser) {
        return executionRepository.findTop100ByStatusOrderByCreatedAtAsc(ExecutionStatus.PENDING).stream()
                .filter(e -> memberRepository.existsByOrganizationIdAndUserId(
                        e.getTask().getProject().getOrganization().getId(), currentUser.getId()))
                .map(e -> {
                    Map<String, Object> item = new HashMap<>();
                    item.put("task_id", e.getTask().getId());
                    item.put("task", buildTaskAssignmentPayload(e.getTask(), e));
                    return item;
                })
                .collect(Collectors.toList());
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
        publishExecutionCompletedEventIfNeeded(execution);
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
                                .message(log.getMessage())
                                .metadata(log.getMetadata())
                                .createdAt(log.getCreatedAt())
                                .build())
                        .collect(Collectors.toList()) : null)
                .createdAt(execution.getCreatedAt())
                .build();
    }
}
