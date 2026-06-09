package dev.squadx.controlpanel.service;

import dev.squadx.controlpanel.dto.spectask.SpecTaskRequest;
import dev.squadx.controlpanel.dto.spectask.SpecTaskResponse;
import dev.squadx.controlpanel.dto.spectask.SpecTaskTransitionRequest;
import dev.squadx.controlpanel.model.Change;
import dev.squadx.controlpanel.model.Requirement;
import dev.squadx.controlpanel.model.SpecTask;
import dev.squadx.controlpanel.model.enums.AssigneeType;
import dev.squadx.controlpanel.model.enums.EventSource;
import dev.squadx.controlpanel.model.enums.Pass5Result;
import dev.squadx.controlpanel.model.enums.SpecTaskStatus;
import dev.squadx.controlpanel.model.enums.TaskEventType;
import dev.squadx.controlpanel.repository.ChangeRepository;
import dev.squadx.controlpanel.repository.RequirementRepository;
import dev.squadx.controlpanel.repository.SpecTaskRepository;
import dev.squadx.exception.BadRequestException;
import dev.squadx.exception.ForbiddenException;
import dev.squadx.exception.ResourceNotFoundException;
import dev.squadx.model.Agent;
import dev.squadx.model.User;
import dev.squadx.repository.AgentRepository;
import dev.squadx.repository.OrganizationMemberRepository;
import dev.squadx.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Tarefas do Control Panel. Realiza work-model R3 (rastreabilidade), R4 (máquina de estados),
 * R5 (CONCLUIDA/AJUSTES só pelo Pass 5) e R6 (responsável humano|agente).
 */
@Service
@RequiredArgsConstructor
public class SpecTaskService {

    private final SpecTaskRepository specTaskRepository;
    private final ChangeRepository changeRepository;
    private final RequirementRepository requirementRepository;
    private final UserRepository userRepository;
    private final AgentRepository agentRepository;
    private final OrganizationMemberRepository memberRepository;
    private final SpecTaskStateMachine stateMachine;
    private final SpecEventService specEventService;

    @Transactional
    public SpecTaskResponse create(SpecTaskRequest request, User currentUser) {
        Change change = changeRepository.findById(request.getChangeId())
                .orElseThrow(() -> new ResourceNotFoundException("Change not found"));
        validateUserAccess(change, currentUser);

        SpecTask.SpecTaskBuilder builder = SpecTask.builder()
                .change(change)
                .title(request.getTitle())
                .status(SpecTaskStatus.A_FAZER)
                .pass5(Pass5Result.PENDING);

        if (request.getRequirementId() != null) {
            Requirement requirement = requirementRepository.findById(request.getRequirementId())
                    .orElseThrow(() -> new ResourceNotFoundException("Requirement not found"));
            builder.requirement(requirement);
        }

        applyAssignee(builder, request);

        SpecTask task = specTaskRepository.save(builder.build());
        return mapToResponse(task);
    }

    @Transactional(readOnly = true)
    public List<SpecTaskResponse> getByChangeId(Long changeId, User currentUser) {
        Change change = changeRepository.findById(changeId)
                .orElseThrow(() -> new ResourceNotFoundException("Change not found"));
        validateUserAccess(change, currentUser);
        return specTaskRepository.findByChangeId(changeId).stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    /**
     * Transição de estado solicitada por dev/agente. Rejeita alvos {@code CONCLUIDA}/{@code AJUSTES}
     * (só o Pass 5 os define — R5) e transições inválidas (R4).
     */
    @Transactional
    public SpecTaskResponse transition(Long taskId, SpecTaskTransitionRequest request, User currentUser) {
        SpecTask task = loadForUser(taskId, currentUser);
        SpecTaskStatus current = task.getStatus();
        SpecTaskStatus target = request.getStatus();

        if (stateMachine.isPass5Only(target)) {
            throw new BadRequestException("Status '" + target + "' can only be set by Pass 5 validation");
        }
        if (!stateMachine.canTransition(current, target)) {
            throw new BadRequestException("Invalid transition: " + current + " -> " + target);
        }
        if (target == SpecTaskStatus.BLOQUEADA && (request.getNote() == null || request.getNote().isBlank())) {
            throw new BadRequestException("A blocker requires a reason");
        }

        // Status is a projection of events (ADR-0002): record the event, let the projector decide.
        TaskEventType type;
        String payload = null;
        if (current == SpecTaskStatus.BLOQUEADA) {
            type = TaskEventType.UNBLOCKED;
        } else if (target == SpecTaskStatus.BLOQUEADA) {
            type = TaskEventType.BLOCKED;
            payload = request.getNote();
        } else if (target == SpecTaskStatus.EM_CURSO) {
            type = TaskEventType.STARTED;
        } else if (target == SpecTaskStatus.EM_VALIDACAO) {
            type = TaskEventType.PR_OPENED;
        } else {
            throw new BadRequestException("Unsupported manual transition to " + target);
        }

        specEventService.record(taskId, type, EventSource.MCP, UUID.randomUUID().toString(),
                payload, Instant.now());
        return mapToResponse(specTaskRepository.findById(taskId)
                .orElseThrow(() -> new ResourceNotFoundException("Task not found")));
    }

    /**
     * Aplica o desfecho do Pass 5 (RFC-0004). Único caminho para {@code CONCLUIDA}/{@code AJUSTES}
     * (R5). Não exposto a dev/agente — chamado pela validação (capability pass5-validation).
     */
    @Transactional
    public SpecTaskResponse applyPass5Outcome(Long taskId, Pass5Result result, String critique) {
        specTaskRepository.findById(taskId)
                .orElseThrow(() -> new ResourceNotFoundException("Task not found"));
        TaskEventType type = switch (result) {
            case PASS -> TaskEventType.PASS5_APPROVED;
            case FAIL -> TaskEventType.PASS5_CHANGES;
            default -> throw new BadRequestException("Pass 5 outcome must be PASS or FAIL");
        };
        specEventService.record(taskId, type, EventSource.PASS5, UUID.randomUUID().toString(),
                result == Pass5Result.FAIL ? critique : null, Instant.now());
        return mapToResponse(specTaskRepository.findById(taskId)
                .orElseThrow(() -> new ResourceNotFoundException("Task not found")));
    }

    private void applyAssignee(SpecTask.SpecTaskBuilder builder, SpecTaskRequest request) {
        AssigneeType type = request.getAssigneeType();
        if (type == null) {
            return;
        }
        builder.assigneeType(type);
        if (type == AssigneeType.HUMAN) {
            if (request.getAssignedUserId() == null) {
                throw new BadRequestException("assigned_user_id is required for a human assignee");
            }
            User user = userRepository.findById(request.getAssignedUserId())
                    .orElseThrow(() -> new ResourceNotFoundException("User not found"));
            builder.assignedUser(user);
        } else if (type == AssigneeType.AGENT) {
            if (request.getAssignedAgentId() == null) {
                throw new BadRequestException("assigned_agent_id is required for an agent assignee");
            }
            Agent agent = agentRepository.findById(request.getAssignedAgentId())
                    .orElseThrow(() -> new ResourceNotFoundException("Agent not found"));
            builder.assignedAgent(agent);
        }
    }

    private SpecTask loadForUser(Long taskId, User currentUser) {
        SpecTask task = specTaskRepository.findById(taskId)
                .orElseThrow(() -> new ResourceNotFoundException("Task not found"));
        validateUserAccess(task.getChange(), currentUser);
        return task;
    }

    private void validateUserAccess(Change change, User currentUser) {
        Long orgId = change.getProject().getOrganization().getId();
        if (!memberRepository.existsByOrganizationIdAndUserId(orgId, currentUser.getId())) {
            throw new ForbiddenException("User does not have access to this organization");
        }
    }

    private SpecTaskResponse mapToResponse(SpecTask task) {
        return SpecTaskResponse.builder()
                .id(task.getId())
                .title(task.getTitle())
                .status(task.getStatus())
                .changeId(task.getChange().getId())
                .requirementId(task.getRequirement() != null ? task.getRequirement().getId() : null)
                .requirementRef(task.getRequirement() != null ? task.getRequirement().getRequirementId() : null)
                .assigneeType(task.getAssigneeType())
                .assignedUserId(task.getAssignedUser() != null ? task.getAssignedUser().getId() : null)
                .assignedUserName(task.getAssignedUser() != null ? task.getAssignedUser().getFullName() : null)
                .assignedAgentId(task.getAssignedAgent() != null ? task.getAssignedAgent().getId() : null)
                .assignedAgentName(task.getAssignedAgent() != null ? task.getAssignedAgent().getName() : null)
                .pass5(task.getPass5())
                .blockerReason(task.getBlockerReason())
                .reviseReason(task.getReviseReason())
                .availableTransitions(new java.util.ArrayList<>(stateMachine.manualTargets(task.getStatus())))
                .createdAt(task.getCreatedAt())
                .updatedAt(task.getUpdatedAt())
                .build();
    }
}
