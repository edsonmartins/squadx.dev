package dev.squadx.service;

import dev.squadx.dto.common.PageResponse;
import dev.squadx.dto.execution.ExecutionRequest;
import dev.squadx.dto.execution.ExecutionResponse;
import dev.squadx.exception.BadRequestException;
import dev.squadx.exception.ForbiddenException;
import dev.squadx.exception.ResourceNotFoundException;
import dev.squadx.model.*;
import dev.squadx.model.enums.ExecutionStatus;
import dev.squadx.model.enums.TaskStatus;
import dev.squadx.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ExecutionService {

    private final ExecutionRepository executionRepository;
    private final TaskRepository taskRepository;
    private final AgentRepository agentRepository;
    private final OrganizationMemberRepository memberRepository;
    private final WebSocketEventService webSocketEventService;

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

        // Update task status
        task.setStatus(TaskStatus.IN_PROGRESS);
        if (task.getStartedAt() == null) {
            task.setStartedAt(Instant.now());
        }
        taskRepository.save(task);

        ExecutionResponse response = mapToResponse(execution);

        // Notify via WebSocket
        notifyExecutionChange("created", response, task.getProject().getId());

        return response;
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
        }

        execution = executionRepository.save(execution);

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
                if (execution.getStatus() == ExecutionStatus.COMPLETED ||
                    execution.getStatus() == ExecutionStatus.FAILED) {
                    webSocketEventService.sendExecutionCompleted(
                            projectId,
                            execution.getTaskId(),
                            execution.getId(),
                            execution.getStatus().name()
                    );
                }
            }
            case "cancelled" -> webSocketEventService.sendExecutionCompleted(
                    projectId,
                    execution.getTaskId(),
                    execution.getId(),
                    ExecutionStatus.CANCELLED.name()
            );
        }
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
