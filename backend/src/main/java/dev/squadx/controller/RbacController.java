package dev.squadx.controller;

import dev.squadx.dto.common.ApiResponse;
import dev.squadx.dto.rbac.*;
import dev.squadx.model.User;
import dev.squadx.service.RbacService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/organizations/{orgId}/rbac")
@RequiredArgsConstructor
@Tag(name = "RBAC", description = "Role-Based Access Control management")
public class RbacController {

    private final RbacService rbacService;

    // ==================== Custom Roles ====================

    @PostMapping("/roles")
    @Operation(summary = "Create a custom role")
    @PreAuthorize("@permissionChecker.check(#user.id, #orgId, 'SETTINGS', 'CREATE')")
    public ResponseEntity<ApiResponse<CustomRoleResponse>> createRole(
            @PathVariable Long orgId,
            @Valid @RequestBody CustomRoleRequest request,
            @AuthenticationPrincipal User user
    ) {
        CustomRoleResponse response = rbacService.createRole(orgId, request);
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponse.success(response, "Custom role created successfully"));
    }

    @GetMapping("/roles")
    @Operation(summary = "List all custom roles for an organization")
    @PreAuthorize("@permissionChecker.check(#user.id, #orgId, 'SETTINGS', 'READ')")
    public ResponseEntity<ApiResponse<List<CustomRoleResponse>>> getRoles(
            @PathVariable Long orgId,
            @AuthenticationPrincipal User user
    ) {
        List<CustomRoleResponse> roles = rbacService.getRolesByOrganization(orgId);
        return ResponseEntity.ok(ApiResponse.success(roles));
    }

    @GetMapping("/roles/{roleId}")
    @Operation(summary = "Get a custom role by ID")
    @PreAuthorize("@permissionChecker.check(#user.id, #orgId, 'SETTINGS', 'READ')")
    public ResponseEntity<ApiResponse<CustomRoleResponse>> getRole(
            @PathVariable Long orgId,
            @PathVariable Long roleId,
            @AuthenticationPrincipal User user
    ) {
        CustomRoleResponse role = rbacService.getRoleById(orgId, roleId);
        return ResponseEntity.ok(ApiResponse.success(role));
    }

    @PutMapping("/roles/{roleId}")
    @Operation(summary = "Update a custom role")
    @PreAuthorize("@permissionChecker.check(#user.id, #orgId, 'SETTINGS', 'UPDATE')")
    public ResponseEntity<ApiResponse<CustomRoleResponse>> updateRole(
            @PathVariable Long orgId,
            @PathVariable Long roleId,
            @Valid @RequestBody CustomRoleRequest request,
            @AuthenticationPrincipal User user
    ) {
        CustomRoleResponse response = rbacService.updateRole(orgId, roleId, request);
        return ResponseEntity.ok(ApiResponse.success(response, "Custom role updated successfully"));
    }

    @DeleteMapping("/roles/{roleId}")
    @Operation(summary = "Delete a custom role")
    @PreAuthorize("@permissionChecker.check(#user.id, #orgId, 'SETTINGS', 'DELETE')")
    public ResponseEntity<ApiResponse<Void>> deleteRole(
            @PathVariable Long orgId,
            @PathVariable Long roleId,
            @AuthenticationPrincipal User user
    ) {
        rbacService.deleteRole(orgId, roleId);
        return ResponseEntity.ok(ApiResponse.success(null, "Custom role deleted successfully"));
    }

    // ==================== Role Assignment ====================

    @PostMapping("/roles/{roleId}/assign")
    @Operation(summary = "Assign a custom role to a user")
    @PreAuthorize("@permissionChecker.check(#user.id, #orgId, 'SETTINGS', 'UPDATE')")
    public ResponseEntity<ApiResponse<Void>> assignRole(
            @PathVariable Long orgId,
            @PathVariable Long roleId,
            @Valid @RequestBody AssignRoleRequest request,
            @AuthenticationPrincipal User user
    ) {
        rbacService.assignRoleToUser(orgId, request.getUserId(), roleId);
        return ResponseEntity.ok(ApiResponse.success(null, "Role assigned successfully"));
    }

    @DeleteMapping("/roles/{roleId}/users/{userId}")
    @Operation(summary = "Remove a custom role from a user")
    @PreAuthorize("@permissionChecker.check(#user.id, #orgId, 'SETTINGS', 'UPDATE')")
    public ResponseEntity<ApiResponse<Void>> removeRole(
            @PathVariable Long orgId,
            @PathVariable Long roleId,
            @PathVariable Long userId,
            @AuthenticationPrincipal User user
    ) {
        rbacService.removeRoleFromUser(orgId, userId, roleId);
        return ResponseEntity.ok(ApiResponse.success(null, "Role removed successfully"));
    }

    @GetMapping("/users/{userId}/roles")
    @Operation(summary = "Get all custom roles assigned to a user")
    @PreAuthorize("@permissionChecker.check(#user.id, #orgId, 'SETTINGS', 'READ')")
    public ResponseEntity<ApiResponse<List<CustomRoleResponse>>> getUserRoles(
            @PathVariable Long orgId,
            @PathVariable Long userId,
            @AuthenticationPrincipal User user
    ) {
        List<CustomRoleResponse> roles = rbacService.getUserRoles(orgId, userId);
        return ResponseEntity.ok(ApiResponse.success(roles));
    }

    // ==================== Permission Check ====================

    @GetMapping("/check")
    @Operation(summary = "Check if the current user has a specific permission")
    public ResponseEntity<ApiResponse<Map<String, Boolean>>> checkPermission(
            @PathVariable Long orgId,
            @RequestParam String resource,
            @RequestParam String action,
            @AuthenticationPrincipal User user
    ) {
        boolean hasPermission = rbacService.hasPermission(
                user.getId(), orgId,
                dev.squadx.model.enums.PermissionResource.valueOf(resource.toUpperCase()),
                dev.squadx.model.enums.PermissionAction.valueOf(action.toUpperCase())
        );
        return ResponseEntity.ok(ApiResponse.success(Map.of("allowed", hasPermission)));
    }
}
