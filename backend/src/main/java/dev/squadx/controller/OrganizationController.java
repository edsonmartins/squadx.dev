package dev.squadx.controller;

import dev.squadx.dto.common.ApiResponse;
import dev.squadx.dto.common.PageResponse;
import dev.squadx.controller.support.AuthenticatedUserResolver;
import dev.squadx.dto.organization.OrganizationRequest;
import dev.squadx.dto.organization.OrganizationResponse;
import dev.squadx.model.User;
import dev.squadx.service.OrganizationService;
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
@RequestMapping("/api/v1/organizations")
@RequiredArgsConstructor
@Tag(name = "Organizations", description = "Organization management endpoints")
public class OrganizationController {

    private final OrganizationService organizationService;

    @PostMapping
    @Operation(summary = "Create a new organization")
    public ResponseEntity<ApiResponse<OrganizationResponse>> create(
            @Valid @RequestBody OrganizationRequest request,
            @AuthenticationPrincipal User user
    ) {
        User currentUser = AuthenticatedUserResolver.resolve(user);
        OrganizationResponse response = organizationService.create(request, currentUser);
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponse.success(response, "Organization created successfully"));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get organization by ID")
    public ResponseEntity<ApiResponse<OrganizationResponse>> getById(
            @PathVariable Long id,
            @AuthenticationPrincipal User user
    ) {
        User currentUser = AuthenticatedUserResolver.resolve(user);
        OrganizationResponse response = organizationService.getById(id, currentUser);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @GetMapping("/slug/{slug}")
    @Operation(summary = "Get organization by slug")
    public ResponseEntity<ApiResponse<OrganizationResponse>> getBySlug(
            @PathVariable String slug,
            @AuthenticationPrincipal User user
    ) {
        User currentUser = AuthenticatedUserResolver.resolve(user);
        OrganizationResponse response = organizationService.getBySlug(slug, currentUser);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @GetMapping("/my")
    @Operation(summary = "Get current user's organizations")
    public ResponseEntity<ApiResponse<PageResponse<OrganizationResponse>>> getMyOrganizations(
            @PageableDefault(size = 20) Pageable pageable,
            @AuthenticationPrincipal User user,
            HttpServletRequest request
    ) {
        User currentUser = AuthenticatedUserResolver.resolve(user, request);
        PageResponse<OrganizationResponse> response = organizationService.getByUserId(currentUser.getId(), pageable);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update an organization")
    public ResponseEntity<ApiResponse<OrganizationResponse>> update(
            @PathVariable Long id,
            @Valid @RequestBody OrganizationRequest request,
            @AuthenticationPrincipal User user
    ) {
        User currentUser = AuthenticatedUserResolver.resolve(user);
        OrganizationResponse response = organizationService.update(id, request, currentUser);
        return ResponseEntity.ok(ApiResponse.success(response, "Organization updated successfully"));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete an organization")
    public ResponseEntity<ApiResponse<Void>> delete(
            @PathVariable Long id,
            @AuthenticationPrincipal User user
    ) {
        User currentUser = AuthenticatedUserResolver.resolve(user);
        organizationService.delete(id, currentUser);
        return ResponseEntity.ok(ApiResponse.success(null, "Organization deleted successfully"));
    }
}
