package dev.squadx.controller;

import dev.squadx.dto.common.ApiResponse;
import dev.squadx.dto.common.PageResponse;
import dev.squadx.controller.support.AuthenticatedUserResolver;
import dev.squadx.dto.project.ProjectRequest;
import dev.squadx.dto.project.ProjectResponse;
import dev.squadx.model.User;
import dev.squadx.service.ProjectService;
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
import jakarta.servlet.http.HttpServletRequest;

@RestController
@RequestMapping("/api/v1/projects")
@RequiredArgsConstructor
@Tag(name = "Projects", description = "Project management endpoints")
public class ProjectController {

    private final ProjectService projectService;

    @PostMapping
    @Operation(summary = "Create a new project")
    public ResponseEntity<ApiResponse<ProjectResponse>> create(
            @Valid @RequestBody ProjectRequest request,
            @AuthenticationPrincipal User user
    ) {
        User currentUser = AuthenticatedUserResolver.resolve(user);
        ProjectResponse response = projectService.create(request, currentUser);
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponse.success(response, "Project created successfully"));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get project by ID")
    public ResponseEntity<ApiResponse<ProjectResponse>> getById(
            @PathVariable Long id,
            @AuthenticationPrincipal User user
    ) {
        User currentUser = AuthenticatedUserResolver.resolve(user);
        ProjectResponse response = projectService.getById(id, currentUser);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @GetMapping("/slug/{slug}")
    @Operation(summary = "Get project by slug")
    public ResponseEntity<ApiResponse<ProjectResponse>> getBySlug(
            @PathVariable String slug,
            @AuthenticationPrincipal User user
    ) {
        User currentUser = AuthenticatedUserResolver.resolve(user);
        ProjectResponse response = projectService.getBySlug(slug, currentUser);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @GetMapping("/organization/{organizationId}")
    @Operation(summary = "Get projects by organization ID")
    public ResponseEntity<ApiResponse<PageResponse<ProjectResponse>>> getByOrganizationId(
            @PathVariable Long organizationId,
            @PageableDefault(size = 20) Pageable pageable,
            @AuthenticationPrincipal User user
    ) {
        User currentUser = AuthenticatedUserResolver.resolve(user);
        PageResponse<ProjectResponse> response = projectService.getByOrganizationId(organizationId, pageable, currentUser);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @GetMapping("/my")
    @Operation(summary = "Get current user's projects")
    public ResponseEntity<ApiResponse<PageResponse<ProjectResponse>>> getMyProjects(
            @PageableDefault(size = 20) Pageable pageable,
            @AuthenticationPrincipal User user,
            HttpServletRequest request
    ) {
        User currentUser = AuthenticatedUserResolver.resolve(user, request);
        PageResponse<ProjectResponse> response = projectService.getByUserId(currentUser.getId(), pageable);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update a project")
    public ResponseEntity<ApiResponse<ProjectResponse>> update(
            @PathVariable Long id,
            @Valid @RequestBody ProjectRequest request,
            @AuthenticationPrincipal User user
    ) {
        User currentUser = AuthenticatedUserResolver.resolve(user);
        ProjectResponse response = projectService.update(id, request, currentUser);
        return ResponseEntity.ok(ApiResponse.success(response, "Project updated successfully"));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete a project")
    public ResponseEntity<ApiResponse<Void>> delete(
            @PathVariable Long id,
            @AuthenticationPrincipal User user
    ) {
        User currentUser = AuthenticatedUserResolver.resolve(user);
        projectService.delete(id, currentUser);
        return ResponseEntity.ok(ApiResponse.success(null, "Project deleted successfully"));
    }
}
