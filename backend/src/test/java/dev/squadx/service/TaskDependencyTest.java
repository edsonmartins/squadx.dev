package dev.squadx.service;

import dev.squadx.dto.task.TaskResponse;
import dev.squadx.exception.BadRequestException;
import dev.squadx.exception.ResourceNotFoundException;
import dev.squadx.model.*;
import dev.squadx.model.enums.TaskPriority;
import dev.squadx.model.enums.TaskStatus;
import dev.squadx.model.enums.UserRole;
import dev.squadx.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TaskDependencyTest {

    @Mock
    private TaskRepository taskRepository;

    @Mock
    private ProjectRepository projectRepository;

    @Mock
    private AgentRepository agentRepository;

    @Mock
    private OrganizationMemberRepository memberRepository;

    @Mock
    private TaskDependencyRepository taskDependencyRepository;

    @Mock
    private ExecutionRepository executionRepository;

    @Mock
    private WebSocketEventService webSocketEventService;

    @InjectMocks
    private TaskService taskService;

    private User currentUser;
    private Organization organization;
    private Project project;
    private Task taskA;
    private Task taskB;
    private Task taskC;

    @BeforeEach
    void setUp() {
        currentUser = User.builder()
                .email("user@example.com")
                .password("encoded")
                .fullName("Test User")
                .role(UserRole.USER)
                .build();
        currentUser.setId(1L);

        organization = Organization.builder()
                .name("Test Org")
                .slug("test-org")
                .build();
        organization.setId(10L);

        project = Project.builder()
                .name("Test Project")
                .slug("test-project")
                .organization(organization)
                .build();
        project.setId(100L);

        taskA = Task.builder()
                .title("Task A")
                .description("First task")
                .status(TaskStatus.TODO)
                .priority(TaskPriority.MEDIUM)
                .project(project)
                .createdBy(currentUser)
                .tags(Set.of())
                .build();
        taskA.setId(1L);
        taskA.setCreatedAt(Instant.now());
        taskA.setUpdatedAt(Instant.now());

        taskB = Task.builder()
                .title("Task B")
                .description("Second task")
                .status(TaskStatus.TODO)
                .priority(TaskPriority.MEDIUM)
                .project(project)
                .createdBy(currentUser)
                .tags(Set.of())
                .build();
        taskB.setId(2L);
        taskB.setCreatedAt(Instant.now());
        taskB.setUpdatedAt(Instant.now());

        taskC = Task.builder()
                .title("Task C")
                .description("Third task")
                .status(TaskStatus.TODO)
                .priority(TaskPriority.MEDIUM)
                .project(project)
                .createdBy(currentUser)
                .tags(Set.of())
                .build();
        taskC.setId(3L);
        taskC.setCreatedAt(Instant.now());
        taskC.setUpdatedAt(Instant.now());

        lenient().when(executionRepository.findTopByTaskIdOrderByCreatedAtDesc(anyLong())).thenReturn(Optional.empty());
    }

    @Nested
    @DisplayName("addDependency()")
    class AddDependency {

        @Test
        @DisplayName("should add a dependency successfully")
        void shouldAddDependencySuccessfully() {
            when(taskRepository.findById(1L)).thenReturn(Optional.of(taskA));
            when(taskRepository.findById(2L)).thenReturn(Optional.of(taskB));
            when(memberRepository.existsByOrganizationIdAndUserId(10L, 1L)).thenReturn(true);
            when(taskDependencyRepository.existsByTaskIdAndDependsOnId(1L, 2L)).thenReturn(false);
            when(taskDependencyRepository.findByTaskId(2L)).thenReturn(Collections.emptyList());
            when(taskDependencyRepository.save(any(TaskDependency.class))).thenAnswer(invocation -> {
                TaskDependency dep = invocation.getArgument(0);
                dep.setId(100L);
                return dep;
            });
            // For mapToResponse calls
            when(taskDependencyRepository.findByTaskId(1L)).thenReturn(Collections.emptyList());
            when(taskDependencyRepository.findByDependsOnId(1L)).thenReturn(Collections.emptyList());
            when(taskRepository.countSubtasksByParentTaskId(anyLong())).thenReturn(0L);

            TaskDependency result = taskService.addDependency(1L, 2L, currentUser);

            assertThat(result).isNotNull();
            assertThat(result.getId()).isEqualTo(100L);
            assertThat(result.getTask()).isEqualTo(taskA);
            assertThat(result.getDependsOn()).isEqualTo(taskB);

            verify(taskDependencyRepository).save(any(TaskDependency.class));
            verify(webSocketEventService).sendTaskUpdated(eq(100L), eq(1L), any(TaskResponse.class));
        }

        @Test
        @DisplayName("should reject self-dependency")
        void shouldRejectSelfDependency() {
            assertThatThrownBy(() -> taskService.addDependency(1L, 1L, currentUser))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessage("A task cannot depend on itself");
        }

        @Test
        @DisplayName("should detect direct cycle (A->B, B->A)")
        void shouldDetectDirectCycle() {
            when(memberRepository.existsByOrganizationIdAndUserId(10L, 1L)).thenReturn(true);

            // B already depends on A (existing dependency)
            TaskDependency existingDep = TaskDependency.builder()
                    .id(100L)
                    .task(taskB)
                    .dependsOn(taskA)
                    .build();

            // When checking cycle: start from dependsOnId=1 (taskA), look for taskId=2 (taskB)
            // taskA's dependencies include taskB? No, B depends on A means findByTaskId(B) has A.
            // We want to add B->A. Cycle check: from dependsOnId=A(1), can we reach taskId=B(2)?
            // findByTaskId(1) = [] means A has no deps, so no cycle...
            // Actually: existing is B->A. We want to add B->A again? No, we want A->B when B->A exists.
            // Let me reconsider: addDependency(2, 1) means task 2 depends on task 1.
            // Existing: task 2 depends on task 1... that's a duplicate not a cycle.
            // For cycle: existing B->A (task B depends on A). Now add A->B (task A depends on B).
            // addDependency(taskId=1, dependsOnId=2) - A depends on B
            // Cycle check: from dependsOnId=2 (B), can we reach taskId=1 (A)?
            // findByTaskId(2) returns [dep(B->A)] so we see A(1) == taskId(1) -> cycle!

            when(taskRepository.findById(1L)).thenReturn(Optional.of(taskA));
            when(taskRepository.findById(2L)).thenReturn(Optional.of(taskB));
            when(taskDependencyRepository.existsByTaskIdAndDependsOnId(1L, 2L)).thenReturn(false);

            // Cycle detection BFS from dependsOnId=2: findByTaskId(2) returns B depends on A
            when(taskDependencyRepository.findByTaskId(2L)).thenReturn(List.of(existingDep));

            assertThatThrownBy(() -> taskService.addDependency(1L, 2L, currentUser))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessage("Adding this dependency would create a circular dependency");
        }

        @Test
        @DisplayName("should detect transitive cycle (A->B->C, C->A)")
        void shouldDetectTransitiveCycle() {
            // Existing: A->B (A depends on B), B->C (B depends on C)
            // Now add: C->A (C depends on A) => cycle C->A->B->C
            // addDependency(taskId=3, dependsOnId=1) - C depends on A
            // Cycle check: from dependsOnId=1 (A), can we reach taskId=3 (C)?
            // findByTaskId(1) = [A depends on B] -> visit B(2)
            // findByTaskId(2) = [B depends on C] -> visit C(3) == taskId(3) -> cycle!

            TaskDependency depAB = TaskDependency.builder().id(100L).task(taskA).dependsOn(taskB).build();
            TaskDependency depBC = TaskDependency.builder().id(101L).task(taskB).dependsOn(taskC).build();

            when(taskRepository.findById(3L)).thenReturn(Optional.of(taskC));
            when(taskRepository.findById(1L)).thenReturn(Optional.of(taskA));
            when(memberRepository.existsByOrganizationIdAndUserId(10L, 1L)).thenReturn(true);
            when(taskDependencyRepository.existsByTaskIdAndDependsOnId(3L, 1L)).thenReturn(false);

            when(taskDependencyRepository.findByTaskId(1L)).thenReturn(List.of(depAB));
            when(taskDependencyRepository.findByTaskId(2L)).thenReturn(List.of(depBC));

            assertThatThrownBy(() -> taskService.addDependency(3L, 1L, currentUser))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessage("Adding this dependency would create a circular dependency");
        }

        @Test
        @DisplayName("should reject duplicate dependency")
        void shouldRejectDuplicateDependency() {
            when(taskRepository.findById(1L)).thenReturn(Optional.of(taskA));
            when(taskRepository.findById(2L)).thenReturn(Optional.of(taskB));
            when(memberRepository.existsByOrganizationIdAndUserId(10L, 1L)).thenReturn(true);
            when(taskDependencyRepository.existsByTaskIdAndDependsOnId(1L, 2L)).thenReturn(true);

            assertThatThrownBy(() -> taskService.addDependency(1L, 2L, currentUser))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessage("Dependency already exists");
        }
    }

    @Nested
    @DisplayName("removeDependency()")
    class RemoveDependency {

        @Test
        @DisplayName("should remove a dependency successfully")
        void shouldRemoveDependencySuccessfully() {
            when(taskRepository.findById(1L)).thenReturn(Optional.of(taskA));
            when(memberRepository.existsByOrganizationIdAndUserId(10L, 1L)).thenReturn(true);
            when(taskDependencyRepository.existsByTaskIdAndDependsOnId(1L, 2L)).thenReturn(true);
            when(taskDependencyRepository.findByTaskId(1L)).thenReturn(Collections.emptyList());
            when(taskDependencyRepository.findByDependsOnId(1L)).thenReturn(Collections.emptyList());
            when(taskRepository.countSubtasksByParentTaskId(anyLong())).thenReturn(0L);

            taskService.removeDependency(1L, 2L, currentUser);

            verify(taskDependencyRepository).deleteByTaskIdAndDependsOnId(1L, 2L);
            verify(webSocketEventService).sendTaskUpdated(eq(100L), eq(1L), any(TaskResponse.class));
        }

        @Test
        @DisplayName("should throw when dependency not found")
        void shouldThrowWhenDependencyNotFound() {
            when(taskRepository.findById(1L)).thenReturn(Optional.of(taskA));
            when(memberRepository.existsByOrganizationIdAndUserId(10L, 1L)).thenReturn(true);
            when(taskDependencyRepository.existsByTaskIdAndDependsOnId(1L, 999L)).thenReturn(false);

            assertThatThrownBy(() -> taskService.removeDependency(1L, 999L, currentUser))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessage("Dependency not found");
        }
    }

    @Nested
    @DisplayName("getBlockers()")
    class GetBlockers {

        @Test
        @DisplayName("should return only non-DONE blocking tasks")
        void shouldReturnOnlyNonDoneBlockers() {
            // Task A depends on B (TODO) and C (DONE)
            // Only B should be returned as a blocker
            taskB.setStatus(TaskStatus.TODO);
            taskC.setStatus(TaskStatus.DONE);
            taskC.setCompletedAt(Instant.now());

            TaskDependency depAB = TaskDependency.builder().id(100L).task(taskA).dependsOn(taskB).build();
            TaskDependency depAC = TaskDependency.builder().id(101L).task(taskA).dependsOn(taskC).build();

            when(taskRepository.findById(1L)).thenReturn(Optional.of(taskA));
            when(memberRepository.existsByOrganizationIdAndUserId(10L, 1L)).thenReturn(true);
            when(taskDependencyRepository.findByTaskId(1L)).thenReturn(List.of(depAB, depAC));
            // mapToResponse for taskB
            when(taskDependencyRepository.findByDependsOnId(2L)).thenReturn(Collections.emptyList());
            when(taskDependencyRepository.findByTaskId(2L)).thenReturn(Collections.emptyList());
            when(taskRepository.countSubtasksByParentTaskId(2L)).thenReturn(0L);

            List<TaskResponse> blockers = taskService.getBlockers(1L, currentUser);

            assertThat(blockers).hasSize(1);
            assertThat(blockers.get(0).getId()).isEqualTo(2L);
            assertThat(blockers.get(0).getTitle()).isEqualTo("Task B");
        }

        @Test
        @DisplayName("should return empty list when all dependencies are DONE")
        void shouldReturnEmptyWhenAllDone() {
            taskB.setStatus(TaskStatus.DONE);

            TaskDependency depAB = TaskDependency.builder().id(100L).task(taskA).dependsOn(taskB).build();

            when(taskRepository.findById(1L)).thenReturn(Optional.of(taskA));
            when(memberRepository.existsByOrganizationIdAndUserId(10L, 1L)).thenReturn(true);
            when(taskDependencyRepository.findByTaskId(1L)).thenReturn(List.of(depAB));

            List<TaskResponse> blockers = taskService.getBlockers(1L, currentUser);

            assertThat(blockers).isEmpty();
        }
    }

    @Nested
    @DisplayName("auto-unblock on completion")
    class AutoUnblock {

        @Test
        @DisplayName("should notify dependents when task is completed and they become unblocked")
        void shouldNotifyDependentsOnCompletion() {
            // Task A depends on B. When B is completed, A should get an unblock notification.
            taskB.setStatus(TaskStatus.IN_PROGRESS);
            taskB.setStartedAt(Instant.now());

            TaskDependency depAB = TaskDependency.builder().id(100L).task(taskA).dependsOn(taskB).build();

            when(taskRepository.findById(2L)).thenReturn(Optional.of(taskB));
            when(memberRepository.existsByOrganizationIdAndUserId(10L, 1L)).thenReturn(true);
            when(taskRepository.save(any(Task.class))).thenAnswer(inv -> inv.getArgument(0));

            // For mapToResponse of taskB (the completed task)
            when(taskDependencyRepository.findByTaskId(2L)).thenReturn(Collections.emptyList());
            when(taskDependencyRepository.findByDependsOnId(2L)).thenReturn(List.of(depAB));
            when(taskRepository.countSubtasksByParentTaskId(2L)).thenReturn(0L);

            // For notifyDependentsUnblocked: find tasks that depend on B
            // findByTaskId(1) for checking remaining blockers of A
            when(taskDependencyRepository.findByTaskId(1L)).thenReturn(List.of(depAB));

            // After B is DONE, depAB's dependsOn (B) status is DONE, so A is unblocked
            // For mapToResponse of taskA
            when(taskDependencyRepository.findByDependsOnId(1L)).thenReturn(Collections.emptyList());
            when(taskRepository.countSubtasksByParentTaskId(1L)).thenReturn(0L);

            taskService.updateStatus(2L, TaskStatus.DONE, currentUser);

            // Verify the task B was updated
            verify(webSocketEventService).sendTaskUpdated(eq(100L), eq(2L), any(TaskResponse.class));
            // Verify the unblock notification for task A
            verify(webSocketEventService).sendTaskUpdated(eq(100L), eq(1L), any(TaskResponse.class));
        }
    }
}
