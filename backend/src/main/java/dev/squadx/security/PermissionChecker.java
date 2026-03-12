package dev.squadx.security;

import dev.squadx.model.enums.PermissionAction;
import dev.squadx.model.enums.PermissionResource;
import dev.squadx.service.RbacService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Spring component for permission checks, usable with @PreAuthorize SpEL expressions.
 *
 * Usage in controllers:
 * <pre>
 * @PreAuthorize("@permissionChecker.check(#user.id, #orgId, 'TASKS', 'CREATE')")
 * </pre>
 */
@Component("permissionChecker")
@RequiredArgsConstructor
@Slf4j
public class PermissionChecker {

    private final RbacService rbacService;

    /**
     * Check if a user has a specific permission in an organization.
     *
     * @param userId   the user's ID
     * @param orgId    the organization's ID
     * @param resource the resource name (must match PermissionResource enum)
     * @param action   the action name (must match PermissionAction enum)
     * @return true if the user has the permission
     */
    public boolean check(Long userId, Long orgId, String resource, String action) {
        try {
            PermissionResource permResource = PermissionResource.valueOf(resource.toUpperCase());
            PermissionAction permAction = PermissionAction.valueOf(action.toUpperCase());
            boolean result = rbacService.hasPermission(userId, orgId, permResource, permAction);
            log.debug("Permission check: user={}, org={}, resource={}, action={} -> {}",
                    userId, orgId, resource, action, result);
            return result;
        } catch (IllegalArgumentException e) {
            log.warn("Invalid permission check: resource={}, action={}", resource, action);
            return false;
        }
    }

    /**
     * Overload accepting enum values directly for programmatic use.
     */
    public boolean check(Long userId, Long orgId, PermissionResource resource, PermissionAction action) {
        return rbacService.hasPermission(userId, orgId, resource, action);
    }
}
