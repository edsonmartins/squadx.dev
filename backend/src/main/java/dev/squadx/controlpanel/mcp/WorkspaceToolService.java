package dev.squadx.controlpanel.mcp;

import dev.squadx.controlpanel.dto.spectask.SpecTaskResponse;
import dev.squadx.controlpanel.mcp.dto.*;
import dev.squadx.controlpanel.model.Requirement;
import dev.squadx.controlpanel.model.Scenario;
import dev.squadx.controlpanel.model.SpecTask;
import dev.squadx.controlpanel.model.enums.EventSource;
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
import dev.squadx.intelligence.CodeIntelligenceModels;
import dev.squadx.intelligence.CodeIntelligenceProviderRegistry;
import dev.squadx.model.User;
import dev.squadx.model.enums.IntelligenceSnapshotStatus;
import dev.squadx.repository.CodeIntelligenceSnapshotRepository;
import dev.squadx.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.beans.factory.annotation.Value;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;
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
    private final CodeIntelligenceSnapshotRepository codeIntelligenceSnapshots;
    private final CodeIntelligenceProviderRegistry codeIntelligenceProviders;
    private final SpecMaterializer materializer;
    @Value("${intelligence.native.repository-root:${SQUADX_INTELLIGENCE_REPOSITORY_ROOT:/repositories}}")
    private String repositoryRoot;

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
        requireTaskInScope(session, request.getTaskId());
        String status = request.getStatus();

        if ("em_curso".equalsIgnoreCase(status)) {
            specEventService.record(request.getTaskId(), TaskEventType.STARTED, EventSource.MCP,
                    UUID.randomUUID().toString(), request.getNote(), Instant.now());
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
        requireTaskInScope(session, request.getTaskId());
        specEventService.record(request.getTaskId(), TaskEventType.BLOCKED, EventSource.MCP,
                UUID.randomUUID().toString(), request.getReason(), Instant.now());
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
                .message(result.message())
                .build();
    }

    @Transactional
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
        Set<String> usedMethodNames = new HashSet<>();
        int total = 0;
        int covered = 0;
        for (Requirement req : requirements) {
            for (Scenario sc : scenarioRepository.findByRequirementId(req.getId())) {
                total++;
                if (sc.isCovered()) {
                    covered++;
                }
                String baseMethodName = req.getRequirementId() + "_" + slug(sc.getName());
                String methodName = baseMethodName;
                int suffix = 2;
                while (!usedMethodNames.add(methodName)) {
                    methodName = baseMethodName + "_" + suffix++;
                }
                methods.add(ScaffoldTestsResponse.Method.builder()
                        .scenarioName(sc.getName())
                        .methodName(methodName)
                        .build());
            }
        }

        String className = "Change" + session.changeId() + "SpecTest";
        String file = "src/test/java/dev/squadx/spec/" + className + ".java";
        writeJUnitScaffold(session.projectId(), file, className, methods);
        return ScaffoldTestsResponse.builder()
                .className(className)
                .file(file)
                .methods(methods)
                .coverage(ScaffoldTestsResponse.Coverage.builder().total(total).covered(covered).build())
                .build();
    }

    private void writeJUnitScaffold(Long projectId, String relativeFile, String className,
                                    List<ScaffoldTestsResponse.Method> methods) {
        Path repository = Path.of(repositoryRoot).resolve(String.valueOf(projectId)).normalize();
        if (!Files.isDirectory(repository)) {
            // A workspace may only expose metadata (for example during planning); preserve the
            // proposed path and let the agent materialize it when the mirror is available.
            return;
        }
        Path target = repository.resolve(relativeFile).normalize();
        if (!target.startsWith(repository)) {
            throw new BadRequestException("E_SCOPE: scaffold path escaped repository root");
        }
        if (Files.exists(target)) {
            return;
        }
        StringBuilder source = new StringBuilder()
                .append("package dev.squadx.spec;\n\n")
                .append("import org.junit.jupiter.api.DisplayName;\n")
                .append("import org.junit.jupiter.api.Test;\n\n")
                .append("class ").append(className).append(" {\n");
        for (ScaffoldTestsResponse.Method method : methods) {
            source.append("    @Test\n")
                    .append("    @DisplayName(\"").append(javaString(method.getScenarioName())).append("\")\n")
                    .append("    void ").append(method.getMethodName()).append("() {\n")
                    .append("        throw new UnsupportedOperationException(\"Implement acceptance scenario\");\n")
                    .append("    }\n\n");
        }
        source.append("}\n");
        try {
            Files.createDirectories(target.getParent());
            Files.writeString(target, source, StandardCharsets.UTF_8,
                    java.nio.file.StandardOpenOption.CREATE_NEW);
        } catch (java.nio.file.FileAlreadyExistsException ignored) {
            // Another agent may have generated the same deterministic scaffold concurrently.
        } catch (IOException e) {
            throw new IllegalStateException("Unable to write JUnit scaffold", e);
        }
    }

    private String javaString(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"")
                .replace("\r", "\\r").replace("\n", "\\n");
    }

    @Transactional(readOnly = true)
    public CodeIntelligenceModels.SearchResult searchCode(WorkspaceSession session, SearchCodeRequest request) {
        var snapshot = codeIntelligenceSnapshots
                .findFirstByProjectIdAndStatusOrderByIndexedAtDesc(session.projectId(), IntelligenceSnapshotStatus.READY)
                .filter(candidate -> "native".equalsIgnoreCase(candidate.getProvider()))
                .orElseThrow(() -> new BadRequestException("E_CONFLICT: no READY native code snapshot for session project"));
        var provider = codeIntelligenceProviders.requireProvider(snapshot.getProvider(),
                CodeIntelligenceModels.Capability.SEARCH);
        return provider.search(new CodeIntelligenceModels.SearchQuery(
                snapshot.getExternalSnapshotId(), request.getQuery(), 0, request.getLimit()));
    }

    @Transactional(readOnly = true)
    public CodeIntelligenceModels.SymbolContext getSymbolContext(WorkspaceSession session, SymbolContextRequest request) {
        var snapshot = codeIntelligenceSnapshots
                .findFirstByProjectIdAndStatusOrderByIndexedAtDesc(session.projectId(), IntelligenceSnapshotStatus.READY)
                .filter(candidate -> "native".equalsIgnoreCase(candidate.getProvider()))
                .orElseThrow(() -> new BadRequestException("E_CONFLICT: no READY native code snapshot for session project"));
        var provider = codeIntelligenceProviders.requireProvider(snapshot.getProvider(),
                CodeIntelligenceModels.Capability.SYMBOL_CONTEXT);
        return provider.getSymbolContext(new CodeIntelligenceModels.SymbolQuery(
                snapshot.getExternalSnapshotId(), request.getSymbol()));
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

    private String slug(String name) {
        String normalized = java.text.Normalizer.normalize(name, java.text.Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "");                          // strip diacritics (á -> a)
        String s = normalized.toLowerCase().replaceAll("[^a-z0-9]+", "_").replaceAll("^_|_$", "");
        return s.isEmpty() ? "scenario" : s;
    }
}
