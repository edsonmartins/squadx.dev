package dev.squadx.service;

import dev.squadx.dto.execution.ExecutionRequest;
import dev.squadx.dto.execution.ExecutionResponse;
import dev.squadx.dto.task.TaskRequest;
import dev.squadx.dto.task.TaskResponse;
import dev.squadx.model.*;
import dev.squadx.model.enums.AutopilotExecutionMode;
import dev.squadx.model.enums.AutopilotRunStatus;
import dev.squadx.model.enums.AutopilotTriggerType;
import dev.squadx.model.enums.TaskPriority;
import dev.squadx.repository.AgentRepository;
import dev.squadx.repository.AutopilotRepository;
import dev.squadx.repository.AutopilotRunRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AutopilotWorkServiceTest {

    @Mock private AutopilotRepository autopilotRepository;
    @Mock private AutopilotRunRepository autopilotRunRepository;
    @Mock private AgentRepository agentRepository;
    @Mock private TaskService taskService;
    @Mock private ExecutionService executionService;

    @InjectMocks private AutopilotWorkService workService;

    private User owner;
    private Organization organization;
    private Project project;
    private Squad squad;
    private Agent agent;
    private Autopilot autopilot;

    @BeforeEach
    void setUp() {
        owner = User.builder().email("owner@example.com").fullName("Owner").build();
        owner.setId(1L);

        organization = Organization.builder().name("Org").slug("org").build();
        organization.setId(100L);

        project = Project.builder().name("Proj").slug("proj").organization(organization).build();
        project.setId(7L);

        squad = Squad.builder().name("Squad").organization(organization).build();
        squad.setId(10L);

        agent = Agent.builder().name("Agent").squad(squad).build();
        agent.setId(50L);

        autopilot = Autopilot.builder()
                .name("Daily Standup")
                .cronExpression("0 9 * * *")
                .executionMode(AutopilotExecutionMode.CREATE_TASK)
                .organization(organization)
                .project(project)
                .taskTitle("Run standup")
                .taskPriority(TaskPriority.MEDIUM)
                .enabled(true)
                .runCount(0)
                .createdBy(owner)
                .build();
        autopilot.setId(1L);
    }

    @Test
    @DisplayName("CREATE_TASK mode creates a task and returns SUCCESS without execution")
    void createTaskModeSuccess() {
        when(autopilotRepository.findById(1L)).thenReturn(Optional.of(autopilot));
        when(taskService.create(any(TaskRequest.class), eq(owner)))
                .thenReturn(TaskResponse.builder().id(500L).build());

        var result = workService.perform(1L, AutopilotTriggerType.CRON);

        assertThat(result.status()).isEqualTo(AutopilotRunStatus.SUCCESS);
        assertThat(result.taskId()).isEqualTo(500L);
        assertThat(result.executionId()).isNull();
        verify(taskService).create(any(TaskRequest.class), eq(owner));
        verify(executionService, never()).startExecution(any(), any());
    }

    @Test
    @DisplayName("RUN_TASK mode with an online agent creates a task and starts execution")
    void runTaskModeSuccess() {
        autopilot.setExecutionMode(AutopilotExecutionMode.RUN_TASK);
        autopilot.setTargetAgent(agent);
        agent.setActive(true);
        agent.setLifecycleState("READY");
        agent.setLastHeartbeat(LocalDateTime.now());

        when(autopilotRepository.findById(1L)).thenReturn(Optional.of(autopilot));
        when(taskService.create(any(TaskRequest.class), eq(owner)))
                .thenReturn(TaskResponse.builder().id(500L).build());
        when(executionService.startExecution(any(ExecutionRequest.class), eq(owner)))
                .thenReturn(ExecutionResponse.builder().id(900L).build());

        var result = workService.perform(1L, AutopilotTriggerType.CRON);

        assertThat(result.status()).isEqualTo(AutopilotRunStatus.SUCCESS);
        assertThat(result.taskId()).isEqualTo(500L);
        assertThat(result.executionId()).isEqualTo(900L);
        verify(executionService).startExecution(any(ExecutionRequest.class), eq(owner));
    }

    @Test
    @DisplayName("RUN_TASK mode is SKIPPED when the target agent is offline (admission gate)")
    void runTaskModeSkippedWhenAgentOffline() {
        autopilot.setExecutionMode(AutopilotExecutionMode.RUN_TASK);
        autopilot.setTargetAgent(agent);
        agent.setActive(true);
        agent.setLifecycleState("READY");
        agent.setLastHeartbeat(LocalDateTime.now().minusMinutes(10)); // stale -> offline

        when(autopilotRepository.findById(1L)).thenReturn(Optional.of(autopilot));

        var result = workService.perform(1L, AutopilotTriggerType.CRON);

        assertThat(result.status()).isEqualTo(AutopilotRunStatus.SKIPPED);
        assertThat(result.message()).contains("offline");
        verify(taskService, never()).create(any(), any());
        verify(executionService, never()).startExecution(any(), any());
    }

    @Test
    @DisplayName("RUN_TASK mode resolves an online agent from the target squad")
    void runTaskModeResolvesSquadAgent() {
        autopilot.setExecutionMode(AutopilotExecutionMode.RUN_TASK);
        autopilot.setTargetSquad(squad);
        agent.setActive(true);
        agent.setLifecycleState("READY");
        agent.setLastHeartbeat(LocalDateTime.now());

        when(autopilotRepository.findById(1L)).thenReturn(Optional.of(autopilot));
        when(agentRepository.findBySquadIdAndIsActiveTrue(10L)).thenReturn(List.of(agent));
        when(taskService.create(any(TaskRequest.class), eq(owner)))
                .thenReturn(TaskResponse.builder().id(501L).build());
        when(executionService.startExecution(any(ExecutionRequest.class), eq(owner)))
                .thenReturn(ExecutionResponse.builder().id(901L).build());

        var result = workService.perform(1L, AutopilotTriggerType.CRON);

        assertThat(result.status()).isEqualTo(AutopilotRunStatus.SUCCESS);
        verify(agentRepository).findBySquadIdAndIsActiveTrue(10L);
    }

    @Test
    @DisplayName("FAILED when the autopilot has no owner user")
    void failedWhenNoOwner() {
        autopilot.setCreatedBy(null);
        when(autopilotRepository.findById(1L)).thenReturn(Optional.of(autopilot));

        var result = workService.perform(1L, AutopilotTriggerType.CRON);

        assertThat(result.status()).isEqualTo(AutopilotRunStatus.FAILED);
        verify(taskService, never()).create(any(), any());
    }

    @Test
    @DisplayName("SKIPPED when the autopilot no longer exists")
    void skippedWhenMissing() {
        when(autopilotRepository.findById(99L)).thenReturn(Optional.empty());

        var result = workService.perform(99L, AutopilotTriggerType.CRON);

        assertThat(result.status()).isEqualTo(AutopilotRunStatus.SKIPPED);
    }

    @Test
    @DisplayName("record() persists the run and bumps lastRunAt/runCount")
    void recordPersistsRunAndBumpsCounters() {
        when(autopilotRepository.findById(1L)).thenReturn(Optional.of(autopilot));
        when(autopilotRunRepository.save(any(AutopilotRun.class))).thenAnswer(i -> {
            AutopilotRun r = i.getArgument(0);
            r.setId(7000L);
            return r;
        });

        var result = AutopilotWorkService.PerformResult.success(500L, null, "ok");
        AutopilotRun run = workService.record(1L, AutopilotTriggerType.MANUAL, result);

        assertThat(run).isNotNull();
        assertThat(run.getStatus()).isEqualTo(AutopilotRunStatus.SUCCESS);
        assertThat(run.getCreatedTaskId()).isEqualTo(500L);
        assertThat(autopilot.getRunCount()).isEqualTo(1);
        assertThat(autopilot.getLastRunAt()).isNotNull();
        verify(autopilotRepository).save(autopilot);
    }
}
