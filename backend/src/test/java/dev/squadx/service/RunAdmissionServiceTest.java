package dev.squadx.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.squadx.dto.execution.ExecutionRequest;
import dev.squadx.model.*;
import dev.squadx.model.enums.ExecutionStatus;
import dev.squadx.model.enums.FollowUpStatus;
import dev.squadx.model.enums.RunAdmissionAction;
import dev.squadx.repository.ExecutionRepository;
import dev.squadx.repository.FollowUpRequestRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RunAdmissionServiceTest {

    @Mock
    private ExecutionRepository executionRepository;

    @Mock
    private FollowUpRequestRepository followUpRequestRepository;

    private RunAdmissionService runAdmissionService;

    private User user;
    private Task task;
    private Execution active;

    @BeforeEach
    void setUp() {
        runAdmissionService = new RunAdmissionService(
                executionRepository, followUpRequestRepository, new ObjectMapper());

        Organization org = Organization.builder().name("Org").slug("org").build();
        org.setId(1L);
        Project project = Project.builder().name("Proj").slug("proj").organization(org).build();
        project.setId(1L);
        task = Task.builder().title("Task").project(project).build();
        task.setId(1L);

        Agent agent = Agent.builder().name("Agent").build();
        agent.setId(1L);
        active = Execution.builder().task(task).agent(agent).status(ExecutionStatus.RUNNING).build();
        active.setId(10L);

        user = User.builder().email("dev@example.com").build();
        user.setId(1L);
    }

    @Test
    @DisplayName("START when there is no duplicate and no active run")
    void startWhenClear() {
        ExecutionRequest request = ExecutionRequest.builder().taskId(1L).agentId(1L).build();
        when(executionRepository.findByTaskIdAndStatusIn(eq(1L), any())).thenReturn(List.of());

        RunAdmissionService.AdmissionResult result = runAdmissionService.admit(task, request, user);

        assertThat(result.action()).isEqualTo(RunAdmissionAction.START);
        assertThat(result.referencedExecution()).isNull();
        verify(followUpRequestRepository, never()).save(any());
    }

    @Test
    @DisplayName("DROP_DUPLICATE when the idempotency key already produced a run")
    void dropDuplicateOnRepeatedKey() {
        ExecutionRequest request = ExecutionRequest.builder()
                .taskId(1L).agentId(1L).idempotencyKey("key-1").build();
        when(executionRepository.findByTaskIdAndIdempotencyKey(1L, "key-1"))
                .thenReturn(Optional.of(active));

        RunAdmissionService.AdmissionResult result = runAdmissionService.admit(task, request, user);

        assertThat(result.action()).isEqualTo(RunAdmissionAction.DROP_DUPLICATE);
        assertThat(result.referencedExecution()).isSameAs(active);
        assertThat(result.decision().getActiveExecutionId()).isEqualTo(10L);
        // No active-run lookup needed once a duplicate is found.
        verify(executionRepository, never()).findByTaskIdAndStatusIn(anyLong(), any());
    }

    @Test
    @DisplayName("QUEUE_FOLLOW_UP and persist a follow-up when a run is already active")
    void queueFollowUpWhenActive() {
        ExecutionRequest request = ExecutionRequest.builder().taskId(1L).agentId(1L).build();
        when(executionRepository.findByTaskIdAndStatusIn(eq(1L), any())).thenReturn(List.of(active));
        when(followUpRequestRepository.save(any(FollowUpRequest.class))).thenAnswer(inv -> {
            FollowUpRequest fu = inv.getArgument(0);
            fu.setId(7L);
            return fu;
        });

        RunAdmissionService.AdmissionResult result = runAdmissionService.admit(task, request, user);

        assertThat(result.action()).isEqualTo(RunAdmissionAction.QUEUE_FOLLOW_UP);
        assertThat(result.followUp()).isNotNull();
        assertThat(result.followUp().getStatus()).isEqualTo(FollowUpStatus.PENDING);
        assertThat(result.followUp().getActiveExecutionId()).isEqualTo(10L);
        assertThat(result.decision().getFollowUpRequestId()).isEqualTo(7L);
        verify(followUpRequestRepository).save(any(FollowUpRequest.class));
    }

    @Test
    @DisplayName("A blank idempotency key does not trigger dedup")
    void blankKeyIsIgnored() {
        ExecutionRequest request = ExecutionRequest.builder()
                .taskId(1L).agentId(1L).idempotencyKey("   ").build();
        when(executionRepository.findByTaskIdAndStatusIn(eq(1L), any())).thenReturn(List.of());

        RunAdmissionService.AdmissionResult result = runAdmissionService.admit(task, request, user);

        assertThat(result.action()).isEqualTo(RunAdmissionAction.START);
        verify(executionRepository, never()).findByTaskIdAndIdempotencyKey(anyLong(), any());
    }
}
