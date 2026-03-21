package dev.squadx.service;

import dev.squadx.dto.common.PageResponse;
import dev.squadx.dto.task.TaskRequest;
import dev.squadx.dto.task.TaskResponse;
import dev.squadx.event.TaskCreatedEvent;
import dev.squadx.event.TaskDeletedEvent;
import dev.squadx.event.TaskStatusChangedEvent;
import dev.squadx.exception.BadRequestException;
import dev.squadx.exception.ForbiddenException;
import dev.squadx.exception.ResourceNotFoundException;
import dev.squadx.model.*;
import dev.squadx.model.enums.TaskStatus;
import dev.squadx.model.vo.TaskStatusTransition;
import dev.squadx.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class TaskService {

    private final TaskRepository taskRepository;
    private final ProjectRepository projectRepository;
    private final AgentRepository agentRepository;
    private final OrganizationMemberRepository memberRepository;
    private final TaskDependencyRepository taskDependencyRepository;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional
    public TaskResponse create(TaskRequest request, User currentUser) {
        Project project = projectRepository.findById(request.getProjectId())
                .orElseThrow(() -> new ResourceNotFoundException("Project not found"));

        validateUserAccess(project.getOrganization().getId(), currentUser.getId());

        Task task = Task.builder()
                .title(request.getTitle())
                .description(request.getDescription())
                .status(request.getStatus() != null ? request.getStatus() : TaskStatus.TODO)
                .priority(request.getPriority())
                .storyPoints(request.getStoryPoints())
                .estimatedHours(request.getEstimatedHours())
                .dueDate(request.getDueDate())
                .project(project)
                .createdBy(currentUser)
                .tags(request.getTags())
                .build();

        if (request.getParentTaskId() != null) {
            Task parentTask = taskRepository.findById(request.getParentTaskId())
                    .orElseThrow(() -> new ResourceNotFoundException("Parent task not found"));
            task.setParentTask(parentTask);
        }

        if (request.getAssignedAgentId() != null) {
            Agent agent = agentRepository.findById(request.getAssignedAgentId())
                    .orElseThrow(() -> new ResourceNotFoundException("Agent not found"));
            task.setAssignedAgent(agent);
        }

        task = taskRepository.save(task);

        TaskResponse response = mapToResponse(task);
        eventPublisher.publishEvent(new TaskCreatedEvent(
                task.getId(), project.getId(), project.getOrganization().getId(), response));

        return response;
    }

    @Transactional(readOnly = true)
    public TaskResponse getById(Long id, User currentUser) {
        Task task = taskRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Task not found"));

        validateUserAccess(task.getProject().getOrganization().getId(), currentUser.getId());

        return mapToResponse(task);
    }

    @Transactional(readOnly = true)
    public PageResponse<TaskResponse> getByProjectId(Long projectId, Pageable pageable, User currentUser) {
        Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> new ResourceNotFoundException("Project not found"));

        validateUserAccess(project.getOrganization().getId(), currentUser.getId());

        Page<TaskResponse> page = taskRepository.findByProjectId(projectId, pageable)
                .map(this::mapToResponse);

        return PageResponse.from(page);
    }

    @Transactional(readOnly = true)
    public List<TaskResponse> getByProjectIdGroupedByStatus(Long projectId, User currentUser) {
        Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> new ResourceNotFoundException("Project not found"));

        validateUserAccess(project.getOrganization().getId(), currentUser.getId());

        return taskRepository.findRootTasksByProjectId(projectId).stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    @Transactional
    public TaskResponse update(Long id, TaskRequest request, User currentUser) {
        Task task = taskRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Task not found"));

        validateUserAccess(task.getProject().getOrganization().getId(), currentUser.getId());

        TaskStatus oldStatusBeforeUpdate = task.getStatus();

        if (request.getTitle() != null) {
            task.setTitle(request.getTitle());
        }
        if (request.getDescription() != null) {
            task.setDescription(request.getDescription());
        }
        if (request.getStatus() != null) {
            updateTaskStatus(task, request.getStatus());
        }
        if (request.getPriority() != null) {
            task.setPriority(request.getPriority());
        }
        if (request.getStoryPoints() != null) {
            task.setStoryPoints(request.getStoryPoints());
        }
        if (request.getEstimatedHours() != null) {
            task.setEstimatedHours(request.getEstimatedHours());
        }
        if (request.getDueDate() != null) {
            task.setDueDate(request.getDueDate());
        }
        if (request.getTags() != null) {
            task.setTags(request.getTags());
        }
        if (request.getAssignedAgentId() != null) {
            Agent agent = agentRepository.findById(request.getAssignedAgentId())
                    .orElseThrow(() -> new ResourceNotFoundException("Agent not found"));
            task.setAssignedAgent(agent);
        }

        task = taskRepository.save(task);

        TaskResponse response = mapToResponse(task);
        eventPublisher.publishEvent(new TaskStatusChangedEvent(
                task.getId(), task.getProject().getId(), task.getProject().getOrganization().getId(),
                oldStatusBeforeUpdate, task.getStatus(), response));

        return response;
    }

    @Transactional
    public TaskResponse updateStatus(Long id, TaskStatus status, User currentUser) {
        Task task = taskRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Task not found"));

        validateUserAccess(task.getProject().getOrganization().getId(), currentUser.getId());

        TaskStatus oldStatus = task.getStatus();
        updateTaskStatus(task, status);
        task = taskRepository.save(task);

        TaskResponse response = mapToResponse(task);
        eventPublisher.publishEvent(new TaskStatusChangedEvent(
                task.getId(), task.getProject().getId(), task.getProject().getOrganization().getId(),
                oldStatus, status, response));

        // When a task is completed, notify dependents that they may be unblocked
        if (status == TaskStatus.DONE) {
            notifyDependentsUnblocked(task);
        }

        return response;
    }

    @Transactional
    public void updateOrder(Long id, Integer newOrderIndex, User currentUser) {
        Task task = taskRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Task not found"));

        validateUserAccess(task.getProject().getOrganization().getId(), currentUser.getId());

        taskRepository.updateOrderIndex(id, newOrderIndex);

        TaskResponse response = mapToResponse(task);
        eventPublisher.publishEvent(new TaskStatusChangedEvent(
                task.getId(), task.getProject().getId(), task.getProject().getOrganization().getId(),
                task.getStatus(), task.getStatus(), response));
    }

    @Transactional
    public void delete(Long id, User currentUser) {
        Task task = taskRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Task not found"));

        validateUserAccess(task.getProject().getOrganization().getId(), currentUser.getId());

        Long projectId = task.getProject().getId();
        Long orgId = task.getProject().getOrganization().getId();
        taskRepository.delete(task);

        eventPublisher.publishEvent(new TaskDeletedEvent(id, projectId, orgId));
    }

    // ---- Task Dependency Methods ----

    @Transactional
    public TaskDependency addDependency(Long taskId, Long dependsOnId, User currentUser) {
        if (taskId.equals(dependsOnId)) {
            throw new BadRequestException("A task cannot depend on itself");
        }

        Task task = taskRepository.findById(taskId)
                .orElseThrow(() -> new ResourceNotFoundException("Task not found"));
        Task dependsOn = taskRepository.findById(dependsOnId)
                .orElseThrow(() -> new ResourceNotFoundException("Dependency task not found"));

        validateUserAccess(task.getProject().getOrganization().getId(), currentUser.getId());

        if (taskDependencyRepository.existsByTaskIdAndDependsOnId(taskId, dependsOnId)) {
            throw new BadRequestException("Dependency already exists");
        }

        // Cycle detection: check if adding this dependency would create a cycle
        if (wouldCreateCycle(taskId, dependsOnId)) {
            throw new BadRequestException("Adding this dependency would create a circular dependency");
        }

        TaskDependency dependency = TaskDependency.builder()
                .task(task)
                .dependsOn(dependsOn)
                .build();

        dependency = taskDependencyRepository.save(dependency);

        log.info("Added dependency: task {} depends on task {}", taskId, dependsOnId);

        // Notify that the task was updated (dependency changed)
        TaskResponse response = mapToResponse(task);
        eventPublisher.publishEvent(new TaskStatusChangedEvent(
                task.getId(), task.getProject().getId(), task.getProject().getOrganization().getId(),
                task.getStatus(), task.getStatus(), response));

        return dependency;
    }

    @Transactional
    public void removeDependency(Long taskId, Long dependsOnId, User currentUser) {
        Task task = taskRepository.findById(taskId)
                .orElseThrow(() -> new ResourceNotFoundException("Task not found"));

        validateUserAccess(task.getProject().getOrganization().getId(), currentUser.getId());

        if (!taskDependencyRepository.existsByTaskIdAndDependsOnId(taskId, dependsOnId)) {
            throw new ResourceNotFoundException("Dependency not found");
        }

        taskDependencyRepository.deleteByTaskIdAndDependsOnId(taskId, dependsOnId);

        log.info("Removed dependency: task {} no longer depends on task {}", taskId, dependsOnId);

        // Notify that the task was updated (dependency changed)
        TaskResponse response = mapToResponse(task);
        eventPublisher.publishEvent(new TaskStatusChangedEvent(
                task.getId(), task.getProject().getId(), task.getProject().getOrganization().getId(),
                task.getStatus(), task.getStatus(), response));
    }

    @Transactional(readOnly = true)
    public List<TaskResponse> getBlockers(Long taskId, User currentUser) {
        Task task = taskRepository.findById(taskId)
                .orElseThrow(() -> new ResourceNotFoundException("Task not found"));

        validateUserAccess(task.getProject().getOrganization().getId(), currentUser.getId());

        List<TaskDependency> dependencies = taskDependencyRepository.findByTaskId(taskId);

        return dependencies.stream()
                .map(TaskDependency::getDependsOn)
                .filter(dep -> dep.getStatus() != TaskStatus.DONE)
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    // ---- Private helpers ----

    /**
     * BFS-based cycle detection.
     * Checks if adding an edge from taskId -> dependsOnId would create a cycle.
     * A cycle exists if we can reach taskId by following dependencies starting from dependsOnId.
     */
    private boolean wouldCreateCycle(Long taskId, Long dependsOnId) {
        Set<Long> visited = new HashSet<>();
        Queue<Long> queue = new LinkedList<>();
        queue.add(dependsOnId);

        while (!queue.isEmpty()) {
            Long current = queue.poll();
            if (current.equals(taskId)) {
                return true; // Cycle detected
            }
            if (visited.contains(current)) {
                continue;
            }
            visited.add(current);

            List<TaskDependency> deps = taskDependencyRepository.findByTaskId(current);
            for (TaskDependency dep : deps) {
                queue.add(dep.getDependsOn().getId());
            }
        }

        return false;
    }

    /**
     * When a task is completed, find all tasks that depend on it and send
     * WebSocket events so they know they may be unblocked.
     */
    private void notifyDependentsUnblocked(Task completedTask) {
        List<TaskDependency> dependents = taskDependencyRepository.findByDependsOnId(completedTask.getId());

        for (TaskDependency dep : dependents) {
            Task dependentTask = dep.getTask();
            // Re-fetch to get fresh dependencies
            List<TaskDependency> remainingBlockers = taskDependencyRepository.findByTaskId(dependentTask.getId());
            boolean stillBlocked = remainingBlockers.stream()
                    .anyMatch(d -> d.getDependsOn().getStatus() != TaskStatus.DONE);

            if (!stillBlocked) {
                log.info("Task {} is now unblocked after completion of task {}",
                        dependentTask.getId(), completedTask.getId());
                TaskResponse response = mapToResponse(dependentTask);
                eventPublisher.publishEvent(new TaskStatusChangedEvent(
                        dependentTask.getId(), dependentTask.getProject().getId(),
                        dependentTask.getProject().getOrganization().getId(),
                        dependentTask.getStatus(), dependentTask.getStatus(), response));
            }
        }
    }

    private void updateTaskStatus(Task task, TaskStatus newStatus) {
        TaskStatus oldStatus = task.getStatus();

        // Validate transition (throws IllegalStateException for invalid transitions)
        try {
            new TaskStatusTransition(oldStatus, newStatus);
        } catch (IllegalStateException e) {
            throw new BadRequestException(e.getMessage());
        }

        task.setStatus(newStatus);

        if (newStatus == TaskStatus.IN_PROGRESS && task.getStartedAt() == null) {
            task.setStartedAt(Instant.now());
        }

        if (newStatus == TaskStatus.DONE && task.getCompletedAt() == null) {
            task.setCompletedAt(Instant.now());
        }

        if (oldStatus == TaskStatus.DONE && newStatus != TaskStatus.DONE) {
            task.setCompletedAt(null);
        }
    }

    private void validateUserAccess(Long organizationId, Long userId) {
        if (!memberRepository.existsByOrganizationIdAndUserId(organizationId, userId)) {
            throw new ForbiddenException("User does not have access to this organization");
        }
    }

    private TaskResponse mapToResponse(Task task) {
        List<TaskDependency> dependencies = taskDependencyRepository.findByTaskId(task.getId());
        List<TaskDependency> dependents = taskDependencyRepository.findByDependsOnId(task.getId());

        List<Long> blockedByIds = dependencies.stream()
                .map(dep -> dep.getDependsOn().getId())
                .collect(Collectors.toList());

        List<Long> dependentIds = dependents.stream()
                .map(dep -> dep.getTask().getId())
                .collect(Collectors.toList());

        boolean blocked = dependencies.stream()
                .anyMatch(dep -> dep.getDependsOn().getStatus() != TaskStatus.DONE);

        return TaskResponse.builder()
                .id(task.getId())
                .title(task.getTitle())
                .description(task.getDescription())
                .status(task.getStatus())
                .priority(task.getPriority())
                .storyPoints(task.getStoryPoints())
                .estimatedHours(task.getEstimatedHours())
                .actualHours(task.getActualHours())
                .dueDate(task.getDueDate())
                .startedAt(task.getStartedAt())
                .completedAt(task.getCompletedAt())
                .orderIndex(task.getOrderIndex())
                .projectId(task.getProject().getId())
                .projectName(task.getProject().getName())
                .assignedAgentId(task.getAssignedAgent() != null ? task.getAssignedAgent().getId() : null)
                .assignedAgentName(task.getAssignedAgent() != null ? task.getAssignedAgent().getName() : null)
                .parentTaskId(task.getParentTask() != null ? task.getParentTask().getId() : null)
                .subtasksCount((int) taskRepository.countSubtasksByParentTaskId(task.getId()))
                .createdById(task.getCreatedBy() != null ? task.getCreatedBy().getId() : null)
                .createdByName(task.getCreatedBy() != null ? task.getCreatedBy().getFullName() : null)
                .tags(task.getTags())
                .blocked(blocked)
                .blockedByIds(blockedByIds)
                .dependentIds(dependentIds)
                .createdAt(task.getCreatedAt())
                .updatedAt(task.getUpdatedAt())
                .build();
    }
}
