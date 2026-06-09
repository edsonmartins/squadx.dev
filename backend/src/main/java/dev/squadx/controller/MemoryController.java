package dev.squadx.controller;

import dev.squadx.dto.common.ApiResponse;
import dev.squadx.dto.memory.MemorySkillRequest;
import dev.squadx.model.User;
import dev.squadx.service.MemoryGovernanceService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/memory")
@RequiredArgsConstructor
@Tag(name = "Memory", description = "BrainSentry-backed memory governance endpoints")
public class MemoryController {

    private final MemoryGovernanceService memoryGovernanceService;

    @GetMapping("/tasks/{taskId}/context")
    @Operation(summary = "Load relevant memories for a task")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> getTaskContext(
            @PathVariable Long taskId,
            @AuthenticationPrincipal User user
    ) {
        return ResponseEntity.ok(ApiResponse.success(memoryGovernanceService.getTaskContext(taskId, user)));
    }

    @GetMapping("/skills")
    @Operation(summary = "List administrable procedural skills")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> listSkills(
            @RequestParam Long organizationId,
            @RequestParam(required = false) Long projectId,
            @RequestParam(required = false) Long agentId,
            @RequestParam(required = false) String query,
            @RequestParam(defaultValue = "20") int limit,
            @AuthenticationPrincipal User user
    ) {
        return ResponseEntity.ok(ApiResponse.success(
                memoryGovernanceService.listSkills(organizationId, projectId, agentId, query, limit, user)
        ));
    }

    @PostMapping("/skills")
    @Operation(summary = "Create a managed procedural skill")
    public ResponseEntity<ApiResponse<Map<String, Object>>> createSkill(
            @Valid @RequestBody MemorySkillRequest request,
            @AuthenticationPrincipal User user
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(memoryGovernanceService.createSkill(request, user), "Skill created"));
    }

    @PutMapping("/skills/{memoryId}")
    @Operation(summary = "Update a managed procedural skill")
    public ResponseEntity<ApiResponse<Map<String, Object>>> updateSkill(
            @PathVariable String memoryId,
            @Valid @RequestBody MemorySkillRequest request,
            @AuthenticationPrincipal User user
    ) {
        return ResponseEntity.ok(ApiResponse.success(
                memoryGovernanceService.updateSkill(memoryId, request, user),
                "Skill updated"
        ));
    }

    @DeleteMapping("/skills/{memoryId}")
    @Operation(summary = "Delete a managed procedural skill")
    public ResponseEntity<ApiResponse<Void>> deleteSkill(
            @PathVariable String memoryId,
            @RequestParam Long organizationId,
            @AuthenticationPrincipal User user
    ) {
        memoryGovernanceService.deleteSkill(memoryId, organizationId, user);
        return ResponseEntity.ok(ApiResponse.success(null, "Skill deleted"));
    }

    @GetMapping("/history/search")
    @Operation(summary = "Search historical memories and session summaries")
    public ResponseEntity<ApiResponse<Map<String, Object>>> searchHistory(
            @RequestParam Long organizationId,
            @RequestParam(required = false) Long projectId,
            @RequestParam(required = false) Long agentId,
            @RequestParam(required = false) Long executionId,
            @RequestParam(required = false) String query,
            @RequestParam(defaultValue = "10") int limit,
            @AuthenticationPrincipal User user
    ) {
        return ResponseEntity.ok(ApiResponse.success(
                memoryGovernanceService.searchHistory(organizationId, projectId, agentId, executionId, query, limit, user)
        ));
    }
}
