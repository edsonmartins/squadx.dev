package dev.squadx.controller;

import dev.squadx.dto.common.ApiResponse;
import dev.squadx.dto.common.PageResponse;
import dev.squadx.dto.task.TaskRequest;
import dev.squadx.dto.task.TaskResponse;
import dev.squadx.model.TaskDependency;
import dev.squadx.model.User;
import dev.squadx.model.enums.TaskStatus;
import dev.squadx.service.TaskService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/tasks")
@RequiredArgsConstructor
@Tag(name = "Tasks", description = "Task management endpoints")
public class TaskController {

    private final TaskService taskService;

    @PostMapping
    @Operation(summary = "Create a new task")
    public ResponseEntity<ApiResponse<TaskResponse>> create(
            @Valid @RequestBody TaskRequest request,
            @AuthenticationPrincipal User user
    ) {
        TaskResponse response = taskService.create(request, user);
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponse.success(response, "Task created successfully"));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get task by ID")
    public ResponseEntity<ApiResponse<TaskResponse>> getById(
            @PathVariable Long id,
            @AuthenticationPrincipal User user
    ) {
        TaskResponse response = taskService.getById(id, user);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @GetMapping("/project/{projectId}")
    @Operation(summary = "Get tasks by project ID")
    public ResponseEntity<ApiResponse<PageResponse<TaskResponse>>> getByProjectId(
            @PathVariable Long projectId,
            @PageableDefault(size = 20) Pageable pageable,
            @AuthenticationPrincipal User user
    ) {
        PageResponse<TaskResponse> response = taskService.getByProjectId(projectId, pageable, user);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @GetMapping("/project/{projectId}/kanban")
    @Operation(summary = "Get tasks by project ID for kanban board")
    public ResponseEntity<ApiResponse<List<TaskResponse>>> getKanbanTasks(
            @PathVariable Long projectId,
            @AuthenticationPrincipal User user
    ) {
        List<TaskResponse> response = taskService.getByProjectIdGroupedByStatus(projectId, user);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update a task")
    public ResponseEntity<ApiResponse<TaskResponse>> update(
            @PathVariable Long id,
            @Valid @RequestBody TaskRequest request,
            @AuthenticationPrincipal User user
    ) {
        TaskResponse response = taskService.update(id, request, user);
        return ResponseEntity.ok(ApiResponse.success(response, "Task updated successfully"));
    }

    @PatchMapping("/{id}/status")
    @Operation(summary = "Update task status")
    public ResponseEntity<ApiResponse<TaskResponse>> updateStatus(
            @PathVariable Long id,
            @RequestParam TaskStatus status,
            @AuthenticationPrincipal User user
    ) {
        TaskResponse response = taskService.updateStatus(id, status, user);
        return ResponseEntity.ok(ApiResponse.success(response, "Task status updated"));
    }

    @PatchMapping("/{id}/order")
    @Operation(summary = "Update task order")
    public ResponseEntity<ApiResponse<Void>> updateOrder(
            @PathVariable Long id,
            @RequestParam Integer orderIndex,
            @AuthenticationPrincipal User user
    ) {
        taskService.updateOrder(id, orderIndex, user);
        return ResponseEntity.ok(ApiResponse.success(null, "Task order updated"));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete a task")
    public ResponseEntity<ApiResponse<Void>> delete(
            @PathVariable Long id,
            @AuthenticationPrincipal User user
    ) {
        taskService.delete(id, user);
        return ResponseEntity.ok(ApiResponse.success(null, "Task deleted successfully"));
    }

    // ---- Task Dependency Endpoints ----

    @PostMapping("/{id}/dependencies")
    @Operation(summary = "Add a dependency to a task")
    public ResponseEntity<ApiResponse<Map<String, Object>>> addDependency(
            @PathVariable Long id,
            @RequestBody Map<String, Long> body,
            @AuthenticationPrincipal User user
    ) {
        Long dependsOnId = body.get("dependsOnId");
        if (dependsOnId == null) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error("dependsOnId is required"));
        }

        TaskDependency dependency = taskService.addDependency(id, dependsOnId, user);

        Map<String, Object> result = Map.of(
                "id", dependency.getId(),
                "taskId", id,
                "dependsOnId", dependsOnId
        );

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponse.success(result, "Dependency added successfully"));
    }

    @DeleteMapping("/{id}/dependencies/{depId}")
    @Operation(summary = "Remove a dependency from a task")
    public ResponseEntity<ApiResponse<Void>> removeDependency(
            @PathVariable Long id,
            @PathVariable Long depId,
            @AuthenticationPrincipal User user
    ) {
        taskService.removeDependency(id, depId, user);
        return ResponseEntity.ok(ApiResponse.success(null, "Dependency removed successfully"));
    }

    @GetMapping("/{id}/blockers")
    @Operation(summary = "Get blocking tasks for a task")
    public ResponseEntity<ApiResponse<List<TaskResponse>>> getBlockers(
            @PathVariable Long id,
            @AuthenticationPrincipal User user
    ) {
        List<TaskResponse> blockers = taskService.getBlockers(id, user);
        return ResponseEntity.ok(ApiResponse.success(blockers));
    }
}
