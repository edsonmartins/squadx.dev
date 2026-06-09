package dev.squadx.service;

import dev.squadx.dto.execution.ExecutionRequest;
import dev.squadx.dto.execution.ExecutionResponse;
import dev.squadx.event.ExecutionCompletedEvent;
import dev.squadx.exception.BadRequestException;
import dev.squadx.exception.ForbiddenException;
import dev.squadx.exception.ResourceNotFoundException;
import dev.squadx.integration.BrainSentryClient;
import dev.squadx.model.*;
import dev.squadx.model.enums.*;
import dev.squadx.repository.*;
import org.springframework.context.ApplicationEventPublisher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ExecutionServiceTest {

    @Mock
    private ExecutionRepository executionRepository;

    @Mock
    private TaskRepository taskRepository;

    @Mock
    private AgentRepository agentRepository;

    @Mock
    private OrganizationMemberRepository memberRepository;

    @Mock
    private WebSocketEventService webSocketEventService;

    @Mock
    private BrainSentryClient brainSentryClient;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @Mock
    private SquadAgentResolver squadAgentResolver;

    @InjectMocks
    private ExecutionService executionService;

    private User testUser;
    private Organization testOrg;
    private Project testProject;
    private Task testTask;
    private Agent testAgent;
    private Execution testExecution;

    @BeforeEach
    void setUp() {
        testUser = User.builder()
                .email("test@example.com")
                .fullName("Test User")
                .role(UserRole.USER)
                .isActive(true)
                .build();
        testUser.setId(1L);

        testOrg = Organization.builder()
                .name("Org")
                .slug("org")
                .build();
        testOrg.setId(1L);

        testProject = Project.builder()
                .name("Proj")
                .slug("proj")
                .organization(testOrg)
                .build();
        testProject.setId(1L);

        testTask = Task.builder()
                .title("Task")
                .project(testProject)
                .status(TaskStatus.TODO)
                .build();
        testTask.setId(1L);

        testAgent = Agent.builder()
                .name("Agent")
                .agentType(AgentType.BACKEND)
                .squad(null)
                .isActive(true)
                .build();
        testAgent.setId(1L);

        testExecution = Execution.builder()
                .task(testTask)
                .agent(testAgent)
                .status(ExecutionStatus.PENDING)
                .logs(new ArrayList<>())
                .build();
        testExecution.setId(1L);

        lenient().when(brainSentryClient.isEnabled()).thenReturn(false);
    }

    @Nested
    @DisplayName("startExecution()")
    class StartExecution {

        @Test
        @DisplayName("should start execution with explicit agent ID")
        void shouldStartExecutionWithExplicitAgentId() {
            ExecutionRequest request = ExecutionRequest.builder()
                    .taskId(1L)
                    .agentId(1L)
                    .build();

            when(taskRepository.findById(1L)).thenReturn(Optional.of(testTask));
            when(memberRepository.existsByOrganizationIdAndUserId(1L, 1L)).thenReturn(true);
            when(executionRepository.findByTaskIdAndStatus(1L, ExecutionStatus.RUNNING))
                    .thenReturn(Collections.emptyList());
            when(agentRepository.findById(1L)).thenReturn(Optional.of(testAgent));
            when(executionRepository.save(any(Execution.class))).thenAnswer(invocation -> {
                Execution saved = invocation.getArgument(0);
                saved.setId(1L);
                return saved;
            });
            when(taskRepository.save(any(Task.class))).thenReturn(testTask);

            ExecutionResponse response = executionService.startExecution(request, testUser);

            assertThat(response).isNotNull();
            assertThat(response.getTaskId()).isEqualTo(1L);
            assertThat(response.getAgentId()).isEqualTo(1L);
            assertThat(response.getStatus()).isEqualTo(ExecutionStatus.PENDING);

            verify(executionRepository).save(any(Execution.class));
            verify(taskRepository).save(any(Task.class));
            verify(webSocketEventService).sendExecutionStarted(eq(1L), eq(1L), any());
            verify(webSocketEventService).sendTaskAssignedToUser(eq("test@example.com"), eq(1L), anyMap());
        }

        @Test
        @DisplayName("should start execution with task's assigned agent when no agentId provided")
        void shouldStartExecutionWithTaskAssignedAgent() {
            testTask.setAssignedAgent(testAgent);

            ExecutionRequest request = ExecutionRequest.builder()
                    .taskId(1L)
                    .build();

            when(taskRepository.findById(1L)).thenReturn(Optional.of(testTask));
            when(memberRepository.existsByOrganizationIdAndUserId(1L, 1L)).thenReturn(true);
            when(executionRepository.findByTaskIdAndStatus(1L, ExecutionStatus.RUNNING))
                    .thenReturn(Collections.emptyList());
            when(executionRepository.save(any(Execution.class))).thenAnswer(invocation -> {
                Execution saved = invocation.getArgument(0);
                saved.setId(1L);
                return saved;
            });
            when(taskRepository.save(any(Task.class))).thenReturn(testTask);

            ExecutionResponse response = executionService.startExecution(request, testUser);

            assertThat(response).isNotNull();
            assertThat(response.getAgentName()).isEqualTo("Agent");

            verify(agentRepository, never()).findById(any());
        }

        @Test
        @DisplayName("should throw when task not found")
        void shouldThrowWhenTaskNotFound() {
            ExecutionRequest request = ExecutionRequest.builder()
                    .taskId(99L)
                    .build();

            when(taskRepository.findById(99L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> executionService.startExecution(request, testUser))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessage("Task not found");
        }

        @Test
        @DisplayName("should throw when agent not found")
        void shouldThrowWhenAgentNotFound() {
            ExecutionRequest request = ExecutionRequest.builder()
                    .taskId(1L)
                    .agentId(99L)
                    .build();

            when(taskRepository.findById(1L)).thenReturn(Optional.of(testTask));
            when(memberRepository.existsByOrganizationIdAndUserId(1L, 1L)).thenReturn(true);
            when(executionRepository.findByTaskIdAndStatus(1L, ExecutionStatus.RUNNING))
                    .thenReturn(Collections.emptyList());
            when(agentRepository.findById(99L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> executionService.startExecution(request, testUser))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessage("Agent not found");
        }

        @Test
        @DisplayName("should throw when no agent specified and task has no assigned agent")
        void shouldThrowWhenNoAgentSpecified() {
            ExecutionRequest request = ExecutionRequest.builder()
                    .taskId(1L)
                    .build();

            when(taskRepository.findById(1L)).thenReturn(Optional.of(testTask));
            when(memberRepository.existsByOrganizationIdAndUserId(1L, 1L)).thenReturn(true);
            when(executionRepository.findByTaskIdAndStatus(1L, ExecutionStatus.RUNNING))
                    .thenReturn(Collections.emptyList());

            assertThatThrownBy(() -> executionService.startExecution(request, testUser))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessage("No agent specified for execution");
        }

        @Test
        @DisplayName("should throw when task already has a running execution")
        void shouldThrowWhenAlreadyRunning() {
            ExecutionRequest request = ExecutionRequest.builder()
                    .taskId(1L)
                    .agentId(1L)
                    .build();

            when(taskRepository.findById(1L)).thenReturn(Optional.of(testTask));
            when(memberRepository.existsByOrganizationIdAndUserId(1L, 1L)).thenReturn(true);
            when(executionRepository.findByTaskIdAndStatus(1L, ExecutionStatus.RUNNING))
                    .thenReturn(List.of(testExecution));

            assertThatThrownBy(() -> executionService.startExecution(request, testUser))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessage("Task already has a running execution");
        }

        @Test
        @DisplayName("should set task status to IN_PROGRESS")
        void shouldSetTaskStatusToInProgress() {
            ExecutionRequest request = ExecutionRequest.builder()
                    .taskId(1L)
                    .agentId(1L)
                    .build();

            when(taskRepository.findById(1L)).thenReturn(Optional.of(testTask));
            when(memberRepository.existsByOrganizationIdAndUserId(1L, 1L)).thenReturn(true);
            when(executionRepository.findByTaskIdAndStatus(1L, ExecutionStatus.RUNNING))
                    .thenReturn(Collections.emptyList());
            when(agentRepository.findById(1L)).thenReturn(Optional.of(testAgent));
            when(executionRepository.save(any(Execution.class))).thenAnswer(invocation -> {
                Execution saved = invocation.getArgument(0);
                saved.setId(1L);
                return saved;
            });
            when(taskRepository.save(any(Task.class))).thenAnswer(invocation -> invocation.getArgument(0));

            executionService.startExecution(request, testUser);

            verify(taskRepository).save(argThat(task -> task.getStatus() == TaskStatus.IN_PROGRESS));
        }

        @Test
        @DisplayName("should continue execution start when BrainSentry session creation fails")
        void shouldContinueWhenBrainSentryStartFails() {
            ExecutionRequest request = ExecutionRequest.builder()
                    .taskId(1L)
                    .agentId(1L)
                    .build();

            when(taskRepository.findById(1L)).thenReturn(Optional.of(testTask));
            when(memberRepository.existsByOrganizationIdAndUserId(1L, 1L)).thenReturn(true);
            when(executionRepository.findByTaskIdAndStatus(1L, ExecutionStatus.RUNNING))
                    .thenReturn(Collections.emptyList());
            when(agentRepository.findById(1L)).thenReturn(Optional.of(testAgent));
            when(executionRepository.save(any(Execution.class))).thenAnswer(invocation -> {
                Execution saved = invocation.getArgument(0);
                saved.setId(1L);
                return saved;
            });
            when(taskRepository.save(any(Task.class))).thenReturn(testTask);
            when(brainSentryClient.isEnabled()).thenReturn(true);
            when(brainSentryClient.startExecutionSession(any(), any(), any(), any(), any()))
                    .thenThrow(new RuntimeException("brainsentry down"));

            ExecutionResponse response = executionService.startExecution(request, testUser);

            assertThat(response).isNotNull();
            assertThat(response.getBrainSentrySessionId()).isNull();
            verify(webSocketEventService).sendTaskAssignedToUser(eq("test@example.com"), eq(1L), anyMap());
        }
    }

    @Nested
    @DisplayName("updateStatus()")
    class UpdateStatus {

        @Test
        @DisplayName("should set startedAt when status changes to RUNNING")
        void shouldSetStartedAtWhenRunning() {
            testExecution.setStartedAt(null);

            when(executionRepository.findById(1L)).thenReturn(Optional.of(testExecution));
            when(memberRepository.existsByOrganizationIdAndUserId(1L, 1L)).thenReturn(true);
            when(executionRepository.save(any(Execution.class))).thenAnswer(invocation -> invocation.getArgument(0));

            ExecutionResponse response = executionService.updateStatus(1L, ExecutionStatus.RUNNING, testUser);

            assertThat(response).isNotNull();
            assertThat(testExecution.getStartedAt()).isNotNull();

            verify(executionRepository).save(any(Execution.class));
        }

        @Test
        @DisplayName("should set completedAt and task to IN_REVIEW when completed")
        void shouldSetCompletedAtAndTaskInReviewWhenCompleted() {
            testExecution.setStartedAt(Instant.now());

            when(executionRepository.findById(1L)).thenReturn(Optional.of(testExecution));
            when(memberRepository.existsByOrganizationIdAndUserId(1L, 1L)).thenReturn(true);
            when(executionRepository.save(any(Execution.class))).thenAnswer(invocation -> invocation.getArgument(0));
            when(taskRepository.save(any(Task.class))).thenReturn(testTask);

            executionService.updateStatus(1L, ExecutionStatus.COMPLETED, testUser);

            assertThat(testExecution.getCompletedAt()).isNotNull();
            verify(taskRepository).save(argThat(task -> task.getStatus() == TaskStatus.IN_REVIEW));
        }

        @Test
        @DisplayName("should continue completion when BrainSentry completion fails")
        void shouldContinueWhenBrainSentryCompletionFails() {
            testExecution.setStartedAt(Instant.now());
            testExecution.setBrainSentrySessionId("bs-session-1");

            when(executionRepository.findById(1L)).thenReturn(Optional.of(testExecution));
            when(memberRepository.existsByOrganizationIdAndUserId(1L, 1L)).thenReturn(true);
            when(executionRepository.save(any(Execution.class))).thenAnswer(invocation -> invocation.getArgument(0));
            when(taskRepository.save(any(Task.class))).thenReturn(testTask);
            doThrow(new RuntimeException("brainsentry down")).when(brainSentryClient)
                    .completeExecutionSession(any(), any(), any(), any(), any(), any(), any(), any());

            ExecutionResponse response = executionService.updateStatus(1L, ExecutionStatus.COMPLETED, testUser);

            assertThat(response).isNotNull();
            assertThat(testExecution.getStatus()).isEqualTo(ExecutionStatus.COMPLETED);
            verify(taskRepository).save(argThat(task -> task.getStatus() == TaskStatus.IN_REVIEW));
            verify(eventPublisher).publishEvent(any(ExecutionCompletedEvent.class));
        }

        @Test
        @DisplayName("should set task to BLOCKED when execution fails")
        void shouldSetTaskToBlockedWhenFailed() {
            testExecution.setStartedAt(Instant.now());

            when(executionRepository.findById(1L)).thenReturn(Optional.of(testExecution));
            when(memberRepository.existsByOrganizationIdAndUserId(1L, 1L)).thenReturn(true);
            when(executionRepository.save(any(Execution.class))).thenAnswer(invocation -> invocation.getArgument(0));
            when(taskRepository.save(any(Task.class))).thenReturn(testTask);

            executionService.updateStatus(1L, ExecutionStatus.FAILED, testUser);

            verify(taskRepository).save(argThat(task -> task.getStatus() == TaskStatus.BLOCKED));
        }

        @Test
        @DisplayName("should throw when execution not found")
        void shouldThrowWhenExecutionNotFound() {
            when(executionRepository.findById(99L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> executionService.updateStatus(99L, ExecutionStatus.RUNNING, testUser))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessage("Execution not found");
        }

        @Test
        @DisplayName("should publish completion event on status change")
        void shouldPublishCompletionEventOnStatusChange() {
            testExecution.setStartedAt(Instant.now());

            when(executionRepository.findById(1L)).thenReturn(Optional.of(testExecution));
            when(memberRepository.existsByOrganizationIdAndUserId(1L, 1L)).thenReturn(true);
            when(executionRepository.save(any(Execution.class))).thenAnswer(invocation -> invocation.getArgument(0));
            when(taskRepository.save(any(Task.class))).thenReturn(testTask);

            executionService.updateStatus(1L, ExecutionStatus.COMPLETED, testUser);

            verify(eventPublisher).publishEvent(any(ExecutionCompletedEvent.class));
        }
    }

    @Nested
    @DisplayName("handleDaemonTaskUpdate()")
    class HandleDaemonTaskUpdate {

        @Test
        @DisplayName("should mark execution running on task_status running")
        void shouldMarkExecutionRunning() {
            when(executionRepository.findTopByTaskIdOrderByCreatedAtDesc(1L)).thenReturn(Optional.of(testExecution));
            when(executionRepository.save(any(Execution.class))).thenAnswer(invocation -> invocation.getArgument(0));

            executionService.handleDaemonTaskUpdate(1L, Map.of(
                    "type", "task_status",
                    "status", "running"
            ));

            assertThat(testExecution.getStatus()).isEqualTo(ExecutionStatus.RUNNING);
            assertThat(testExecution.getStartedAt()).isNotNull();
        }

        @Test
        @DisplayName("should complete execution from daemon payload")
        void shouldCompleteExecutionFromDaemonPayload() {
            when(executionRepository.findTopByTaskIdOrderByCreatedAtDesc(1L)).thenReturn(Optional.of(testExecution));
            when(executionRepository.save(any(Execution.class))).thenAnswer(invocation -> invocation.getArgument(0));
            when(taskRepository.save(any(Task.class))).thenReturn(testTask);

            executionService.handleDaemonTaskUpdate(1L, Map.of(
                    "type", "task_completed",
                    "result", Map.of(
                            "final_result", "done",
                            "git_branch", "feat/test",
                            "git_commit", "abc123",
                            "total_input_tokens", 12,
                            "total_output_tokens", 34,
                            "total_cost", 0.56
                    )
            ));

            assertThat(testExecution.getStatus()).isEqualTo(ExecutionStatus.COMPLETED);
            assertThat(testExecution.getResult()).isEqualTo("done");
            assertThat(testExecution.getGitBranch()).isEqualTo("feat/test");
            assertThat(testExecution.getGitCommit()).isEqualTo("abc123");
            verify(taskRepository).save(argThat(task -> task.getStatus() == TaskStatus.IN_REVIEW));
            verify(eventPublisher).publishEvent(any(ExecutionCompletedEvent.class));
        }

        @Test
        @DisplayName("should reset task to TODO when daemon rejects task")
        void shouldResetTaskToTodoWhenRejected() {
            when(executionRepository.findTopByTaskIdOrderByCreatedAtDesc(1L)).thenReturn(Optional.of(testExecution));
            when(executionRepository.save(any(Execution.class))).thenAnswer(invocation -> invocation.getArgument(0));
            when(taskRepository.save(any(Task.class))).thenReturn(testTask);

            executionService.handleDaemonTaskUpdate(1L, Map.of(
                    "type", "task_rejected",
                    "reason", "Max concurrent tasks reached"
            ));

            assertThat(testExecution.getStatus()).isEqualTo(ExecutionStatus.FAILED);
            assertThat(testExecution.getErrorMessage()).isEqualTo("Max concurrent tasks reached");
            verify(taskRepository).save(argThat(task -> task.getStatus() == TaskStatus.TODO));
            verify(eventPublisher).publishEvent(any(ExecutionCompletedEvent.class));
        }
    }

    @Nested
    @DisplayName("cancel()")
    class Cancel {

        @Test
        @DisplayName("should cancel a pending execution")
        void shouldCancelPendingExecution() {
            testExecution.setStatus(ExecutionStatus.PENDING);

            when(executionRepository.findById(1L)).thenReturn(Optional.of(testExecution));
            when(memberRepository.existsByOrganizationIdAndUserId(1L, 1L)).thenReturn(true);
            when(executionRepository.save(any(Execution.class))).thenAnswer(invocation -> invocation.getArgument(0));

            ExecutionResponse response = executionService.cancel(1L, testUser);

            assertThat(response).isNotNull();
            assertThat(testExecution.getStatus()).isEqualTo(ExecutionStatus.CANCELLED);
            assertThat(testExecution.getCompletedAt()).isNotNull();

            verify(executionRepository).save(any(Execution.class));
        }

        @Test
        @DisplayName("should cancel a running execution")
        void shouldCancelRunningExecution() {
            testExecution.setStatus(ExecutionStatus.RUNNING);

            when(executionRepository.findById(1L)).thenReturn(Optional.of(testExecution));
            when(memberRepository.existsByOrganizationIdAndUserId(1L, 1L)).thenReturn(true);
            when(executionRepository.save(any(Execution.class))).thenAnswer(invocation -> invocation.getArgument(0));

            ExecutionResponse response = executionService.cancel(1L, testUser);

            assertThat(response).isNotNull();
            assertThat(testExecution.getStatus()).isEqualTo(ExecutionStatus.CANCELLED);
        }

        @Test
        @DisplayName("should throw when cancelling a finished execution")
        void shouldThrowWhenCancellingFinishedExecution() {
            testExecution.setStatus(ExecutionStatus.COMPLETED);

            when(executionRepository.findById(1L)).thenReturn(Optional.of(testExecution));
            when(memberRepository.existsByOrganizationIdAndUserId(1L, 1L)).thenReturn(true);

            assertThatThrownBy(() -> executionService.cancel(1L, testUser))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessage("Cannot cancel a finished execution");

            verify(executionRepository, never()).save(any(Execution.class));
        }
    }

    @Nested
    @DisplayName("addLog()")
    class AddLog {

        @Test
        @DisplayName("should add log to execution")
        void shouldAddLogToExecution() {
            when(executionRepository.findById(1L)).thenReturn(Optional.of(testExecution));
            when(executionRepository.save(any(Execution.class))).thenReturn(testExecution);

            executionService.addLog(1L, "INFO", "Test log message", "{\"key\":\"value\"}");

            assertThat(testExecution.getLogs()).hasSize(1);
            assertThat(testExecution.getLogs().get(0).getMessage()).isEqualTo("Test log message");
            assertThat(testExecution.getLogs().get(0).getLevel()).isEqualTo("INFO");

            verify(executionRepository).save(any(Execution.class));
            verify(webSocketEventService).sendExecutionLog(1L, "INFO", "Test log message");
        }
    }

    @Test
    void getPendingAssignmentsReturnsPayloadsForMemberOrgs() {
        when(executionRepository.findPendingForUser(eq(ExecutionStatus.PENDING), eq(1L), any()))
                .thenReturn(java.util.List.of(testExecution));

        var pending = executionService.getPendingAssignments(testUser);

        assertThat(pending).hasSize(1);
        assertThat(pending.get(0).get("task_id")).isEqualTo(1L);
        assertThat(pending.get(0)).containsKey("task");
    }

    @Test
    void claimPendingReturnsTrueWhenRowUpdated() {
        when(executionRepository.findById(1L)).thenReturn(java.util.Optional.of(testExecution));
        when(memberRepository.existsByOrganizationIdAndUserId(1L, 1L)).thenReturn(true);
        when(executionRepository.claimExecution(eq(1L), eq(ExecutionStatus.RUNNING),
                eq(ExecutionStatus.PENDING), any())).thenReturn(1);

        assertThat(executionService.claimPending(1L, testUser)).isTrue();
    }
}
