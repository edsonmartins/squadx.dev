package dev.squadx.controlpanel.controller;

import dev.squadx.controlpanel.dto.version.CreateVersionRequest;
import dev.squadx.controlpanel.dto.version.SpecVersionResponse;
import dev.squadx.controlpanel.mcp.SpecMaterializer;
import dev.squadx.controlpanel.mcp.dto.MaterializeResponse;
import dev.squadx.controlpanel.service.ChangeService;
import dev.squadx.controlpanel.service.SpecVersionService;
import dev.squadx.dto.common.ApiResponse;
import dev.squadx.model.User;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Versionamento e materialização da spec de uma mudança (ADR-0001, RFC-0002).
 */
@RestController
@RequestMapping("/api/v1/changes/{changeId}")
@RequiredArgsConstructor
@Tag(name = "Control Panel — Spec Versions", description = "Spec versioning + Git materialization")
public class SpecVersionController {

    private final SpecVersionService specVersionService;
    private final ChangeService changeService;
    private final SpecMaterializer materializer;

    @PostMapping("/versions")
    @Operation(summary = "Create (approve) a new spec version")
    public ResponseEntity<ApiResponse<SpecVersionResponse>> createVersion(
            @PathVariable Long changeId,
            @RequestBody CreateVersionRequest request,
            @AuthenticationPrincipal User user
    ) {
        SpecVersionResponse response = specVersionService.createVersion(changeId, request.getSummary(), user);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(response, "Version created"));
    }

    @GetMapping("/versions")
    @Operation(summary = "Spec version history of a change")
    public ResponseEntity<ApiResponse<List<SpecVersionResponse>>> history(
            @PathVariable Long changeId,
            @AuthenticationPrincipal User user
    ) {
        return ResponseEntity.ok(ApiResponse.success(specVersionService.getHistory(changeId, user)));
    }

    @PostMapping("/materialize")
    @Operation(summary = "Materialize the current spec version to the repo")
    public ResponseEntity<ApiResponse<MaterializeResponse>> materialize(
            @PathVariable Long changeId,
            @AuthenticationPrincipal User user
    ) {
        changeService.getById(changeId, user); // validates access
        SpecMaterializer.MaterializationResult result = materializer.materialize(changeId);
        MaterializeResponse response = MaterializeResponse.builder()
                .ok(result.available())
                .changeId(changeId)
                .version(result.version())
                .commit(result.commit())
                .prUrl(result.prUrl())
                .message(result.message())
                .build();
        return ResponseEntity.ok(ApiResponse.success(response));
    }
}
