package dev.squadx.service;

import dev.squadx.dto.task.ExternalTaskRequest;
import dev.squadx.dto.task.ExternalTaskResponse;
import dev.squadx.exception.ResourceNotFoundException;
import dev.squadx.model.Project;
import dev.squadx.model.Task;
import dev.squadx.model.Agent;
import dev.squadx.model.Execution;
import dev.squadx.model.enums.ExecutionStatus;
import dev.squadx.model.enums.TaskPriority;
import dev.squadx.model.enums.TaskStatus;
import dev.squadx.repository.ProjectRepository;
import dev.squadx.repository.TaskRepository;
import dev.squadx.repository.ExecutionRepository;
import dev.squadx.event.ExecutionStartedEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.context.ApplicationEventPublisher;

import java.util.LinkedHashSet;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class ExternalTaskService {

    private static final String PULLWISE = "PULLWISE";
    private final ProjectRepository projectRepository;
    private final TaskRepository taskRepository;
    private final ExecutionRepository executionRepository;
    private final SquadAgentResolver squadAgentResolver;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional
    public ExternalTaskResponse upsertPullwiseTask(ExternalTaskRequest request) {
        String externalId = String.valueOf(request.reviewId());
        var existing = taskRepository.findByExternalSystemAndExternalId(PULLWISE, externalId);
        if (existing.isPresent()) {
            return startIfRequested(existing.get(), false, Boolean.TRUE.equals(request.autoStart()));
        }

        Project project = projectRepository.findFirstByRepositoryUrlIn(repositoryVariants(request.repositoryUrl()))
                .orElseThrow(() -> new ResourceNotFoundException(
                        "No SquadX project matches repository_url " + request.repositoryUrl()));
        Task task = Task.builder()
                .title(request.title())
                .description(request.description())
                .status(TaskStatus.TODO)
                .priority(TaskPriority.HIGH)
                .project(project)
                .assignedSquad(project.getSquad())
                .pullwiseReviewId(request.reviewId())
                .externalSystem(PULLWISE)
                .externalId(externalId)
                .requestedGitRevision(request.headRevision())
                .architectureOnly(true)
                .tags(Set.of("pullwise", "architecture-review"))
                .build();
        task = taskRepository.save(task);
        return startIfRequested(task, true, Boolean.TRUE.equals(request.autoStart()));
    }

    private ExternalTaskResponse startIfRequested(Task task, boolean created, boolean autoStart) {
        var current = executionRepository.findTopByTaskIdOrderByCreatedAtDesc(task.getId());
        if (current.isPresent()) {
            return new ExternalTaskResponse(task.getId(), created, current.get().getId(), "ALREADY_SCHEDULED");
        }
        if (!autoStart) {
            return new ExternalTaskResponse(task.getId(), created, null, "QUEUED_FOR_APPROVAL");
        }
        Agent agent = squadAgentResolver.resolve(task.getAssignedSquad());
        if (agent == null) {
            return new ExternalTaskResponse(task.getId(), created, null, "NO_AGENT_AVAILABLE");
        }
        task.setAssignedAgent(agent);
        task.setStatus(TaskStatus.IN_PROGRESS);
        taskRepository.save(task);
        Execution execution = executionRepository.save(Execution.builder()
                .task(task)
                .agent(agent)
                .status(ExecutionStatus.PENDING)
                .idempotencyKey("pullwise-review:" + task.getPullwiseReviewId())
                .build());
        eventPublisher.publishEvent(new ExecutionStartedEvent(execution.getId()));
        return new ExternalTaskResponse(task.getId(), created, execution.getId(), "SCHEDULED");
    }

    private Set<String> repositoryVariants(String value) {
        String normalized = value.trim();
        Set<String> variants = new LinkedHashSet<>();
        variants.add(normalized);
        if (normalized.endsWith(".git")) {
            variants.add(normalized.substring(0, normalized.length() - 4));
        } else {
            variants.add(normalized + ".git");
        }
        return variants;
    }
}
