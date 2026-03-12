package dev.squadx.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.squadx.dto.rbac.AssignRoleRequest;
import dev.squadx.dto.rbac.CustomRoleRequest;
import dev.squadx.dto.rbac.CustomRoleResponse;
import dev.squadx.exception.GlobalExceptionHandler;
import dev.squadx.exception.ResourceNotFoundException;
import dev.squadx.model.User;
import dev.squadx.model.enums.PermissionAction;
import dev.squadx.model.enums.PermissionResource;
import dev.squadx.model.enums.UserRole;
import dev.squadx.security.JwtAuthenticationFilter;
import dev.squadx.security.JwtService;
import dev.squadx.service.RbacService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.bean.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(RbacController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(GlobalExceptionHandler.class)
class RbacControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private RbacService rbacService;

    @MockBean
    private JwtService jwtService;

    @MockBean
    private JwtAuthenticationFilter jwtAuthenticationFilter;

    @MockBean
    private UserDetailsService userDetailsService;

    private User testUser;
    private CustomRoleResponse sampleRole;

    @BeforeEach
    void setUp() {
        testUser = User.builder()
                .email("admin@example.com")
                .password("encoded")
                .fullName("Admin User")
                .role(UserRole.ADMIN)
                .build();
        testUser.setId(1L);

        sampleRole = CustomRoleResponse.builder()
                .id(1L)
                .organizationId(10L)
                .name("Reviewer")
                .description("Can review code")
                .permissions(List.of())
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();
    }

    @Nested
    @DisplayName("POST /api/v1/organizations/{orgId}/rbac/roles")
    class CreateRoleEndpoint {

        @Test
        @DisplayName("should create custom role and return 201")
        void shouldCreateRoleSuccessfully() throws Exception {
            CustomRoleRequest request = CustomRoleRequest.builder()
                    .name("Reviewer")
                    .description("Can review code")
                    .permissions(List.of())
                    .build();

            when(rbacService.createRole(eq(10L), any(CustomRoleRequest.class)))
                    .thenReturn(sampleRole);

            mockMvc.perform(post("/api/v1/organizations/10/rbac/roles")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request))
                            .with(user(testUser)))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.message").value("Custom role created successfully"))
                    .andExpect(jsonPath("$.data.name").value("Reviewer"));
        }
    }

    @Nested
    @DisplayName("GET /api/v1/organizations/{orgId}/rbac/roles")
    class ListRolesEndpoint {

        @Test
        @DisplayName("should return all roles for organization")
        void shouldReturnRoles() throws Exception {
            when(rbacService.getRolesByOrganization(10L)).thenReturn(List.of(sampleRole));

            mockMvc.perform(get("/api/v1/organizations/10/rbac/roles")
                            .with(user(testUser)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data[0].name").value("Reviewer"));
        }
    }

    @Nested
    @DisplayName("GET /api/v1/organizations/{orgId}/rbac/roles/{roleId}")
    class GetRoleByIdEndpoint {

        @Test
        @DisplayName("should return role by id")
        void shouldReturnRoleById() throws Exception {
            when(rbacService.getRoleById(10L, 1L)).thenReturn(sampleRole);

            mockMvc.perform(get("/api/v1/organizations/10/rbac/roles/1")
                            .with(user(testUser)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data.id").value(1))
                    .andExpect(jsonPath("$.data.name").value("Reviewer"));
        }
    }

    @Nested
    @DisplayName("PUT /api/v1/organizations/{orgId}/rbac/roles/{roleId}")
    class UpdateRoleEndpoint {

        @Test
        @DisplayName("should update role and return 200")
        void shouldUpdateRoleSuccessfully() throws Exception {
            CustomRoleRequest request = CustomRoleRequest.builder()
                    .name("Senior Reviewer")
                    .description("Updated description")
                    .permissions(List.of())
                    .build();

            CustomRoleResponse updated = CustomRoleResponse.builder()
                    .id(1L)
                    .organizationId(10L)
                    .name("Senior Reviewer")
                    .description("Updated description")
                    .build();

            when(rbacService.updateRole(eq(10L), eq(1L), any(CustomRoleRequest.class)))
                    .thenReturn(updated);

            mockMvc.perform(put("/api/v1/organizations/10/rbac/roles/1")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request))
                            .with(user(testUser)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.message").value("Custom role updated successfully"))
                    .andExpect(jsonPath("$.data.name").value("Senior Reviewer"));
        }
    }

    @Nested
    @DisplayName("DELETE /api/v1/organizations/{orgId}/rbac/roles/{roleId}")
    class DeleteRoleEndpoint {

        @Test
        @DisplayName("should delete role and return 200")
        void shouldDeleteRoleSuccessfully() throws Exception {
            doNothing().when(rbacService).deleteRole(10L, 1L);

            mockMvc.perform(delete("/api/v1/organizations/10/rbac/roles/1")
                            .with(user(testUser)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.message").value("Custom role deleted successfully"));
        }
    }

    @Nested
    @DisplayName("POST /api/v1/organizations/{orgId}/rbac/roles/{roleId}/assign")
    class AssignRoleEndpoint {

        @Test
        @DisplayName("should assign role to user and return 200")
        void shouldAssignRoleSuccessfully() throws Exception {
            AssignRoleRequest request = AssignRoleRequest.builder()
                    .userId(5L)
                    .roleId(1L)
                    .build();

            doNothing().when(rbacService).assignRoleToUser(10L, 5L, 1L);

            mockMvc.perform(post("/api/v1/organizations/10/rbac/roles/1/assign")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request))
                            .with(user(testUser)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.message").value("Role assigned successfully"));
        }
    }

    @Nested
    @DisplayName("GET /api/v1/organizations/{orgId}/rbac/users/{userId}/roles")
    class GetUserRolesEndpoint {

        @Test
        @DisplayName("should return roles for a user")
        void shouldReturnUserRoles() throws Exception {
            when(rbacService.getUserRoles(10L, 5L)).thenReturn(List.of(sampleRole));

            mockMvc.perform(get("/api/v1/organizations/10/rbac/users/5/roles")
                            .with(user(testUser)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data[0].name").value("Reviewer"));
        }
    }

    @Nested
    @DisplayName("GET /api/v1/organizations/{orgId}/rbac/check")
    class CheckPermissionEndpoint {

        @Test
        @DisplayName("should check permission and return allowed status")
        void shouldCheckPermission() throws Exception {
            when(rbacService.hasPermission(eq(1L), eq(10L),
                    eq(PermissionResource.TASKS), eq(PermissionAction.READ)))
                    .thenReturn(true);

            mockMvc.perform(get("/api/v1/organizations/10/rbac/check")
                            .param("resource", "TASKS")
                            .param("action", "READ")
                            .with(user(testUser)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data.allowed").value(true));
        }
    }
}
