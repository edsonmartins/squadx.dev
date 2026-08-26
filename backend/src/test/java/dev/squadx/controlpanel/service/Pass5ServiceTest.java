package dev.squadx.controlpanel.service;

import dev.squadx.controlpanel.model.Change;
import dev.squadx.controlpanel.model.Requirement;
import dev.squadx.controlpanel.model.Scenario;
import dev.squadx.controlpanel.model.SpecTask;
import dev.squadx.controlpanel.model.enums.Pass5Result;
import dev.squadx.controlpanel.repository.Pass5RunRepository;
import dev.squadx.controlpanel.repository.ScenarioRepository;
import dev.squadx.controlpanel.repository.SpecTaskRepository;
import dev.squadx.controlpanel.validation.ConformanceReviewer;
import dev.squadx.controlpanel.validation.ConformanceVerdict;
import dev.squadx.controlpanel.validation.GitDiffProvider;
import dev.squadx.controlpanel.validation.TestSuiteExecutor;
import dev.squadx.repository.OrganizationMemberRepository;
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
    @Mock private SpecTaskRepository taskRepository;
    @Mock private ScenarioRepository scenarioRepository;
    @Mock private Pass5RunRepository runRepository;
    @Mock private ConformanceReviewer reviewer;
    @Mock private SpecTaskService taskService;
    @Mock private OrganizationMemberRepository memberRepository;
    @Mock private GitDiffProvider diffProvider;
    @Mock private TestSuiteExecutor testExecutor;
    @InjectMocks private Pass5Service service;

    @Test
    void R1_whenScenarioHasNoMappedTest_thenPass5FailsWithoutReview() {
        SpecTask task = task(7L);
        Scenario scenario = scenario(task, "login_invalido", false);
        givenTask(task, List.of(scenario));

        var run = service.validate(7L, "abc1234");

        assertThat(run.getOutcome()).isEqualTo(Pass5Result.FAIL);
        assertThat(run.getCritique()).contains("Cenários sem teste");
        verifyNoInteractions(testExecutor, reviewer);
        verify(taskService).applyPass5Outcome(7L, Pass5Result.FAIL, run.getCritique());
    }

    @Test
    void R2_whenMappedTestFails_thenPass5FailsBeforeConformance() {
        SpecTask task = task(7L);
        Scenario scenario = scenario(task, "login_valido", true);
        givenTask(task, List.of(scenario));
        when(testExecutor.execute(7L, "abc1234"))
                .thenReturn(TestSuiteExecutor.TestExecutionResult.failed("JUnit failure"));

        var run = service.validate(7L, "abc1234");

        assertThat(run.getOutcome()).isEqualTo(Pass5Result.FAIL);
        assertThat(run.getCritique()).contains("JUnit failure");
        verifyNoInteractions(reviewer);
    }

    @Test
    void R5_whenSameTaskAndShaArrivesTwice_thenSecondRunIsIdempotent() {
        SpecTask task = task(7L);
        var existing = dev.squadx.controlpanel.model.Pass5Run.builder().specTask(task)
                .prSha("abc1234").outcome(Pass5Result.PASS).build();
        when(runRepository.existsBySpecTaskIdAndPrSha(7L, "abc1234")).thenReturn(true);
        when(runRepository.findTopBySpecTaskIdOrderByCreatedAtDesc(7L)).thenReturn(Optional.of(existing));

        assertThat(service.validate(7L, "abc1234")).isSameAs(existing);
        verify(taskRepository, never()).findById(anyLong());
        verify(runRepository, never()).save(any());
    }

    private void givenTask(SpecTask task, List<Scenario> scenarios) {
        when(runRepository.existsBySpecTaskIdAndPrSha(anyLong(), anyString())).thenReturn(false);
        when(taskRepository.findById(7L)).thenReturn(Optional.of(task));
        when(scenarioRepository.findByRequirementId(9L)).thenReturn(scenarios);
        when(runRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(reviewer.review(any())).thenReturn(ConformanceVerdict.ok());
        when(testExecutor.execute(anyLong(), anyString())).thenReturn(TestSuiteExecutor.TestExecutionResult.passed("ok"));
    }

    private SpecTask task(Long id) {
        Change change = Change.builder().build();
        Requirement requirement = Requirement.builder().change(change).requirementId("R1").build();
        requirement.setId(9L);
        SpecTask task = SpecTask.builder().change(change).requirement(requirement).title("test").build();
        task.setId(id);
        return task;
    }

    private Scenario scenario(SpecTask task, String name, boolean covered) {
        return Scenario.builder().requirement(task.getRequirement()).name(name)
                .whenCondition("Quando dados válidos").thenResult("Então a operação funciona")
                .covered(covered).build();
    }
}
