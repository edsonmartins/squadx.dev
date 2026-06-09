package dev.squadx.controller;

import dev.squadx.dto.common.ApiResponse;
import dev.squadx.dto.common.PageResponse;
import dev.squadx.dto.execution.ExecutionRequest;
import dev.squadx.dto.execution.ExecutionResponse;
import dev.squadx.model.User;
import dev.squadx.model.enums.ExecutionStatus;
import dev.squadx.service.ExecutionService;
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
@RequestMapping("/api/v1/executions")
@RequiredArgsConstructor
@Tag(name = "Executions", description = "Task execution management endpoints")
public class ExecutionController {

    private final ExecutionService executionService;

    @PostMapping
    @Operation(summary = "Start a new execution")
    public ResponseEntity<ApiResponse<ExecutionResponse>> start(
            @Valid @RequestBody ExecutionRequest request,
            @AuthenticationPrincipal User user
    ) {
        ExecutionResponse response = executionService.startExecution(request, user);
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponse.success(response, "Execution started"));
    }

    @GetMapping("/pending")
    @Operation(summary = "List pending execution assignments for the client to claim (polling fallback)")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> getPending(
            @AuthenticationPrincipal User user
    ) {
        return ResponseEntity.ok(ApiResponse.success(executionService.getPendingAssignments(user)));
    }

    @PostMapping("/{id}/claim")
    @Operation(summary = "Atomically claim a pending execution (polling fallback)")
    public ResponseEntity<ApiResponse<Boolean>> claim(
            @PathVariable Long id,
            @AuthenticationPrincipal User user
    ) {
        return ResponseEntity.ok(ApiResponse.success(executionService.claimPending(id, user)));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get execution by ID")
    public ResponseEntity<ApiResponse<ExecutionResponse>> getById(
            @PathVariable Long id,
            @AuthenticationPrincipal User user
    ) {
        ExecutionResponse response = executionService.getById(id, user);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @GetMapping("/task/{taskId}")
    @Operation(summary = "Get executions by task ID")
    public ResponseEntity<ApiResponse<PageResponse<ExecutionResponse>>> getByTaskId(
            @PathVariable Long taskId,
            @PageableDefault(size = 20) Pageable pageable,
            @AuthenticationPrincipal User user
    ) {
        PageResponse<ExecutionResponse> response = executionService.getByTaskId(taskId, pageable, user);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @GetMapping("/project/{projectId}")
    @Operation(summary = "Get executions by project ID")
    public ResponseEntity<ApiResponse<PageResponse<ExecutionResponse>>> getByProjectId(
            @PathVariable Long projectId,
            @PageableDefault(size = 20) Pageable pageable,
            @AuthenticationPrincipal User user
    ) {
        PageResponse<ExecutionResponse> response = executionService.getByProjectId(projectId, pageable, user);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @GetMapping("/organization/{organizationId}")
    @Operation(summary = "Get executions by organization ID")
    public ResponseEntity<ApiResponse<PageResponse<ExecutionResponse>>> getByOrganizationId(
            @PathVariable Long organizationId,
            @PageableDefault(size = 20) Pageable pageable,
            @AuthenticationPrincipal User user
    ) {
        PageResponse<ExecutionResponse> response = executionService.getByOrganizationId(organizationId, pageable, user);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @PatchMapping("/{id}/status")
    @Operation(summary = "Update execution status")
    public ResponseEntity<ApiResponse<ExecutionResponse>> updateStatus(
            @PathVariable Long id,
            @RequestParam ExecutionStatus status,
            @AuthenticationPrincipal User user
    ) {
        ExecutionResponse response = executionService.updateStatus(id, status, user);
        return ResponseEntity.ok(ApiResponse.success(response, "Execution status updated"));
    }

    @PostMapping("/{id}/cancel")
    @Operation(summary = "Cancel an execution")
    public ResponseEntity<ApiResponse<ExecutionResponse>> cancel(
            @PathVariable Long id,
            @AuthenticationPrincipal User user
    ) {
        ExecutionResponse response = executionService.cancel(id, user);
        return ResponseEntity.ok(ApiResponse.success(response, "Execution cancelled"));
    }

    @GetMapping("/organization/{organizationId}/metrics")
    @Operation(summary = "Get organization execution metrics")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getOrganizationMetrics(
            @PathVariable Long organizationId,
            @AuthenticationPrincipal User user
    ) {
        Map<String, Object> metrics = executionService.getOrganizationMetrics(organizationId, user);
        return ResponseEntity.ok(ApiResponse.success(metrics));
    }
}
