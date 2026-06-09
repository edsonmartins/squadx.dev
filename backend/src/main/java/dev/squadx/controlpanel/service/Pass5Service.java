package dev.squadx.controlpanel.service;

import dev.squadx.controlpanel.dto.pass5.Pass5StatusResponse;
import dev.squadx.controlpanel.model.Pass5Run;
import dev.squadx.controlpanel.model.Requirement;
import dev.squadx.controlpanel.model.Scenario;
import dev.squadx.controlpanel.model.SpecTask;
import dev.squadx.controlpanel.model.enums.Pass5Result;
import dev.squadx.controlpanel.repository.Pass5RunRepository;
import dev.squadx.controlpanel.repository.ScenarioRepository;
import dev.squadx.controlpanel.repository.SpecTaskRepository;
import dev.squadx.controlpanel.validation.ConformanceReviewer;
import dev.squadx.controlpanel.validation.ConformanceVerdict;
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
 * Portão de conformidade Pass 5 (RFC-0004, ADR-0005). Confere cobertura cenário↔teste e
 * conformidade comportamental, e aplica o desfecho via {@link SpecTaskService#applyPass5Outcome}
 * — único caminho para {@code concluida}/{@code ajustes} (ADR-0004).
 */
@Service
@RequiredArgsConstructor
public class Pass5Service {

    private final SpecTaskRepository specTaskRepository;
    private final ScenarioRepository scenarioRepository;
    private final Pass5RunRepository pass5RunRepository;
    private final ConformanceReviewer reviewer;
    private final SpecTaskService specTaskService;
    private final OrganizationMemberRepository memberRepository;

    /**
     * Roda o Pass 5 para uma tarefa (idempotente por {@code (task, prSha)} — R5). Disparado pelo
     * merge (evento) ou sob demanda. {@code prSha} nulo força reexecução.
     */
    @Transactional
    public Pass5Run validate(Long specTaskId, String prSha) {
        if (prSha != null && pass5RunRepository.existsBySpecTaskIdAndPrSha(specTaskId, prSha)) {
            return pass5RunRepository.findTopBySpecTaskIdOrderByCreatedAtDesc(specTaskId).orElse(null);
        }

        SpecTask task = specTaskRepository.findById(specTaskId)
                .orElseThrow(() -> new ResourceNotFoundException("Task not found"));
        List<Scenario> scenarios = scenariosOf(task);

        int total = scenarios.size();
        int covered = (int) scenarios.stream().filter(Scenario::isCovered).count();

        Pass5Result outcome;
        String critique;

        if (total == 0) {
            outcome = Pass5Result.FAIL;
            critique = "Nenhum cenário de aceite para validar";
        } else {
            List<String> uncovered = scenarios.stream()
                    .filter(s -> !s.isCovered()).map(Scenario::getName).collect(Collectors.toList());
            if (!uncovered.isEmpty()) {
                outcome = Pass5Result.FAIL;                                  // R1/R2
                critique = "Cenários sem teste: " + String.join(", ", uncovered);
            } else {
                ConformanceVerdict verdict = reviewer.review(toRequest(specTaskId, prSha, scenarios)); // R3
                if (verdict.diverges()) {
                    outcome = Pass5Result.FAIL;
                    critique = verdict.critique();
                } else {
                    outcome = Pass5Result.PASS;
                    critique = null;
                }
            }
        }

        Pass5Run run = pass5RunRepository.save(Pass5Run.builder()
                .specTask(task).prSha(prSha).outcome(outcome).critique(critique)
                .coverageTotal(total).coverageCovered(covered).build());

        specTaskService.applyPass5Outcome(specTaskId, outcome, critique);     // R4
        return run;
    }

    @Transactional
    public Pass5StatusResponse runOnDemand(Long specTaskId, User currentUser) {
        validateUserAccess(loadTask(specTaskId), currentUser);
        validate(specTaskId, null);
        return getStatus(specTaskId, currentUser);
    }

    @Transactional(readOnly = true)
    public Pass5StatusResponse getStatus(Long specTaskId, User currentUser) {
        SpecTask task = loadTask(specTaskId);
        validateUserAccess(task, currentUser);
        List<Scenario> scenarios = scenariosOf(task);
        Pass5Run last = pass5RunRepository.findTopBySpecTaskIdOrderByCreatedAtDesc(specTaskId).orElse(null);

        return Pass5StatusResponse.builder()
                .specTaskId(specTaskId)
                .outcome(last != null ? last.getOutcome() : null)
                .critique(last != null ? last.getCritique() : null)
                .coverageTotal(scenarios.size())
                .coverageCovered((int) scenarios.stream().filter(Scenario::isCovered).count())
                .scenarios(scenarios.stream()
                        .map(s -> Pass5StatusResponse.ScenarioCoverage.builder()
                                .id(s.getId()).name(s.getName()).covered(s.isCovered()).build())
                        .collect(Collectors.toList()))
                .build();
    }

    private List<Scenario> scenariosOf(SpecTask task) {
        Requirement req = task.getRequirement();
        return req != null ? scenarioRepository.findByRequirementId(req.getId()) : List.of();
    }

    private ConformanceReviewer.ConformanceRequest toRequest(Long specTaskId, String prSha,
                                                             List<Scenario> scenarios) {
        List<ConformanceReviewer.ScenarioRef> refs = scenarios.stream()
                .map(s -> new ConformanceReviewer.ScenarioRef(s.getName(), s.getWhenCondition(), s.getThenResult()))
                .collect(Collectors.toList());
        return new ConformanceReviewer.ConformanceRequest(specTaskId, prSha, refs);
    }

    private SpecTask loadTask(Long specTaskId) {
        return specTaskRepository.findById(specTaskId)
                .orElseThrow(() -> new ResourceNotFoundException("Task not found"));
    }

    private void validateUserAccess(SpecTask task, User currentUser) {
        Long orgId = task.getChange().getProject().getOrganization().getId();
        if (!memberRepository.existsByOrganizationIdAndUserId(orgId, currentUser.getId())) {
            throw new ForbiddenException("User does not have access to this organization");
        }
    }
}
