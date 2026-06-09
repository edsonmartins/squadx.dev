package dev.squadx.controlpanel.service;

import dev.squadx.controlpanel.model.Change;
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
import dev.squadx.model.Organization;
import dev.squadx.model.Project;
import dev.squadx.repository.OrganizationMemberRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class Pass5ServiceTest {

    @Mock private SpecTaskRepository specTaskRepository;
    @Mock private ScenarioRepository scenarioRepository;
    @Mock private Pass5RunRepository pass5RunRepository;
    @Mock private ConformanceReviewer reviewer;
    @Mock private SpecTaskService specTaskService;
    @Mock private dev.squadx.controlpanel.repository.ChangeRepository changeRepository;
    @Mock private OrganizationMemberRepository memberRepository;

    @InjectMocks private Pass5Service service;

    private SpecTask task;
    private Requirement requirement;
    private Change change;
    private final dev.squadx.model.User user = userWithId1();

    private static dev.squadx.model.User userWithId1() {
        dev.squadx.model.User u = dev.squadx.model.User.builder().email("u@example.com").fullName("U").build();
        u.setId(1L);
        return u;
    }

    @BeforeEach
    void setUp() {
        Organization org = Organization.builder().name("Org").slug("org").build();
        org.setId(100L);
        Project project = Project.builder().name("Proj").slug("proj").organization(org).build();
        project.setId(7L);
        change = Change.builder().project(project).build();
        change.setId(5L);
        requirement = Requirement.builder().change(change).requirementId("R1").build();
        requirement.setId(9L);
        task = SpecTask.builder().change(change).requirement(requirement).title("T")
                .pass5(Pass5Result.PENDING).build();
        task.setId(42L);
    }

    private Scenario scenario(String name, boolean covered) {
        return Scenario.builder().requirement(requirement).name(name)
                .whenCondition("w").thenResult("t").covered(covered).build();
    }

    private void mockValidateBase() {
        when(pass5RunRepository.existsBySpecTaskIdAndPrSha(eq(42L), anyString())).thenReturn(false);
        when(specTaskRepository.findById(42L)).thenReturn(Optional.of(task));
        when(pass5RunRepository.save(any(Pass5Run.class))).thenAnswer(i -> i.getArgument(0));
    }

    @Test
    void uncoveredScenarioFails() {  // R1/R2
        mockValidateBase();
        when(scenarioRepository.findByRequirementId(9L)).thenReturn(List.of(
                scenario("login ok", true), scenario("login inválido", false)));

        service.validate(42L, "sha1");

        verify(specTaskService).applyPass5Outcome(eq(42L), eq(Pass5Result.FAIL),
                argThat(c -> c != null && c.contains("login inválido")));
        verify(reviewer, never()).review(any());
    }

    @Test
    void allCoveredAndReviewerOkPasses() {  // R3/R4
        mockValidateBase();
        when(scenarioRepository.findByRequirementId(9L)).thenReturn(List.of(scenario("s", true)));
        when(reviewer.review(any())).thenReturn(ConformanceVerdict.ok());

        service.validate(42L, "sha1");

        verify(specTaskService).applyPass5Outcome(eq(42L), eq(Pass5Result.PASS), isNull());
    }

    @Test
    void reviewerDivergesFails() {  // R3
        mockValidateBase();
        when(scenarioRepository.findByRequirementId(9L)).thenReturn(List.of(scenario("s", true)));
        when(reviewer.review(any())).thenReturn(ConformanceVerdict.diverges("código não bate"));

        service.validate(42L, "sha1");

        verify(specTaskService).applyPass5Outcome(eq(42L), eq(Pass5Result.FAIL), eq("código não bate"));
    }

    @Test
    void idempotentByTaskAndSha() {  // R5
        when(pass5RunRepository.existsBySpecTaskIdAndPrSha(42L, "sha1")).thenReturn(true);
        when(pass5RunRepository.findTopBySpecTaskIdOrderByCreatedAtDesc(42L))
                .thenReturn(Optional.of(Pass5Run.builder().outcome(Pass5Result.PASS).build()));

        service.validate(42L, "sha1");

        verify(specTaskService, never()).applyPass5Outcome(anyLong(), any(), any());
        verify(pass5RunRepository, never()).save(any());
    }

    @Test
    void noScenariosFails() {
        task.setRequirement(null);
        mockValidateBase();

        service.validate(42L, "sha1");

        verify(specTaskService).applyPass5Outcome(eq(42L), eq(Pass5Result.FAIL),
                argThat(c -> c != null && c.contains("Nenhum cenário")));
    }

    @Test
    void getStatusesForChangeReturnsPerTask() {
        when(changeRepository.findById(5L)).thenReturn(Optional.of(change));
        when(memberRepository.existsByOrganizationIdAndUserId(100L, 1L)).thenReturn(true);
        when(specTaskRepository.findByChangeId(5L)).thenReturn(List.of(task));
        when(scenarioRepository.findByRequirementIdIn(List.of(9L))).thenReturn(List.of(scenario("s", true)));
        when(pass5RunRepository.findBySpecTaskIdInOrderByCreatedAtDescIdDesc(List.of(42L)))
                .thenReturn(List.of());

        var statuses = service.getStatusesForChange(5L, user);

        assertThat(statuses).hasSize(1);
        assertThat(statuses.get(0).getCoverageTotal()).isEqualTo(1);
        assertThat(statuses.get(0).getCoverageCovered()).isEqualTo(1);
    }
}
