package dev.squadx.controlpanel.controller;

import dev.squadx.controlpanel.dto.spectask.SpecTaskRequest;
import dev.squadx.controlpanel.dto.spectask.SpecTaskResponse;
import dev.squadx.controlpanel.dto.spectask.SpecTaskTransitionRequest;
import dev.squadx.controlpanel.service.SpecTaskService;
import dev.squadx.dto.common.ApiResponse;
import dev.squadx.model.User;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/spec-tasks")
@RequiredArgsConstructor
@Tag(name = "Control Panel — Tasks", description = "Spec-driven tasks with the Pass 5 state machine")
public class SpecTaskController {

    private final SpecTaskService specTaskService;

    @PostMapping
    @Operation(summary = "Create a spec task")
    public ResponseEntity<ApiResponse<SpecTaskResponse>> create(
            @Valid @RequestBody SpecTaskRequest request,
            @AuthenticationPrincipal User user
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(specTaskService.create(request, user), "Task created successfully"));
    }

    @GetMapping("/change/{changeId}")
    @Operation(summary = "List tasks of a change")
    public ResponseEntity<ApiResponse<List<SpecTaskResponse>>> getByChange(
            @PathVariable Long changeId,
            @AuthenticationPrincipal User user
    ) {
        return ResponseEntity.ok(ApiResponse.success(specTaskService.getByChangeId(changeId, user)));
    }

    @PatchMapping("/{id}/transition")
    @Operation(summary = "Transition a task (CONCLUIDA/AJUSTES are rejected — only Pass 5 sets them)")
    public ResponseEntity<ApiResponse<SpecTaskResponse>> transition(
            @PathVariable Long id,
            @Valid @RequestBody SpecTaskTransitionRequest request,
            @AuthenticationPrincipal User user
    ) {
        return ResponseEntity.ok(
                ApiResponse.success(specTaskService.transition(id, request, user), "Task transitioned"));
    }
}
