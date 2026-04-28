package dev.squadx.service;

import dev.squadx.dto.common.PageResponse;
import dev.squadx.dto.task.TaskRequest;
import dev.squadx.dto.task.TaskResponse;
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
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

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
class TaskServiceTest {

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
    private Task task;

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

        task = Task.builder()
                .title("Test Task")
                .description("Task description")
                .status(TaskStatus.TODO)
                .priority(TaskPriority.MEDIUM)
                .project(project)
                .createdBy(currentUser)
                .tags(Set.of("backend"))
                .build();
        task.setId(1000L);
        task.setCreatedAt(Instant.now());
        task.setUpdatedAt(Instant.now());

        // Default stubs for dependency repository (used by mapToResponse)
        lenient().when(taskDependencyRepository.findByTaskId(anyLong())).thenReturn(Collections.emptyList());
        lenient().when(taskDependencyRepository.findByDependsOnId(anyLong())).thenReturn(Collections.emptyList());
        lenient().when(executionRepository.findTopByTaskIdOrderByCreatedAtDesc(anyLong())).thenReturn(Optional.empty());
    }

    @Nested
    @DisplayName("create()")
    class Create {

        @Test
        @DisplayName("should create a task successfully")
        void shouldCreateTask() {
            TaskRequest request = TaskRequest.builder()
                    .title("New Task")
                    .description("New task description")
                    .projectId(100L)
                    .priority(TaskPriority.HIGH)
                    .storyPoints(5)
                    .tags(Set.of("api"))
                    .build();

            when(projectRepository.findById(100L)).thenReturn(Optional.of(project));
            when(memberRepository.existsByOrganizationIdAndUserId(10L, 1L)).thenReturn(true);
            when(taskRepository.save(any(Task.class))).thenAnswer(invocation -> {
                Task saved = invocation.getArgument(0);
                saved.setId(1001L);
                saved.setCreatedAt(Instant.now());
                saved.setUpdatedAt(Instant.now());
                return saved;
            });
            when(taskRepository.countSubtasksByParentTaskId(anyLong())).thenReturn(0L);

            TaskResponse response = taskService.create(request, currentUser);

            assertThat(response).isNotNull();
            assertThat(response.getTitle()).isEqualTo("New Task");
            assertThat(response.getDescription()).isEqualTo("New task description");
            assertThat(response.getStatus()).isEqualTo(TaskStatus.TODO);
            assertThat(response.getPriority()).isEqualTo(TaskPriority.HIGH);
            assertThat(response.getProjectId()).isEqualTo(100L);

            verify(taskRepository).save(any(Task.class));
            verify(webSocketEventService).sendTaskCreated(eq(100L), any(TaskResponse.class));
        }

        @Test
        @DisplayName("should include latest execution context when available")
        void shouldIncludeLatestExecutionContext() {
            TaskRequest request = TaskRequest.builder()
                    .title("New Task")
                    .projectId(100L)
                    .build();

            Execution execution = Execution.builder()
                    .task(task)
                    .agent(Agent.builder().name("Builder").build())
                    .brainSentrySessionId("bs-session-123")
                    .build();
            execution.setId(900L);

            when(projectRepository.findById(100L)).thenReturn(Optional.of(project));
            when(memberRepository.existsByOrganizationIdAndUserId(10L, 1L)).thenReturn(true);
            when(taskRepository.save(any(Task.class))).thenAnswer(invocation -> {
                Task saved = invocation.getArgument(0);
                saved.setId(1001L);
                saved.setCreatedAt(Instant.now());
                saved.setUpdatedAt(Instant.now());
                return saved;
            });
            when(taskRepository.countSubtasksByParentTaskId(anyLong())).thenReturn(0L);
            when(executionRepository.findTopByTaskIdOrderByCreatedAtDesc(1001L)).thenReturn(Optional.of(execution));

            TaskResponse response = taskService.create(request, currentUser);

            assertThat(response.getExecutionId()).isEqualTo(900L);
            assertThat(response.getBrainSentrySessionId()).isEqualTo("bs-session-123");
        }

        @Test
        @DisplayName("should throw ResourceNotFoundException when project not found")
        void shouldThrowWhenProjectNotFound() {
            TaskRequest request = TaskRequest.builder()
                    .title("New Task")
                    .projectId(999L)
                    .build();

            when(projectRepository.findById(999L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> taskService.create(request, currentUser))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessage("Project not found");
        }
    }

    @Nested
    @DisplayName("update()")
    class Update {

        @Test
        @DisplayName("should update task fields successfully")
        void shouldUpdateTask() {
            TaskRequest request = TaskRequest.builder()
                    .title("Updated Title")
                    .description("Updated description")
                    .priority(TaskPriority.URGENT)
                    .storyPoints(8)
                    .build();

            when(taskRepository.findById(1000L)).thenReturn(Optional.of(task));
            when(memberRepository.existsByOrganizationIdAndUserId(10L, 1L)).thenReturn(true);
            when(taskRepository.save(any(Task.class))).thenAnswer(invocation -> invocation.getArgument(0));
            when(taskRepository.countSubtasksByParentTaskId(anyLong())).thenReturn(0L);

            TaskResponse response = taskService.update(1000L, request, currentUser);

            assertThat(response).isNotNull();
            assertThat(response.getTitle()).isEqualTo("Updated Title");
            assertThat(response.getDescription()).isEqualTo("Updated description");
            assertThat(response.getPriority()).isEqualTo(TaskPriority.URGENT);

            verify(taskRepository).save(any(Task.class));
            verify(webSocketEventService).sendTaskUpdated(eq(100L), eq(1000L), any(TaskResponse.class));
        }

        @Test
        @DisplayName("should throw ResourceNotFoundException when task not found")
        void shouldThrowWhenTaskNotFound() {
            TaskRequest request = TaskRequest.builder()
                    .title("Updated Title")
                    .build();

            when(taskRepository.findById(999L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> taskService.update(999L, request, currentUser))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessage("Task not found");
        }
    }

    @Nested
    @DisplayName("updateStatus()")
    class UpdateStatus {

        @Test
        @DisplayName("should update status to IN_PROGRESS and set startedAt")
        void shouldUpdateStatusToInProgress() {
            when(taskRepository.findById(1000L)).thenReturn(Optional.of(task));
            when(memberRepository.existsByOrganizationIdAndUserId(10L, 1L)).thenReturn(true);
            when(taskRepository.save(any(Task.class))).thenAnswer(invocation -> invocation.getArgument(0));
            when(taskRepository.countSubtasksByParentTaskId(anyLong())).thenReturn(0L);

            TaskResponse response = taskService.updateStatus(1000L, TaskStatus.IN_PROGRESS, currentUser);

            assertThat(response).isNotNull();
            assertThat(response.getStatus()).isEqualTo(TaskStatus.IN_PROGRESS);
            assertThat(response.getStartedAt()).isNotNull();

            verify(webSocketEventService).sendTaskUpdated(eq(100L), eq(1000L), any(TaskResponse.class));
        }

        @Test
        @DisplayName("should update status to DONE and set completedAt")
        void shouldUpdateStatusToDone() {
            task.setStatus(TaskStatus.IN_PROGRESS);
            task.setStartedAt(Instant.now());

            when(taskRepository.findById(1000L)).thenReturn(Optional.of(task));
            when(memberRepository.existsByOrganizationIdAndUserId(10L, 1L)).thenReturn(true);
            when(taskRepository.save(any(Task.class))).thenAnswer(invocation -> invocation.getArgument(0));
            when(taskRepository.countSubtasksByParentTaskId(anyLong())).thenReturn(0L);

            TaskResponse response = taskService.updateStatus(1000L, TaskStatus.DONE, currentUser);

            assertThat(response).isNotNull();
            assertThat(response.getStatus()).isEqualTo(TaskStatus.DONE);
            assertThat(response.getCompletedAt()).isNotNull();
        }

        @Test
        @DisplayName("should clear completedAt when moving from DONE to another status")
        void shouldClearCompletedAtWhenMovingFromDone() {
            task.setStatus(TaskStatus.DONE);
            task.setCompletedAt(Instant.now());

            when(taskRepository.findById(1000L)).thenReturn(Optional.of(task));
            when(memberRepository.existsByOrganizationIdAndUserId(10L, 1L)).thenReturn(true);
            when(taskRepository.save(any(Task.class))).thenAnswer(invocation -> invocation.getArgument(0));
            when(taskRepository.countSubtasksByParentTaskId(anyLong())).thenReturn(0L);

            TaskResponse response = taskService.updateStatus(1000L, TaskStatus.IN_PROGRESS, currentUser);

            assertThat(response).isNotNull();
            assertThat(response.getStatus()).isEqualTo(TaskStatus.IN_PROGRESS);
            assertThat(response.getCompletedAt()).isNull();
        }
    }

    @Nested
    @DisplayName("getByProjectId()")
    class GetByProjectId {

        @Test
        @DisplayName("should return paginated tasks for a project")
        void shouldReturnPaginatedTasks() {
            Pageable pageable = PageRequest.of(0, 20);
            Page<Task> taskPage = new PageImpl<>(List.of(task), pageable, 1);

            when(projectRepository.findById(100L)).thenReturn(Optional.of(project));
            when(memberRepository.existsByOrganizationIdAndUserId(10L, 1L)).thenReturn(true);
            when(taskRepository.findByProjectId(100L, pageable)).thenReturn(taskPage);
            when(taskRepository.countSubtasksByParentTaskId(anyLong())).thenReturn(0L);

            PageResponse<TaskResponse> response = taskService.getByProjectId(100L, pageable, currentUser);

            assertThat(response).isNotNull();
            assertThat(response.getContent()).hasSize(1);
            assertThat(response.getTotalElements()).isEqualTo(1);
            assertThat(response.getPageNumber()).isEqualTo(0);
            assertThat(response.getPageSize()).isEqualTo(20);

            TaskResponse taskResponse = response.getContent().get(0);
            assertThat(taskResponse.getTitle()).isEqualTo("Test Task");
            assertThat(taskResponse.getProjectId()).isEqualTo(100L);
        }

        @Test
        @DisplayName("should throw ResourceNotFoundException when project not found")
        void shouldThrowWhenProjectNotFound() {
            Pageable pageable = PageRequest.of(0, 20);

            when(projectRepository.findById(999L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> taskService.getByProjectId(999L, pageable, currentUser))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessage("Project not found");
        }
    }

    @Nested
    @DisplayName("getById()")
    class GetById {

        @Test
        @DisplayName("should return task when found")
        void shouldReturnTaskWhenFound() {
            when(taskRepository.findById(1000L)).thenReturn(Optional.of(task));
            when(memberRepository.existsByOrganizationIdAndUserId(10L, 1L)).thenReturn(true);
            when(taskRepository.countSubtasksByParentTaskId(1000L)).thenReturn(0L);

            TaskResponse response = taskService.getById(1000L, currentUser);

            assertThat(response).isNotNull();
            assertThat(response.getId()).isEqualTo(1000L);
            assertThat(response.getTitle()).isEqualTo("Test Task");
            assertThat(response.getProjectId()).isEqualTo(100L);
            assertThat(response.getProjectName()).isEqualTo("Test Project");
            assertThat(response.getCreatedById()).isEqualTo(1L);
            assertThat(response.getCreatedByName()).isEqualTo("Test User");
        }

        @Test
        @DisplayName("should throw ResourceNotFoundException when task not found")
        void shouldThrowWhenTaskNotFound() {
            when(taskRepository.findById(999L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> taskService.getById(999L, currentUser))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessage("Task not found");
        }
    }
}
