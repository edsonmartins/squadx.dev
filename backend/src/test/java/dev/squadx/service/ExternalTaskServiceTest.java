package dev.squadx.service;

import dev.squadx.dto.task.ExternalTaskRequest;
import dev.squadx.model.Project;
import dev.squadx.model.Task;
import dev.squadx.model.Agent;
import dev.squadx.model.Execution;
import dev.squadx.model.Squad;
import dev.squadx.repository.ProjectRepository;
import dev.squadx.repository.TaskRepository;
import dev.squadx.repository.ExecutionRepository;
import org.springframework.context.ApplicationEventPublisher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ExternalTaskServiceTest {

    @Mock ProjectRepository projectRepository;
    @Mock TaskRepository taskRepository;
    @Mock ExecutionRepository executionRepository;
    @Mock SquadAgentResolver squadAgentResolver;
    @Mock ApplicationEventPublisher eventPublisher;
    private ExternalTaskService service;

    @BeforeEach
    void setUp() {
        service = new ExternalTaskService(projectRepository, taskRepository, executionRepository,
                squadAgentResolver, eventPublisher);
    }

    @Test
    void returnsExistingTaskForRepeatedReview() {
        Task existing = Task.builder().build();
        existing.setId(91L);
        when(taskRepository.findByExternalSystemAndExternalId("PULLWISE", "44"))
                .thenReturn(Optional.of(existing));

        var result = service.upsertPullwiseTask(request());

        assertThat(result.taskId()).isEqualTo(91L);
        assertThat(result.created()).isFalse();
        assertThat(result.autoStartStatus()).isEqualTo("QUEUED_FOR_APPROVAL");
        verifyNoInteractions(projectRepository);
        verify(taskRepository, never()).save(any());
    }

    @Test
    void createsLinkedTaskForMatchingRepository() {
        Project project = Project.builder().name("repo").repositoryUrl("https://github.com/acme/repo")
                .squad(Squad.builder().name("architecture").build()).build();
        when(taskRepository.findByExternalSystemAndExternalId("PULLWISE", "44"))
                .thenReturn(Optional.empty());
        when(projectRepository.findFirstByRepositoryUrlIn(any())).thenReturn(Optional.of(project));
        when(taskRepository.save(any())).thenAnswer(invocation -> {
            Task task = invocation.getArgument(0);
            task.setId(92L);
            return task;
        });

        var result = service.upsertPullwiseTask(request());

        assertThat(result.taskId()).isEqualTo(92L);
        assertThat(result.created()).isTrue();
        assertThat(result.autoStartStatus()).isEqualTo("QUEUED_FOR_APPROVAL");
        verify(taskRepository).save(argThat(task ->
                task.getPullwiseReviewId().equals(44L)
                        && "PULLWISE".equals(task.getExternalSystem())
                        && "44".equals(task.getExternalId())
                        && "abc123".equals(task.getRequestedGitRevision())
                        && Boolean.TRUE.equals(task.getArchitectureOnly())));
    }

    @Test
    void schedulesExecutionWhenAutoStartIsEnabled() {
        Project project = Project.builder().name("repo").repositoryUrl("https://github.com/acme/repo")
                .squad(Squad.builder().name("architecture").build()).build();
        Agent agent = Agent.builder().name("architect").build();
        when(taskRepository.findByExternalSystemAndExternalId("PULLWISE", "44"))
                .thenReturn(Optional.empty());
        when(projectRepository.findFirstByRepositoryUrlIn(any())).thenReturn(Optional.of(project));
        when(taskRepository.save(any())).thenAnswer(invocation -> {
            Task task = invocation.getArgument(0);
            task.setId(92L);
            return task;
        });
        when(squadAgentResolver.resolve(any())).thenReturn(agent);
        when(executionRepository.save(any())).thenAnswer(invocation -> {
            Execution execution = invocation.getArgument(0);
            execution.setId(93L);
            return execution;
        });

        var request = new ExternalTaskRequest("https://github.com/acme/repo.git", 44L,
                "Architecture review", "Generate delta", "abc123", true);
        var result = service.upsertPullwiseTask(request);

        assertThat(result.executionId()).isEqualTo(93L);
        assertThat(result.autoStartStatus()).isEqualTo("SCHEDULED");
        verify(eventPublisher).publishEvent(any(Object.class));
    }

    private ExternalTaskRequest request() {
        return new ExternalTaskRequest("https://github.com/acme/repo.git", 44L,
                "Architecture review", "Generate delta", "abc123", false);
    }
}
