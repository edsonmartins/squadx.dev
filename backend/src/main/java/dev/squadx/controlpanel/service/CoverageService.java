package dev.squadx.controlpanel.service;

import dev.squadx.controlpanel.model.Scenario;
import dev.squadx.controlpanel.repository.ScenarioRepository;
import dev.squadx.exception.ForbiddenException;
import dev.squadx.exception.ResourceNotFoundException;
import dev.squadx.model.User;
import dev.squadx.repository.OrganizationMemberRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Marca a cobertura (cenário↔teste) de um cenário. É o writer mínimo que alimenta o gate do
 * Pass 5 (ADR-0005); a geração automática (scaffold_tests/scan) chega em {@code workspace-mcp-server}.
 */
@Service
@RequiredArgsConstructor
public class CoverageService {

    private final ScenarioRepository scenarioRepository;
    private final OrganizationMemberRepository memberRepository;

    @Transactional
    public void setCovered(Long scenarioId, boolean covered, User currentUser) {
        Scenario scenario = scenarioRepository.findById(scenarioId)
                .orElseThrow(() -> new ResourceNotFoundException("Scenario not found"));
        Long orgId = scenario.getRequirement().getChange().getProject().getOrganization().getId();
        if (!memberRepository.existsByOrganizationIdAndUserId(orgId, currentUser.getId())) {
            throw new ForbiddenException("User does not have access to this organization");
        }
        scenario.setCovered(covered);
        scenarioRepository.save(scenario);
    }
}

