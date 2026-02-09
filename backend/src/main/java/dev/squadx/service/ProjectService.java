package dev.squadx.service;

import dev.squadx.dto.common.PageResponse;
import dev.squadx.dto.project.ProjectRequest;
import dev.squadx.dto.project.ProjectResponse;
import dev.squadx.exception.BadRequestException;
import dev.squadx.exception.ForbiddenException;
import dev.squadx.exception.ResourceNotFoundException;
import dev.squadx.model.Organization;
import dev.squadx.model.Project;
import dev.squadx.model.Squad;
import dev.squadx.model.User;
import dev.squadx.repository.OrganizationMemberRepository;
import dev.squadx.repository.OrganizationRepository;
import dev.squadx.repository.ProjectRepository;
import dev.squadx.repository.SquadRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.text.Normalizer;
import java.util.Locale;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class ProjectService {

    private final ProjectRepository projectRepository;
    private final OrganizationRepository organizationRepository;
    private final OrganizationMemberRepository memberRepository;
    private final SquadRepository squadRepository;

    private static final Pattern NONLATIN = Pattern.compile("[^\\w-]");
    private static final Pattern WHITESPACE = Pattern.compile("[\\s]");

    @Transactional
    public ProjectResponse create(ProjectRequest request, User currentUser) {
        if (request.getOrganizationId() == null) {
            throw new BadRequestException("Organization ID is required");
        }

        Organization org = organizationRepository.findById(request.getOrganizationId())
                .orElseThrow(() -> new ResourceNotFoundException("Organization not found"));

        validateUserAccess(org.getId(), currentUser.getId());

        String slug = generateSlug(request.getName());
        if (projectRepository.existsBySlug(slug)) {
            slug = slug + "-" + System.currentTimeMillis();
        }

        Project project = Project.builder()
                .name(request.getName())
                .slug(slug)
                .description(request.getDescription())
                .repositoryUrl(request.getRepositoryUrl())
                .defaultBranch(request.getDefaultBranch() != null ? request.getDefaultBranch() : "main")
                .organization(org)
                .build();

        if (request.getSquadId() != null) {
            Squad squad = squadRepository.findById(request.getSquadId())
                    .orElseThrow(() -> new ResourceNotFoundException("Squad not found"));
            project.setSquad(squad);
        }

        project = projectRepository.save(project);

        return mapToResponse(project);
    }

    @Transactional(readOnly = true)
    public ProjectResponse getById(Long id, User currentUser) {
        Project project = projectRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Project not found"));

        validateUserAccess(project.getOrganization().getId(), currentUser.getId());

        return mapToResponse(project);
    }

    @Transactional(readOnly = true)
    public ProjectResponse getBySlug(String slug, User currentUser) {
        Project project = projectRepository.findBySlug(slug)
                .orElseThrow(() -> new ResourceNotFoundException("Project not found"));

        validateUserAccess(project.getOrganization().getId(), currentUser.getId());

        return mapToResponse(project);
    }

    @Transactional(readOnly = true)
    public PageResponse<ProjectResponse> getByOrganizationId(Long organizationId, Pageable pageable, User currentUser) {
        validateUserAccess(organizationId, currentUser.getId());

        Page<ProjectResponse> page = projectRepository.findActiveByOrganizationId(organizationId, pageable)
                .map(this::mapToResponse);

        return PageResponse.from(page);
    }

    @Transactional(readOnly = true)
    public PageResponse<ProjectResponse> getByUserId(Long userId, Pageable pageable) {
        Page<ProjectResponse> page = projectRepository.findByUserId(userId, pageable)
                .map(this::mapToResponse);

        return PageResponse.from(page);
    }

    @Transactional
    public ProjectResponse update(Long id, ProjectRequest request, User currentUser) {
        Project project = projectRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Project not found"));

        validateUserAccess(project.getOrganization().getId(), currentUser.getId());

        if (request.getName() != null) {
            project.setName(request.getName());
        }
        if (request.getDescription() != null) {
            project.setDescription(request.getDescription());
        }
        if (request.getRepositoryUrl() != null) {
            project.setRepositoryUrl(request.getRepositoryUrl());
        }
        if (request.getDefaultBranch() != null) {
            project.setDefaultBranch(request.getDefaultBranch());
        }
        if (request.getSquadId() != null) {
            Squad squad = squadRepository.findById(request.getSquadId())
                    .orElseThrow(() -> new ResourceNotFoundException("Squad not found"));
            project.setSquad(squad);
        }

        project = projectRepository.save(project);

        return mapToResponse(project);
    }

    @Transactional
    public void delete(Long id, User currentUser) {
        Project project = projectRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Project not found"));

        validateUserAccess(project.getOrganization().getId(), currentUser.getId());

        projectRepository.delete(project);
    }

    private void validateUserAccess(Long organizationId, Long userId) {
        if (!memberRepository.existsByOrganizationIdAndUserId(organizationId, userId)) {
            throw new ForbiddenException("User does not have access to this organization");
        }
    }

    private String generateSlug(String input) {
        String nowhitespace = WHITESPACE.matcher(input).replaceAll("-");
        String normalized = Normalizer.normalize(nowhitespace, Normalizer.Form.NFD);
        String slug = NONLATIN.matcher(normalized).replaceAll("");
        return slug.toLowerCase(Locale.ENGLISH);
    }

    private ProjectResponse mapToResponse(Project project) {
        return ProjectResponse.builder()
                .id(project.getId())
                .name(project.getName())
                .slug(project.getSlug())
                .description(project.getDescription())
                .repositoryUrl(project.getRepositoryUrl())
                .defaultBranch(project.getDefaultBranch())
                .isActive(project.isActive())
                .organizationId(project.getOrganization().getId())
                .organizationName(project.getOrganization().getName())
                .squadId(project.getSquad() != null ? project.getSquad().getId() : null)
                .squadName(project.getSquad() != null ? project.getSquad().getName() : null)
                .tasksCount((int) projectRepository.countTasksByProjectId(project.getId()))
                .createdAt(project.getCreatedAt())
                .updatedAt(project.getUpdatedAt())
                .build();
    }
}
