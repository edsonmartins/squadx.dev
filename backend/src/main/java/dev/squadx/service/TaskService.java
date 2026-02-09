package dev.squadx.service;

import dev.squadx.dto.common.PageResponse;
import dev.squadx.dto.task.TaskRequest;
import dev.squadx.dto.task.TaskResponse;
import dev.squadx.exception.ForbiddenException;
import dev.squadx.exception.ResourceNotFoundException;
import dev.squadx.model.*;
import dev.squadx.model.enums.TaskStatus;
import dev.squadx.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class TaskService {

    private final TaskRepository taskRepository;
    private final ProjectRepository projectRepository;
    private final AgentRepository agentRepository;
    private final OrganizationMemberRepository memberRepository;
    private final WebSocketEventService webSocketEventService;

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
        notifyTaskChange("created", response, project.getId());

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
        notifyTaskChange("updated", response, task.getProject().getId());

        return response;
    }

    @Transactional
    public TaskResponse updateStatus(Long id, TaskStatus status, User currentUser) {
        Task task = taskRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Task not found"));

        validateUserAccess(task.getProject().getOrganization().getId(), currentUser.getId());

        updateTaskStatus(task, status);
        task = taskRepository.save(task);

        TaskResponse response = mapToResponse(task);
        notifyTaskChange("status_changed", response, task.getProject().getId());

        return response;
    }

    @Transactional
    public void updateOrder(Long id, Integer newOrderIndex, User currentUser) {
        Task task = taskRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Task not found"));

        validateUserAccess(task.getProject().getOrganization().getId(), currentUser.getId());

        taskRepository.updateOrderIndex(id, newOrderIndex);

        notifyTaskChange("reordered", mapToResponse(task), task.getProject().getId());
    }

    @Transactional
    public void delete(Long id, User currentUser) {
        Task task = taskRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Task not found"));

        validateUserAccess(task.getProject().getOrganization().getId(), currentUser.getId());

        Long projectId = task.getProject().getId();
        taskRepository.delete(task);

        notifyTaskChange("deleted", TaskResponse.builder().id(id).build(), projectId);
    }

    private void updateTaskStatus(Task task, TaskStatus newStatus) {
        TaskStatus oldStatus = task.getStatus();
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

    private void notifyTaskChange(String action, TaskResponse task, Long projectId) {
        switch (action) {
            case "created" -> webSocketEventService.sendTaskCreated(projectId, task);
            case "updated", "status_changed", "reordered" -> webSocketEventService.sendTaskUpdated(projectId, task.getId(), task);
            case "deleted" -> webSocketEventService.sendTaskDeleted(projectId, task.getId());
        }
    }

    private TaskResponse mapToResponse(Task task) {
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
                .createdAt(task.getCreatedAt())
                .updatedAt(task.getUpdatedAt())
                .build();
    }
}
