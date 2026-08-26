package dev.squadx.controlpanel.controller;

import dev.squadx.controlpanel.dto.change.ActivityEventResponse;
import dev.squadx.controlpanel.dto.change.ChangeRequest;
import dev.squadx.controlpanel.dto.change.ChangeResponse;
import dev.squadx.controlpanel.service.ChangeService;
import dev.squadx.controlpanel.service.SpecEventService;
import dev.squadx.controlpanel.service.SpecVersionService;
import dev.squadx.controlpanel.dto.change.SpecVersionResponse;
import dev.squadx.dto.common.ApiResponse;
import dev.squadx.dto.common.PageResponse;
import dev.squadx.model.User;
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

import java.util.Map;

@RestController
@RequestMapping("/api/v1/changes")
@RequiredArgsConstructor
@Tag(name = "Control Panel — Changes", description = "Spec changes (control panel work model)")
public class ChangeController {

    private final ChangeService changeService;
    private final SpecVersionService specVersionService;
    private final SpecEventService specEventService;

    @GetMapping("/{id}/versions")
    @Operation(summary = "List semantic spec versions of a change")
    public ResponseEntity<ApiResponse<java.util.List<SpecVersionResponse>>> versions(
            @PathVariable Long id, @AuthenticationPrincipal User user) {
        return ResponseEntity.ok(ApiResponse.success(specVersionService.historyForUser(id, user)));
    }

    @PostMapping
    @Operation(summary = "Create a change")
    public ResponseEntity<ApiResponse<ChangeResponse>> create(
            @Valid @RequestBody ChangeRequest request,
            @AuthenticationPrincipal User user
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(changeService.create(request, user), "Change created successfully"));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get a change by ID")
    public ResponseEntity<ApiResponse<ChangeResponse>> getById(
            @PathVariable Long id,
            @AuthenticationPrincipal User user
    ) {
        return ResponseEntity.ok(ApiResponse.success(changeService.getById(id, user)));
    }

    @GetMapping("/project/{projectId}")
    @Operation(summary = "List changes of a project")
    public ResponseEntity<ApiResponse<PageResponse<ChangeResponse>>> getByProject(
            @PathVariable Long projectId,
            @PageableDefault(size = 20) Pageable pageable,
            @AuthenticationPrincipal User user
    ) {
        return ResponseEntity.ok(ApiResponse.success(changeService.getByProjectId(projectId, pageable, user)));
    }

    @GetMapping("/project/{projectId}/where-we-are")
    @Operation(summary = "Projection of progress per status for a project (\"where we are\")")
    public ResponseEntity<ApiResponse<Map<String, Object>>> whereWeAre(
            @PathVariable Long projectId,
            @AuthenticationPrincipal User user
    ) {
        return ResponseEntity.ok(ApiResponse.success(changeService.whereWeAre(projectId, user)));
    }

    @GetMapping("/project/{projectId}/activity")
    @Operation(summary = "Recent spec events across the project (live activity feed)")
    public ResponseEntity<ApiResponse<java.util.List<ActivityEventResponse>>> activity(
            @PathVariable Long projectId,
            @RequestParam(name = "limit", defaultValue = "30") int limit,
            @AuthenticationPrincipal User user
    ) {
        return ResponseEntity.ok(
                ApiResponse.success(specEventService.recentForProject(projectId, user, limit)));
    }
}
