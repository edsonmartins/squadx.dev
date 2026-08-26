package dev.squadx.controlpanel.service;

import dev.squadx.controlpanel.dto.change.ChangeRequest;
import dev.squadx.controlpanel.dto.change.ChangeResponse;
import dev.squadx.controlpanel.model.Change;
import dev.squadx.controlpanel.model.enums.ChangePhase;
import dev.squadx.controlpanel.model.enums.SpecTaskStatus;
import dev.squadx.controlpanel.repository.ChangeRepository;
import dev.squadx.controlpanel.repository.SpecTaskRepository;
import dev.squadx.dto.common.PageResponse;
import dev.squadx.exception.ForbiddenException;
import dev.squadx.exception.ResourceNotFoundException;
import dev.squadx.model.Project;
import dev.squadx.model.User;
import dev.squadx.repository.OrganizationMemberRepository;
import dev.squadx.repository.ProjectRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Mudanças (changes) do Control Panel. Realiza work-model R1 e a projeção "onde estamos" (R7).
 */
@Service
@RequiredArgsConstructor
public class ChangeService {

    private final ChangeRepository changeRepository;
    private final SpecTaskRepository specTaskRepository;
    private final ProjectRepository projectRepository;
    private final OrganizationMemberRepository memberRepository;

    @Transactional
    public ChangeResponse create(ChangeRequest request, User currentUser) {
        Project project = projectRepository.findById(request.getProjectId())
                .orElseThrow(() -> new ResourceNotFoundException("Project not found"));
        validateUserAccess(project.getOrganization().getId(), currentUser.getId());

        Change change = Change.builder()
                .project(project)
                .module(request.getModule())
                .phase(request.getPhase() != null ? request.getPhase() : ChangePhase.SPEC)
                .createdBy(currentUser)
                .build();

        change = changeRepository.save(change);
        return mapToResponse(change);
    }

    @Transactional(readOnly = true)
    public ChangeResponse getById(Long id, User currentUser) {
        Change change = loadForUser(id, currentUser);
        return mapToResponse(change);
    }

    @Transactional(readOnly = true)
    public PageResponse<ChangeResponse> getByProjectId(Long projectId, Pageable pageable, User currentUser) {
        Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> new ResourceNotFoundException("Project not found"));
        validateUserAccess(project.getOrganization().getId(), currentUser.getId());

        Page<ChangeResponse> page = changeRepository.findByProjectId(projectId, pageable)
                .map(this::mapToResponse);
        return PageResponse.from(page);
    }

    /**
     * Projeção "onde estamos" por projeto (R7): contagem por status, total, concluídas e progresso.
     * Derivada das tarefas (que em execution-tracking passam a ser projeção de eventos).
     */
    @Transactional(readOnly = true)
    public Map<String, Object> whereWeAre(Long projectId, User currentUser) {
        Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> new ResourceNotFoundException("Project not found"));
        validateUserAccess(project.getOrganization().getId(), currentUser.getId());

        Map<String, Long> counts = new LinkedHashMap<>();
        for (SpecTaskStatus s : SpecTaskStatus.values()) {
            counts.put(s.name(), 0L);
        }
        long total = 0;
        for (Object[] row : specTaskRepository.countByStatusForProject(projectId)) {
            SpecTaskStatus status = (SpecTaskStatus) row[0];
            long count = (Long) row[1];
            counts.put(status.name(), count);
            total += count;
        }
        long done = counts.getOrDefault(SpecTaskStatus.CONCLUIDA.name(), 0L);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("project_id", projectId);
        result.put("counts", counts);
        result.put("total", total);
        result.put("concluidas", done);
        result.put("progress", total > 0 ? (double) done / total : 0.0);
        return result;
    }

    /** Valida acesso e devolve o escopo (org/projeto/change) para abrir uma sessão MCP do workspace. */
    @Transactional(readOnly = true)
    public WorkspaceScope resolveScope(Long changeId, User currentUser) {
        Change change = loadForUser(changeId, currentUser);
        return new WorkspaceScope(
                change.getProject().getOrganization().getId(),
                change.getProject().getId(),
                change.getId());
    }

    public record WorkspaceScope(Long orgId, Long projectId, Long changeId) {}

    private Change loadForUser(Long id, User currentUser) {
        Change change = changeRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Change not found"));
        validateUserAccess(change.getProject().getOrganization().getId(), currentUser.getId());
        return change;
    }

    private void validateUserAccess(Long organizationId, Long userId) {
        if (!memberRepository.existsByOrganizationIdAndUserId(organizationId, userId)) {
            throw new ForbiddenException("User does not have access to this organization");
        }
    }

    private ChangeResponse mapToResponse(Change change) {
        return ChangeResponse.builder()
                .id(change.getId())
                .module(change.getModule())
                .phase(change.getPhase())
                .projectId(change.getProject().getId())
                .projectName(change.getProject().getName())
                .createdById(change.getCreatedBy() != null ? change.getCreatedBy().getId() : null)
                .createdByName(change.getCreatedBy() != null ? change.getCreatedBy().getFullName() : null)
                .createdAt(change.getCreatedAt())
                .updatedAt(change.getUpdatedAt())
                .build();
    }
}

