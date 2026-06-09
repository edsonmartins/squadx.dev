package dev.squadx.service;

import dev.squadx.dto.autopilot.AutopilotRequest;
import dev.squadx.dto.autopilot.AutopilotResponse;
import dev.squadx.dto.autopilot.AutopilotRunResponse;
import dev.squadx.exception.ForbiddenException;
import dev.squadx.exception.ResourceNotFoundException;
import dev.squadx.model.*;
import dev.squadx.model.enums.AutopilotExecutionMode;
import dev.squadx.model.enums.AutopilotRunStatus;
import dev.squadx.model.enums.AutopilotTriggerType;
import dev.squadx.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.jobrunr.jobs.lambdas.JobLambda;
import org.jobrunr.scheduling.JobScheduler;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.ZoneId;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AutopilotServiceTest {

    @Mock private AutopilotRepository autopilotRepository;
    @Mock private AutopilotRunRepository autopilotRunRepository;
    @Mock private ProjectRepository projectRepository;
    @Mock private SquadRepository squadRepository;
    @Mock private AgentRepository agentRepository;
    @Mock private OrganizationMemberRepository memberRepository;
    @Mock private JobScheduler jobScheduler;
    @Mock private AutopilotExecutor autopilotExecutor;

    @InjectMocks private AutopilotService autopilotService;

    private User currentUser;
    private Organization organization;
    private Project project;
    private Autopilot autopilot;

    @BeforeEach
    void setUp() {
        currentUser = User.builder().email("user@example.com").fullName("User").build();
        currentUser.setId(1L);

        organization = Organization.builder().name("Org").slug("org").build();
        organization.setId(100L);

        project = Project.builder().name("Proj").slug("proj").organization(organization).build();
        project.setId(7L);

        autopilot = Autopilot.builder()
                .name("Daily Standup")
                .cronExpression("0 9 * * *")
                .timezone("UTC")
                .executionMode(AutopilotExecutionMode.CREATE_TASK)
                .organization(organization)
                .project(project)
                .taskTitle("Run standup")
                .enabled(true)
                .runCount(0)
                .createdBy(currentUser)
                .build();
        autopilot.setId(1L);
    }

    @Test
    @DisplayName("create() persists an enabled autopilot and schedules a recurring JobRunr job")
    void createSchedulesJob() {
        AutopilotRequest request = AutopilotRequest.builder()
                .name("Daily Standup")
                .cronExpression("0 9 * * *")
                .projectId(7L)
                .taskTitle("Run standup")
                .enabled(true)
                .build();

        when(projectRepository.findById(7L)).thenReturn(Optional.of(project));
        when(memberRepository.existsByOrganizationIdAndUserId(100L, 1L)).thenReturn(true);
        when(autopilotRepository.save(any(Autopilot.class))).thenAnswer(i -> {
            Autopilot saved = i.getArgument(0);
            saved.setId(1L);
            return saved;
        });

        AutopilotResponse response = autopilotService.create(request, currentUser);

        assertThat(response.getName()).isEqualTo("Daily Standup");
        assertThat(response.getOrganizationId()).isEqualTo(100L);
        verify(jobScheduler).scheduleRecurrently(eq("autopilot-1"), eq("0 9 * * *"), any(ZoneId.class), any(JobLambda.class));
        verify(jobScheduler, never()).deleteRecurringJob(anyString());
    }

    @Test
    @DisplayName("create() with enabled=false does not schedule a job")
    void createDisabledDoesNotSchedule() {
        AutopilotRequest request = AutopilotRequest.builder()
                .name("Off")
                .cronExpression("0 9 * * *")
                .projectId(7L)
                .taskTitle("x")
                .enabled(false)
                .build();

        when(projectRepository.findById(7L)).thenReturn(Optional.of(project));
        when(memberRepository.existsByOrganizationIdAndUserId(100L, 1L)).thenReturn(true);
        when(autopilotRepository.save(any(Autopilot.class))).thenAnswer(i -> {
            Autopilot saved = i.getArgument(0);
            saved.setId(2L);
            return saved;
        });

        autopilotService.create(request, currentUser);

        verify(jobScheduler, never()).scheduleRecurrently(anyString(), anyString(), any(ZoneId.class), any(JobLambda.class));
        verify(jobScheduler).deleteRecurringJob("autopilot-2");
    }

    @Test
    @DisplayName("create() throws ForbiddenException when user lacks org access")
    void createForbidden() {
        AutopilotRequest request = AutopilotRequest.builder()
                .name("x").cronExpression("0 9 * * *").projectId(7L).taskTitle("x").build();

        when(projectRepository.findById(7L)).thenReturn(Optional.of(project));
        when(memberRepository.existsByOrganizationIdAndUserId(100L, 1L)).thenReturn(false);

        assertThatThrownBy(() -> autopilotService.create(request, currentUser))
                .isInstanceOf(ForbiddenException.class);

        verify(autopilotRepository, never()).save(any());
    }

    @Test
    @DisplayName("getById() throws when autopilot does not exist")
    void getByIdNotFound() {
        when(autopilotRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> autopilotService.getById(999L, currentUser))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    @DisplayName("toggle() disables an enabled autopilot and removes its schedule")
    void toggleDisables() {
        when(autopilotRepository.findById(1L)).thenReturn(Optional.of(autopilot));
        when(memberRepository.existsByOrganizationIdAndUserId(100L, 1L)).thenReturn(true);
        when(autopilotRepository.save(any(Autopilot.class))).thenAnswer(i -> i.getArgument(0));

        AutopilotResponse response = autopilotService.toggle(1L, currentUser);

        assertThat(response.getEnabled()).isFalse();
        verify(jobScheduler).deleteRecurringJob("autopilot-1");
    }

    @Test
    @DisplayName("delete() unschedules the job and removes the entity")
    void deleteRemovesJobAndEntity() {
        when(autopilotRepository.findById(1L)).thenReturn(Optional.of(autopilot));
        when(memberRepository.existsByOrganizationIdAndUserId(100L, 1L)).thenReturn(true);

        autopilotService.delete(1L, currentUser);

        verify(jobScheduler).deleteRecurringJob("autopilot-1");
        verify(autopilotRepository).delete(autopilot);
    }

    @Test
    @DisplayName("runNow() delegates to the executor with a MANUAL trigger")
    void runNowDelegatesToExecutor() {
        when(autopilotRepository.findById(1L)).thenReturn(Optional.of(autopilot));
        when(memberRepository.existsByOrganizationIdAndUserId(100L, 1L)).thenReturn(true);
        AutopilotRun run = AutopilotRun.builder()
                .autopilot(autopilot)
                .triggerType(AutopilotTriggerType.MANUAL)
                .status(AutopilotRunStatus.SUCCESS)
                .build();
        run.setId(42L);
        when(autopilotExecutor.execute(1L, AutopilotTriggerType.MANUAL)).thenReturn(run);

        AutopilotRunResponse response = autopilotService.runNow(1L, currentUser);

        assertThat(response).isNotNull();
        assertThat(response.getStatus()).isEqualTo(AutopilotRunStatus.SUCCESS);
        verify(autopilotExecutor).execute(1L, AutopilotTriggerType.MANUAL);
    }
}
