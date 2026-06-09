package dev.squadx.controlpanel.service;

import dev.squadx.controlpanel.dto.requirement.RequirementRequest;
import dev.squadx.controlpanel.dto.requirement.RequirementResponse;
import dev.squadx.controlpanel.dto.requirement.ScenarioRequest;
import dev.squadx.controlpanel.dto.requirement.ScenarioResponse;
import dev.squadx.controlpanel.model.Change;
import dev.squadx.controlpanel.model.Requirement;
import dev.squadx.controlpanel.model.Scenario;
import dev.squadx.controlpanel.repository.ChangeRepository;
import dev.squadx.controlpanel.repository.RequirementRepository;
import dev.squadx.controlpanel.repository.ScenarioRepository;
import dev.squadx.controlpanel.repository.SpecTaskRepository;
import dev.squadx.exception.BadRequestException;
import dev.squadx.exception.ForbiddenException;
import dev.squadx.exception.ResourceNotFoundException;
import dev.squadx.model.User;
import dev.squadx.repository.OrganizationMemberRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Requisitos e seus cenários de aceite. Realiza work-model R2 (todo requisito exige ≥1 cenário)
 * e R3 (rastreabilidade via {@code task_refs}).
 */
@Service
@RequiredArgsConstructor
public class RequirementService {

    private final RequirementRepository requirementRepository;
    private final ScenarioRepository scenarioRepository;
    private final ChangeRepository changeRepository;
    private final SpecTaskRepository specTaskRepository;
    private final OrganizationMemberRepository memberRepository;

    @Transactional
    public RequirementResponse create(RequirementRequest request, User currentUser) {
        Change change = changeRepository.findById(request.getChangeId())
                .orElseThrow(() -> new ResourceNotFoundException("Change not found"));
        validateUserAccess(change, currentUser);

        if (request.getScenarios() == null || request.getScenarios().isEmpty()) {
            throw new BadRequestException("A requirement must have at least one acceptance scenario");
        }

        String requirementId = (request.getRequirementId() != null && !request.getRequirementId().isBlank())
                ? request.getRequirementId()
                : "R" + (requirementRepository.findByChangeId(change.getId()).size() + 1);

        Requirement requirement = Requirement.builder()
                .change(change)
                .requirementId(requirementId)
                .type(request.getType())
                .title(request.getTitle())
                .description(request.getDescription())
                .build();
        requirement = requirementRepository.save(requirement);

        for (ScenarioRequest sc : request.getScenarios()) {
            scenarioRepository.save(Scenario.builder()
                    .requirement(requirement)
                    .name(sc.getName())
                    .whenCondition(sc.getWhen())
                    .thenResult(sc.getThen())
                    .covered(false)
                    .build());
        }

        return mapToResponse(requirement);
    }

    @Transactional(readOnly = true)
    public List<RequirementResponse> getByChangeId(Long changeId, User currentUser) {
        Change change = changeRepository.findById(changeId)
                .orElseThrow(() -> new ResourceNotFoundException("Change not found"));
        validateUserAccess(change, currentUser);

        return requirementRepository.findByChangeId(changeId).stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    private void validateUserAccess(Change change, User currentUser) {
        Long orgId = change.getProject().getOrganization().getId();
        if (!memberRepository.existsByOrganizationIdAndUserId(orgId, currentUser.getId())) {
            throw new ForbiddenException("User does not have access to this organization");
        }
    }

    private RequirementResponse mapToResponse(Requirement requirement) {
        List<ScenarioResponse> scenarios = scenarioRepository.findByRequirementId(requirement.getId()).stream()
                .map(s -> ScenarioResponse.builder()
                        .id(s.getId())
                        .name(s.getName())
                        .when(s.getWhenCondition())
                        .then(s.getThenResult())
                        .covered(s.isCovered())
                        .build())
                .collect(Collectors.toList());

        List<Long> taskRefs = specTaskRepository.findByRequirementId(requirement.getId()).stream()
                .map(t -> t.getId())
                .collect(Collectors.toList());

        return RequirementResponse.builder()
                .id(requirement.getId())
                .changeId(requirement.getChange().getId())
                .requirementId(requirement.getRequirementId())
                .type(requirement.getType())
                .title(requirement.getTitle())
                .description(requirement.getDescription())
                .scenarios(scenarios)
                .taskRefs(taskRefs)
                .createdAt(requirement.getCreatedAt())
                .updatedAt(requirement.getUpdatedAt())
                .build();
    }
}
