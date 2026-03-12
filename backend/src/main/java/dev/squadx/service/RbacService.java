package dev.squadx.service;

import dev.squadx.dto.rbac.*;
import dev.squadx.exception.BadRequestException;
import dev.squadx.exception.ResourceNotFoundException;
import dev.squadx.model.*;
import dev.squadx.model.enums.OrgRole;
import dev.squadx.model.enums.PermissionAction;
import dev.squadx.model.enums.PermissionResource;
import dev.squadx.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class RbacService {

    private final CustomRoleRepository customRoleRepository;
    private final RolePermissionRepository rolePermissionRepository;
    private final UserCustomRoleRepository userCustomRoleRepository;
    private final OrganizationRepository organizationRepository;
    private final OrganizationMemberRepository organizationMemberRepository;

    // ==================== Custom Role CRUD ====================

    @Transactional
    public CustomRoleResponse createRole(Long orgId, CustomRoleRequest request) {
        Organization org = organizationRepository.findById(orgId)
                .orElseThrow(() -> new ResourceNotFoundException("Organization not found"));

        if (customRoleRepository.existsByOrganizationIdAndName(orgId, request.getName())) {
            throw new BadRequestException("A role with this name already exists in the organization");
        }

        CustomRole role = CustomRole.builder()
                .organization(org)
                .name(request.getName())
                .description(request.getDescription())
                .build();

        role = customRoleRepository.save(role);

        if (request.getPermissions() != null) {
            for (CustomRoleRequest.PermissionEntry entry : request.getPermissions()) {
                RolePermission permission = RolePermission.builder()
                        .role(role)
                        .resource(PermissionResource.valueOf(entry.getResource().toUpperCase()))
                        .action(PermissionAction.valueOf(entry.getAction().toUpperCase()))
                        .build();
                rolePermissionRepository.save(permission);
            }
        }

        return mapToRoleResponse(customRoleRepository.findById(role.getId()).orElseThrow());
    }

    @Transactional(readOnly = true)
    public List<CustomRoleResponse> getRolesByOrganization(Long orgId) {
        return customRoleRepository.findByOrganizationId(orgId).stream()
                .map(this::mapToRoleResponse)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public CustomRoleResponse getRoleById(Long orgId, Long roleId) {
        CustomRole role = customRoleRepository.findById(roleId)
                .orElseThrow(() -> new ResourceNotFoundException("Custom role not found"));

        if (!role.getOrganization().getId().equals(orgId)) {
            throw new ResourceNotFoundException("Custom role not found in this organization");
        }

        return mapToRoleResponse(role);
    }

    @Transactional
    public CustomRoleResponse updateRole(Long orgId, Long roleId, CustomRoleRequest request) {
        CustomRole role = customRoleRepository.findById(roleId)
                .orElseThrow(() -> new ResourceNotFoundException("Custom role not found"));

        if (!role.getOrganization().getId().equals(orgId)) {
            throw new ResourceNotFoundException("Custom role not found in this organization");
        }

        role.setName(request.getName());
        role.setDescription(request.getDescription());

        // Replace permissions
        rolePermissionRepository.deleteByRoleId(roleId);

        if (request.getPermissions() != null) {
            for (CustomRoleRequest.PermissionEntry entry : request.getPermissions()) {
                RolePermission permission = RolePermission.builder()
                        .role(role)
                        .resource(PermissionResource.valueOf(entry.getResource().toUpperCase()))
                        .action(PermissionAction.valueOf(entry.getAction().toUpperCase()))
                        .build();
                rolePermissionRepository.save(permission);
            }
        }

        role = customRoleRepository.save(role);
        return mapToRoleResponse(customRoleRepository.findById(role.getId()).orElseThrow());
    }

    @Transactional
    public void deleteRole(Long orgId, Long roleId) {
        CustomRole role = customRoleRepository.findById(roleId)
                .orElseThrow(() -> new ResourceNotFoundException("Custom role not found"));

        if (!role.getOrganization().getId().equals(orgId)) {
            throw new ResourceNotFoundException("Custom role not found in this organization");
        }

        customRoleRepository.delete(role);
    }

    // ==================== Role Assignment ====================

    @Transactional
    public void assignRoleToUser(Long orgId, Long userId, Long roleId) {
        customRoleRepository.findById(roleId)
                .orElseThrow(() -> new ResourceNotFoundException("Custom role not found"));

        UserCustomRole assignment = UserCustomRole.builder()
                .userId(userId)
                .customRoleId(roleId)
                .organizationId(orgId)
                .build();

        userCustomRoleRepository.save(assignment);
        log.info("Assigned custom role {} to user {} in org {}", roleId, userId, orgId);
    }

    @Transactional
    public void removeRoleFromUser(Long orgId, Long userId, Long roleId) {
        userCustomRoleRepository.deleteByUserIdAndCustomRoleIdAndOrganizationId(userId, roleId, orgId);
        log.info("Removed custom role {} from user {} in org {}", roleId, userId, orgId);
    }

    @Transactional(readOnly = true)
    public List<CustomRoleResponse> getUserRoles(Long orgId, Long userId) {
        return userCustomRoleRepository.findByUserIdAndOrganizationId(userId, orgId).stream()
                .map(ucr -> mapToRoleResponse(ucr.getCustomRole()))
                .collect(Collectors.toList());
    }

    // ==================== Permission Check ====================

    /**
     * Check if a user has a specific permission within an organization.
     * Priority: OrgRole (OWNER > ADMIN > MEMBER) then custom roles.
     */
    @Transactional(readOnly = true)
    public boolean hasPermission(Long userId, Long orgId, PermissionResource resource, PermissionAction action) {
        OrganizationMember member = organizationMemberRepository
                .findByOrganizationIdAndUserId(orgId, userId)
                .orElse(null);

        if (member == null) {
            return false;
        }

        OrgRole orgRole = member.getRole();

        // OWNER has all permissions
        if (orgRole == OrgRole.OWNER) {
            return true;
        }

        // ADMIN has all permissions except BILLING and SETTINGS delete
        if (orgRole == OrgRole.ADMIN) {
            if (resource == PermissionResource.BILLING && action == PermissionAction.DELETE) {
                return false;
            }
            return true;
        }

        // VIEWER can only read
        if (orgRole == OrgRole.VIEWER) {
            return action == PermissionAction.READ;
        }

        // MEMBER: check custom roles
        return userCustomRoleRepository.hasPermissionThroughCustomRole(userId, orgId, resource, action);
    }

    // ==================== Mapping ====================

    private CustomRoleResponse mapToRoleResponse(CustomRole role) {
        List<PermissionResponse> permissions = role.getPermissions().stream()
                .map(p -> PermissionResponse.builder()
                        .id(p.getId())
                        .resource(p.getResource().name())
                        .action(p.getAction().name())
                        .build())
                .collect(Collectors.toList());

        return CustomRoleResponse.builder()
                .id(role.getId())
                .organizationId(role.getOrganization().getId())
                .name(role.getName())
                .description(role.getDescription())
                .permissions(permissions)
                .createdAt(role.getCreatedAt())
                .updatedAt(role.getUpdatedAt())
                .build();
    }
}
