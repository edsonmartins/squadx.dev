package dev.squadx.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.squadx.dto.common.PageResponse;
import dev.squadx.dto.organization.OrganizationRequest;
import dev.squadx.dto.organization.OrganizationResponse;
import dev.squadx.exception.ForbiddenException;
import dev.squadx.exception.GlobalExceptionHandler;
import dev.squadx.exception.ResourceNotFoundException;
import dev.squadx.model.User;
import dev.squadx.model.enums.UserRole;
import dev.squadx.security.JwtAuthenticationFilter;
import dev.squadx.security.JwtService;
import dev.squadx.service.OrganizationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(
        value = OrganizationController.class,
        excludeAutoConfiguration = org.springframework.boot.autoconfigure.security.oauth2.client.servlet.OAuth2ClientAutoConfiguration.class
)
@AutoConfigureMockMvc(addFilters = false)
@Import(GlobalExceptionHandler.class)
class OrganizationControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private OrganizationService organizationService;

    @MockBean
    private JwtService jwtService;

    @MockBean
    private JwtAuthenticationFilter jwtAuthenticationFilter;

    @MockBean
    private UserDetailsService userDetailsService;

    private User testUser;
    private OrganizationResponse sampleOrganization;

    @BeforeEach
    void setUp() {
        testUser = User.builder()
                .email("test@example.com")
                .password("encoded")
                .fullName("Test User")
                .role(UserRole.USER)
                .build();
        testUser.setId(1L);

        sampleOrganization = OrganizationResponse.builder()
                .id(1L)
                .name("Test Organization")
                .slug("test-organization")
                .description("A test organization")
                .isActive(true)
                .membersCount(5)
                .projectsCount(3)
                .squadsCount(2)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();
    }

    @Nested
    @DisplayName("POST /api/v1/organizations")
    class CreateEndpoint {

        @Test
        @DisplayName("should create organization successfully and return 201")
        void shouldCreateOrganizationSuccessfully() throws Exception {
            OrganizationRequest request = OrganizationRequest.builder()
                    .name("Test Organization")
                    .description("A test organization")
                    .build();

            when(organizationService.create(any(OrganizationRequest.class), nullable(User.class)))
                    .thenReturn(sampleOrganization);

            mockMvc.perform(post("/api/v1/organizations")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request))
                            .with(authentication(new UsernamePasswordAuthenticationToken(testUser, null, testUser.getAuthorities()))))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data.id").value(1))
                    .andExpect(jsonPath("$.data.name").value("Test Organization"))
                    .andExpect(jsonPath("$.message").value("Organization created successfully"));
        }
    }

    @Nested
    @DisplayName("GET /api/v1/organizations/{id}")
    class GetByIdEndpoint {

        @Test
        @DisplayName("should return organization by id with 200")
        void shouldReturnOrganizationById() throws Exception {
            when(organizationService.getById(eq(1L), nullable(User.class)))
                    .thenReturn(sampleOrganization);

            mockMvc.perform(get("/api/v1/organizations/1")
                            .with(authentication(new UsernamePasswordAuthenticationToken(testUser, null, testUser.getAuthorities()))))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data.id").value(1))
                    .andExpect(jsonPath("$.data.name").value("Test Organization"));
        }

        @Test
        @DisplayName("should return 404 when organization not found")
        void shouldReturn404WhenNotFound() throws Exception {
            when(organizationService.getById(eq(999L), nullable(User.class)))
                    .thenThrow(new ResourceNotFoundException("Organization not found"));

            mockMvc.perform(get("/api/v1/organizations/999")
                            .with(authentication(new UsernamePasswordAuthenticationToken(testUser, null, testUser.getAuthorities()))))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.success").value(false))
                    .andExpect(jsonPath("$.message").value("Organization not found"));
        }
    }

    @Nested
    @DisplayName("GET /api/v1/organizations/slug/{slug}")
    class GetBySlugEndpoint {

        @Test
        @DisplayName("should return organization by slug with 200")
        void shouldReturnOrganizationBySlug() throws Exception {
            when(organizationService.getBySlug(eq("test-organization"), nullable(User.class)))
                    .thenReturn(sampleOrganization);

            mockMvc.perform(get("/api/v1/organizations/slug/test-organization")
                            .with(authentication(new UsernamePasswordAuthenticationToken(testUser, null, testUser.getAuthorities()))))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data.id").value(1))
                    .andExpect(jsonPath("$.data.slug").value("test-organization"));
        }
    }

    @Nested
    @DisplayName("GET /api/v1/organizations/my")
    class GetMyOrganizationsEndpoint {

        @Test
        @DisplayName("should return current user organizations with 200")
        void shouldReturnMyOrganizations() throws Exception {
            PageResponse<OrganizationResponse> pageResponse = PageResponse.<OrganizationResponse>builder()
                    .content(List.of(sampleOrganization))
                    .pageNumber(0)
                    .pageSize(20)
                    .totalElements(1)
                    .totalPages(1)
                    .isFirst(true)
                    .isLast(true)
                    .build();

            when(organizationService.getByUserId(eq(1L), any()))
                    .thenReturn(pageResponse);

            mockMvc.perform(get("/api/v1/organizations/my")
                            .with(authentication(new UsernamePasswordAuthenticationToken(testUser, null, testUser.getAuthorities()))))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data.content[0].id").value(1))
                    .andExpect(jsonPath("$.data.total_elements").value(1));
        }
    }

    @Nested
    @DisplayName("PUT /api/v1/organizations/{id}")
    class UpdateEndpoint {

        @Test
        @DisplayName("should update organization successfully and return 200")
        void shouldUpdateOrganizationSuccessfully() throws Exception {
            OrganizationRequest request = OrganizationRequest.builder()
                    .name("Updated Organization")
                    .description("Updated description")
                    .build();

            OrganizationResponse updatedOrg = OrganizationResponse.builder()
                    .id(1L)
                    .name("Updated Organization")
                    .description("Updated description")
                    .build();

            when(organizationService.update(eq(1L), any(OrganizationRequest.class), nullable(User.class)))
                    .thenReturn(updatedOrg);

            mockMvc.perform(put("/api/v1/organizations/1")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request))
                            .with(authentication(new UsernamePasswordAuthenticationToken(testUser, null, testUser.getAuthorities()))))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data.id").value(1))
                    .andExpect(jsonPath("$.data.name").value("Updated Organization"))
                    .andExpect(jsonPath("$.message").value("Organization updated successfully"));
        }

        @Test
        @DisplayName("should return 403 when user is not allowed to update")
        void shouldReturn403WhenForbidden() throws Exception {
            OrganizationRequest request = OrganizationRequest.builder()
                    .name("Updated Organization")
                    .build();

            when(organizationService.update(eq(1L), any(OrganizationRequest.class), nullable(User.class)))
                    .thenThrow(new ForbiddenException("You don't have permission to update this organization"));

            mockMvc.perform(put("/api/v1/organizations/1")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request))
                            .with(authentication(new UsernamePasswordAuthenticationToken(testUser, null, testUser.getAuthorities()))))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.success").value(false))
                    .andExpect(jsonPath("$.message").value("You don't have permission to update this organization"));
        }
    }

    @Nested
    @DisplayName("DELETE /api/v1/organizations/{id}")
    class DeleteEndpoint {

        @Test
        @DisplayName("should delete organization successfully and return 200")
        void shouldDeleteOrganizationSuccessfully() throws Exception {
            doNothing().when(organizationService).delete(eq(1L), nullable(User.class));

            mockMvc.perform(delete("/api/v1/organizations/1")
                            .with(authentication(new UsernamePasswordAuthenticationToken(testUser, null, testUser.getAuthorities()))))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.message").value("Organization deleted successfully"));
        }
    }
}
