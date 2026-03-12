package dev.squadx.service;

import dev.squadx.dto.rbac.CustomRoleRequest;
import dev.squadx.dto.rbac.CustomRoleResponse;
import dev.squadx.exception.BadRequestException;
import dev.squadx.exception.ResourceNotFoundException;
import dev.squadx.model.*;
import dev.squadx.model.enums.OrgRole;
import dev.squadx.model.enums.PermissionAction;
import dev.squadx.model.enums.PermissionResource;
import dev.squadx.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RbacServiceTest {

    @Mock
    private CustomRoleRepository customRoleRepository;

    @Mock
    private RolePermissionRepository rolePermissionRepository;

    @Mock
    private UserCustomRoleRepository userCustomRoleRepository;

    @Mock
    private OrganizationRepository organizationRepository;

    @Mock
    private OrganizationMemberRepository organizationMemberRepository;

    @InjectMocks
    private RbacService rbacService;

    private Organization testOrg;
    private CustomRole testRole;

    @BeforeEach
    void setUp() {
        testOrg = Organization.builder()
                .name("Test Org")
                .slug("test-org")
                .build();
        testOrg.setId(1L);

        testRole = CustomRole.builder()
                .organization(testOrg)
                .name("Developer")
                .description("Developer role")
                .permissions(new HashSet<>())
                .build();
        testRole.setId(10L);
        testRole.setCreatedAt(Instant.now());
        testRole.setUpdatedAt(Instant.now());
    }

    @Nested
    @DisplayName("createRole()")
    class CreateRole {

        @Test
        @DisplayName("should create a custom role with permissions")
        void shouldCreateRoleWithPermissions() {
            CustomRoleRequest request = CustomRoleRequest.builder()
                    .name("Developer")
                    .description("Developer role")
                    .permissions(List.of(
                            CustomRoleRequest.PermissionEntry.builder()
                                    .resource("TASKS")
                                    .action("CREATE")
                                    .build()
                    ))
                    .build();

            when(organizationRepository.findById(1L)).thenReturn(Optional.of(testOrg));
            when(customRoleRepository.existsByOrganizationIdAndName(1L, "Developer")).thenReturn(false);
            when(customRoleRepository.save(any(CustomRole.class))).thenAnswer(invocation -> {
                CustomRole saved = invocation.getArgument(0);
                saved.setId(10L);
                saved.setCreatedAt(Instant.now());
                saved.setUpdatedAt(Instant.now());
                return saved;
            });
            when(rolePermissionRepository.save(any(RolePermission.class))).thenAnswer(invocation -> invocation.getArgument(0));
            when(customRoleRepository.findById(10L)).thenReturn(Optional.of(testRole));

            CustomRoleResponse response = rbacService.createRole(1L, request);

            assertThat(response).isNotNull();
            assertThat(response.getName()).isEqualTo("Developer");
            verify(customRoleRepository).save(any(CustomRole.class));
            verify(rolePermissionRepository).save(any(RolePermission.class));
        }

        @Test
        @DisplayName("should throw BadRequestException when role name already exists")
        void shouldThrowWhenDuplicateName() {
            CustomRoleRequest request = CustomRoleRequest.builder()
                    .name("Developer")
                    .build();

            when(organizationRepository.findById(1L)).thenReturn(Optional.of(testOrg));
            when(customRoleRepository.existsByOrganizationIdAndName(1L, "Developer")).thenReturn(true);

            assertThatThrownBy(() -> rbacService.createRole(1L, request))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessageContaining("already exists");

            verify(customRoleRepository, never()).save(any(CustomRole.class));
        }

        @Test
        @DisplayName("should throw ResourceNotFoundException when organization not found")
        void shouldThrowWhenOrgNotFound() {
            CustomRoleRequest request = CustomRoleRequest.builder()
                    .name("Developer")
                    .build();

            when(organizationRepository.findById(99L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> rbacService.createRole(99L, request))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining("Organization not found");
        }
    }

    @Nested
    @DisplayName("getRoleById()")
    class GetRoleById {

        @Test
        @DisplayName("should return role when found in the organization")
        void shouldReturnRoleWhenFound() {
            when(customRoleRepository.findById(10L)).thenReturn(Optional.of(testRole));

            CustomRoleResponse response = rbacService.getRoleById(1L, 10L);

            assertThat(response).isNotNull();
            assertThat(response.getId()).isEqualTo(10L);
            assertThat(response.getName()).isEqualTo("Developer");
        }

        @Test
        @DisplayName("should throw ResourceNotFoundException when role belongs to different org")
        void shouldThrowWhenRoleInDifferentOrg() {
            when(customRoleRepository.findById(10L)).thenReturn(Optional.of(testRole));

            assertThatThrownBy(() -> rbacService.getRoleById(999L, 10L))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining("not found in this organization");
        }
    }

    @Nested
    @DisplayName("getRolesByOrganization()")
    class GetRolesByOrganization {

        @Test
        @DisplayName("should return all roles for an organization")
        void shouldReturnAllRolesForOrg() {
            when(customRoleRepository.findByOrganizationId(1L)).thenReturn(List.of(testRole));

            List<CustomRoleResponse> roles = rbacService.getRolesByOrganization(1L);

            assertThat(roles).hasSize(1);
            assertThat(roles.get(0).getName()).isEqualTo("Developer");
            verify(customRoleRepository).findByOrganizationId(1L);
        }
    }

    @Nested
    @DisplayName("updateRole()")
    class UpdateRole {

        @Test
        @DisplayName("should update role name, description and permissions")
        void shouldUpdateRole() {
            CustomRoleRequest request = CustomRoleRequest.builder()
                    .name("Senior Developer")
                    .description("Updated description")
                    .permissions(List.of(
                            CustomRoleRequest.PermissionEntry.builder()
                                    .resource("PROJECTS")
                                    .action("UPDATE")
                                    .build()
                    ))
                    .build();

            when(customRoleRepository.findById(10L)).thenReturn(Optional.of(testRole));
            when(customRoleRepository.save(any(CustomRole.class))).thenReturn(testRole);
            when(customRoleRepository.findById(10L)).thenReturn(Optional.of(testRole));

            CustomRoleResponse response = rbacService.updateRole(1L, 10L, request);

            assertThat(response).isNotNull();
            verify(rolePermissionRepository).deleteByRoleId(10L);
            verify(rolePermissionRepository).save(any(RolePermission.class));
            verify(customRoleRepository).save(any(CustomRole.class));
        }
    }

    @Nested
    @DisplayName("deleteRole()")
    class DeleteRole {

        @Test
        @DisplayName("should delete a custom role")
        void shouldDeleteRole() {
            when(customRoleRepository.findById(10L)).thenReturn(Optional.of(testRole));

            rbacService.deleteRole(1L, 10L);

            verify(customRoleRepository).delete(testRole);
        }

        @Test
        @DisplayName("should throw ResourceNotFoundException when role not found")
        void shouldThrowWhenRoleNotFound() {
            when(customRoleRepository.findById(99L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> rbacService.deleteRole(1L, 99L))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining("Custom role not found");
        }
    }

    @Nested
    @DisplayName("assignRoleToUser()")
    class AssignRoleToUser {

        @Test
        @DisplayName("should assign a custom role to a user")
        void shouldAssignRole() {
            when(customRoleRepository.findById(10L)).thenReturn(Optional.of(testRole));

            rbacService.assignRoleToUser(1L, 5L, 10L);

            verify(userCustomRoleRepository).save(any(UserCustomRole.class));
        }

        @Test
        @DisplayName("should throw ResourceNotFoundException when role not found")
        void shouldThrowWhenRoleNotFound() {
            when(customRoleRepository.findById(99L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> rbacService.assignRoleToUser(1L, 5L, 99L))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining("Custom role not found");

            verify(userCustomRoleRepository, never()).save(any());
        }
    }

    @Nested
    @DisplayName("removeRoleFromUser()")
    class RemoveRoleFromUser {

        @Test
        @DisplayName("should remove a custom role from a user")
        void shouldRemoveRole() {
            rbacService.removeRoleFromUser(1L, 5L, 10L);

            verify(userCustomRoleRepository).deleteByUserIdAndCustomRoleIdAndOrganizationId(5L, 10L, 1L);
        }
    }

    @Nested
    @DisplayName("hasPermission()")
    class HasPermission {

        @Test
        @DisplayName("should return true for OWNER regardless of resource/action")
        void shouldReturnTrueForOwner() {
            OrganizationMember member = OrganizationMember.builder()
                    .role(OrgRole.OWNER)
                    .build();

            when(organizationMemberRepository.findByOrganizationIdAndUserId(1L, 5L))
                    .thenReturn(Optional.of(member));

            boolean result = rbacService.hasPermission(5L, 1L, PermissionResource.BILLING, PermissionAction.DELETE);

            assertThat(result).isTrue();
        }

        @Test
        @DisplayName("should return true for ADMIN except BILLING DELETE")
        void shouldReturnTrueForAdminExceptBillingDelete() {
            OrganizationMember member = OrganizationMember.builder()
                    .role(OrgRole.ADMIN)
                    .build();

            when(organizationMemberRepository.findByOrganizationIdAndUserId(1L, 5L))
                    .thenReturn(Optional.of(member));

            assertThat(rbacService.hasPermission(5L, 1L, PermissionResource.TASKS, PermissionAction.CREATE)).isTrue();
            assertThat(rbacService.hasPermission(5L, 1L, PermissionResource.BILLING, PermissionAction.DELETE)).isFalse();
        }

        @Test
        @DisplayName("should return true for VIEWER only on READ actions")
        void shouldReturnTrueForViewerOnlyRead() {
            OrganizationMember member = OrganizationMember.builder()
                    .role(OrgRole.VIEWER)
                    .build();

            when(organizationMemberRepository.findByOrganizationIdAndUserId(1L, 5L))
                    .thenReturn(Optional.of(member));

            assertThat(rbacService.hasPermission(5L, 1L, PermissionResource.TASKS, PermissionAction.READ)).isTrue();
            assertThat(rbacService.hasPermission(5L, 1L, PermissionResource.TASKS, PermissionAction.CREATE)).isFalse();
        }

        @Test
        @DisplayName("should fall back to custom role check for MEMBER")
        void shouldCheckCustomRoleForMember() {
            OrganizationMember member = OrganizationMember.builder()
                    .role(OrgRole.MEMBER)
                    .build();

            when(organizationMemberRepository.findByOrganizationIdAndUserId(1L, 5L))
                    .thenReturn(Optional.of(member));
            when(userCustomRoleRepository.hasPermissionThroughCustomRole(
                    5L, 1L, PermissionResource.TASKS, PermissionAction.CREATE))
                    .thenReturn(true);

            boolean result = rbacService.hasPermission(5L, 1L, PermissionResource.TASKS, PermissionAction.CREATE);

            assertThat(result).isTrue();
            verify(userCustomRoleRepository).hasPermissionThroughCustomRole(
                    5L, 1L, PermissionResource.TASKS, PermissionAction.CREATE);
        }

        @Test
        @DisplayName("should return false when user is not a member of the organization")
        void shouldReturnFalseWhenNotMember() {
            when(organizationMemberRepository.findByOrganizationIdAndUserId(1L, 5L))
                    .thenReturn(Optional.empty());

            boolean result = rbacService.hasPermission(5L, 1L, PermissionResource.TASKS, PermissionAction.READ);

            assertThat(result).isFalse();
        }
    }
}
