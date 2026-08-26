package dev.squadx.controller;

import dev.squadx.dto.artifact.ExecutionArtifactResponse;
import dev.squadx.dto.artifact.PublishArtifactRequest;
import dev.squadx.dto.common.ApiResponse;
import dev.squadx.model.User;
import dev.squadx.service.ExecutionArtifactService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
@Tag(name = "Execution Artifacts", description = "Versioned deliverables produced by executions")
public class ExecutionArtifactController {
    private final ExecutionArtifactService artifactService;

    @PutMapping("/executions/{executionId}/artifacts")
    @Operation(summary = "Publish or replace an execution artifact by stable key")
    public ResponseEntity<ApiResponse<ExecutionArtifactResponse>> publish(
            @PathVariable Long executionId,
            @Valid @RequestBody PublishArtifactRequest request,
            @AuthenticationPrincipal User user) {
        return ResponseEntity.ok(ApiResponse.success(artifactService.publish(executionId, request, user)));
    }

    @GetMapping("/executions/{executionId}/artifacts")
    @Operation(summary = "List artifact metadata for an execution")
    public ResponseEntity<ApiResponse<List<ExecutionArtifactResponse>>> list(
            @PathVariable Long executionId,
            @AuthenticationPrincipal User user) {
        return ResponseEntity.ok(ApiResponse.success(artifactService.list(executionId, user)));
    }

    @GetMapping("/executions/{executionId}/artifacts/architecture-baseline")
    @Operation(summary = "Get the previous architecture IR for this execution's project")
    public ResponseEntity<ApiResponse<ExecutionArtifactResponse>> baseline(
            @PathVariable Long executionId,
            @AuthenticationPrincipal User user) {
        return ResponseEntity.ok(ApiResponse.success(
                artifactService.getArchitectureBaseline(executionId, user)));
    }

    @GetMapping("/execution-artifacts/{artifactId}")
    @Operation(summary = "Read an execution artifact including content")
    public ResponseEntity<ApiResponse<ExecutionArtifactResponse>> get(
            @PathVariable Long artifactId,
            @AuthenticationPrincipal User user) {
        return ResponseEntity.ok(ApiResponse.success(artifactService.get(artifactId, user)));
    }
}
