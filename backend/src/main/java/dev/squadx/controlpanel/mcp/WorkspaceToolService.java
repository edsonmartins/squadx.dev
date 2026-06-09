package dev.squadx.controlpanel.mcp;

import dev.squadx.controlpanel.dto.spectask.SpecTaskResponse;
import dev.squadx.controlpanel.dto.spectask.SpecTaskTransitionRequest;
import dev.squadx.controlpanel.mcp.dto.*;
import dev.squadx.controlpanel.model.Requirement;
import dev.squadx.controlpanel.model.Scenario;
import dev.squadx.controlpanel.model.SpecTask;
import dev.squadx.controlpanel.model.enums.EventSource;
import dev.squadx.controlpanel.model.enums.SpecTaskStatus;
import dev.squadx.controlpanel.model.enums.TaskEventType;
import dev.squadx.controlpanel.repository.RequirementRepository;
import dev.squadx.controlpanel.repository.ScenarioRepository;
import dev.squadx.controlpanel.repository.SpecTaskRepository;
import dev.squadx.controlpanel.service.ChangeService;
import dev.squadx.controlpanel.service.RequirementService;
import dev.squadx.controlpanel.service.SpecEventService;
import dev.squadx.controlpanel.service.SpecTaskService;
import dev.squadx.exception.BadRequestException;
import dev.squadx.exception.ResourceNotFoundException;
import dev.squadx.model.User;
import dev.squadx.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Lógica das tools do contrato `workspace` (RFC-0001, ADR-0003). O agente atua em nome do usuário
 * da sessão (RBAC intacto) e tudo é restrito ao {@code changeId} da sessão (escopo — R6).
 */
@Service
@RequiredArgsConstructor
public class WorkspaceToolService {

    private final ChangeService changeService;
    private final RequirementService requirementService;
    private final SpecTaskService specTaskService;
    private final SpecEventService specEventService;
    private final SpecTaskRepository specTaskRepository;
    private final RequirementRepository requirementRepository;
    private final ScenarioRepository scenarioRepository;
    private final UserRepository userRepository;
    private final SpecMaterializer materializer;

    @Transactional(readOnly = true)
    public GetChangeResponse getChange(WorkspaceSession session) {
        User user = resolveUser(session);
        return GetChangeResponse.builder()
                .change(changeService.getById(session.changeId(), user))
                .requirements(requirementService.getByChangeId(session.changeId(), user))
                .tasks(specTaskService.getByChangeId(session.changeId(), user))
                .build();
    }

    @Transactional(readOnly = true)
    public List<SpecTaskResponse> getTasks(WorkspaceSession session, String assignee) {
        User user = resolveUser(session);
        List<SpecTaskResponse> tasks = specTaskService.getByChangeId(session.changeId(), user);
        if (assignee == null || assignee.isBlank()) {
            return tasks;
        }
        return tasks.stream()
                .filter(t -> assignee.equals(t.getAssignedAgentName()) || assignee.equals(t.getAssignedUserName()))
                .collect(Collectors.toList());
    }

    @Transactional
    public UpdateTaskStatusResponse updateTaskStatus(WorkspaceSession session, UpdateTaskStatusRequest request) {
        User user = resolveUser(session);
        requireTaskInScope(session, request.getTaskId());
        String status = request.getStatus();

        if ("em_curso".equalsIgnoreCase(status)) {
            specTaskService.transition(request.getTaskId(),
                    SpecTaskTransitionRequest.builder().status(SpecTaskStatus.EM_CURSO).build(), user);
        } else if ("implementado".equalsIgnoreCase(status)) {
            // "implementado" é evento, não estado de board (ADR-0004) — não muda o status.
            specEventService.record(request.getTaskId(), TaskEventType.IMPLEMENTED, EventSource.MCP,
                    UUID.randomUUID().toString(), request.getNote(), Instant.now());
        } else {
            throw new BadRequestException("E_VALIDATION: status must be 'em_curso' or 'implementado'");
        }
        return statusResponse(request.getTaskId());
    }

    @Transactional
    public UpdateTaskStatusResponse reportBlocker(WorkspaceSession session, ReportBlockerRequest request) {
        User user = resolveUser(session);
        requireTaskInScope(session, request.getTaskId());
        specTaskService.transition(request.getTaskId(),
                SpecTaskTransitionRequest.builder()
                        .status(SpecTaskStatus.BLOQUEADA).note(request.getReason()).build(),
                user);
        return statusResponse(request.getTaskId());
    }

    @Transactional
    public MaterializeResponse materializeChange(WorkspaceSession session) {
        SpecMaterializer.MaterializationResult result = materializer.materialize(session.changeId());
        return MaterializeResponse.builder()
                .ok(result.available())
                .changeId(session.changeId())
                .version(result.version())
                .commit(result.commit())
                .prUrl(result.prUrl())
                .message(result.message())
                .build();
    }

    @Transactional(readOnly = true)
    public ScaffoldTestsResponse scaffoldTests(WorkspaceSession session, ScaffoldTestsRequest request) {
        List<Requirement> requirements;
        if (request.getRequirementId() != null) {
            Requirement req = requirementRepository.findById(request.getRequirementId())
                    .orElseThrow(() -> new ResourceNotFoundException("Requirement not found"));
            if (!req.getChange().getId().equals(session.changeId())) {
                throw new BadRequestException("E_SCOPE: requirement is outside the session's change");
            }
            requirements = List.of(req);
        } else {
            requirements = requirementRepository.findByChangeId(session.changeId());
        }

        List<ScaffoldTestsResponse.Method> methods = new ArrayList<>();
        int total = 0;
        int covered = 0;
        for (Requirement req : requirements) {
            for (Scenario sc : scenarioRepository.findByRequirementId(req.getId())) {
                total++;
                if (sc.isCovered()) {
                    covered++;
                }
                methods.add(ScaffoldTestsResponse.Method.builder()
                        .scenarioName(sc.getName())
                        .methodName(dev.squadx.controlpanel.validation.ScenarioTestNaming
                                .methodName(req.getRequirementId(), sc.getName()))
                        .build());
            }
        }

        String className = "Change" + session.changeId() + "SpecTest";
        return ScaffoldTestsResponse.builder()
                .className(className)
                .file("src/test/java/dev/squadx/spec/" + className + ".java")
                .methods(methods)
                .coverage(ScaffoldTestsResponse.Coverage.builder().total(total).covered(covered).build())
                .build();
    }

    private User resolveUser(WorkspaceSession session) {
        return userRepository.findById(session.userId())
                .orElseThrow(() -> new ResourceNotFoundException("Session user not found"));
    }

    private SpecTask requireTaskInScope(WorkspaceSession session, Long taskId) {
        SpecTask task = specTaskRepository.findById(taskId)
                .orElseThrow(() -> new ResourceNotFoundException("Task not found"));
        if (!task.getChange().getId().equals(session.changeId())) {
            throw new BadRequestException("E_SCOPE: task is outside the session's change");
        }
        return task;
    }

    private UpdateTaskStatusResponse statusResponse(Long taskId) {
        SpecTask task = specTaskRepository.findById(taskId)
                .orElseThrow(() -> new ResourceNotFoundException("Task not found"));
        return UpdateTaskStatusResponse.builder()
                .ok(true).taskId(taskId).status(task.getStatus()).build();
    }

}
