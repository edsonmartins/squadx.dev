package dev.squadx.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.squadx.dto.common.PageResponse;
import dev.squadx.dto.project.ProjectRequest;
import dev.squadx.dto.project.ProjectResponse;
import dev.squadx.exception.GlobalExceptionHandler;
import dev.squadx.exception.ResourceNotFoundException;
import dev.squadx.model.User;
import dev.squadx.model.enums.UserRole;
import dev.squadx.security.JwtAuthenticationFilter;
import dev.squadx.security.JwtService;
import dev.squadx.service.ProjectService;
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
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(
        value = ProjectController.class,
        excludeAutoConfiguration = org.springframework.boot.autoconfigure.security.oauth2.client.servlet.OAuth2ClientAutoConfiguration.class
)
@AutoConfigureMockMvc(addFilters = false)
@Import(GlobalExceptionHandler.class)
class ProjectControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private ProjectService projectService;

    @MockBean
    private JwtService jwtService;

    @MockBean
    private JwtAuthenticationFilter jwtAuthenticationFilter;

    @MockBean
    private UserDetailsService userDetailsService;

    private User testUser;
    private ProjectResponse sampleProject;

    @BeforeEach
    void setUp() {
        testUser = User.builder()
                .email("test@example.com")
                .password("encoded")
                .fullName("Test User")
                .role(UserRole.USER)
                .build();
        testUser.setId(1L);

        sampleProject = ProjectResponse.builder()
                .id(1L)
                .name("Test Project")
                .slug("test-project")
                .description("A test project")
                .repositoryUrl("https://github.com/test/repo")
                .defaultBranch("main")
                .isActive(true)
                .organizationId(10L)
                .organizationName("Test Org")
                .tasksCount(5)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();
    }

    @Nested
    @DisplayName("POST /api/v1/projects")
    class CreateEndpoint {

        @Test
        @DisplayName("should create project successfully and return 201")
        void shouldCreateProjectSuccessfully() throws Exception {
            ProjectRequest request = ProjectRequest.builder()
                    .name("Test Project")
                    .description("A test project")
                    .organizationId(10L)
                    .build();

            when(projectService.create(any(ProjectRequest.class), nullable(User.class)))
                    .thenReturn(sampleProject);

            mockMvc.perform(post("/api/v1/projects")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request))
                            .with(authentication(new UsernamePasswordAuthenticationToken(testUser, null, testUser.getAuthorities()))))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data.id").value(1))
                    .andExpect(jsonPath("$.data.name").value("Test Project"))
                    .andExpect(jsonPath("$.message").value("Project created successfully"));
        }

        @Test
        @DisplayName("should return 400 when validation fails")
        void shouldReturn400WhenValidationFails() throws Exception {
            ProjectRequest request = ProjectRequest.builder()
                    .name("")
                    .build();

            mockMvc.perform(post("/api/v1/projects")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request))
                            .with(authentication(new UsernamePasswordAuthenticationToken(testUser, null, testUser.getAuthorities()))))
                    .andExpect(status().isBadRequest());
        }
    }

    @Nested
    @DisplayName("GET /api/v1/projects/{id}")
    class GetByIdEndpoint {

        @Test
        @DisplayName("should return project by id with 200")
        void shouldReturnProjectById() throws Exception {
            when(projectService.getById(eq(1L), nullable(User.class)))
                    .thenReturn(sampleProject);

            mockMvc.perform(get("/api/v1/projects/1")
                            .with(authentication(new UsernamePasswordAuthenticationToken(testUser, null, testUser.getAuthorities()))))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data.id").value(1))
                    .andExpect(jsonPath("$.data.name").value("Test Project"));
        }

        @Test
        @DisplayName("should return 404 when project not found")
        void shouldReturn404WhenNotFound() throws Exception {
            when(projectService.getById(eq(999L), nullable(User.class)))
                    .thenThrow(new ResourceNotFoundException("Project not found"));

            mockMvc.perform(get("/api/v1/projects/999")
                            .with(authentication(new UsernamePasswordAuthenticationToken(testUser, null, testUser.getAuthorities()))))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.success").value(false))
                    .andExpect(jsonPath("$.message").value("Project not found"));
        }
    }

    @Nested
    @DisplayName("GET /api/v1/projects/organization/{organizationId}")
    class GetByOrganizationEndpoint {

        @Test
        @DisplayName("should return projects by organization with 200")
        void shouldReturnProjectsByOrganization() throws Exception {
            PageResponse<ProjectResponse> pageResponse = PageResponse.<ProjectResponse>builder()
                    .content(List.of(sampleProject))
                    .pageNumber(0)
                    .pageSize(20)
                    .totalElements(1)
                    .totalPages(1)
                    .isFirst(true)
                    .isLast(true)
                    .build();

            when(projectService.getByOrganizationId(eq(10L), any(), nullable(User.class)))
                    .thenReturn(pageResponse);

            mockMvc.perform(get("/api/v1/projects/organization/10")
                            .with(authentication(new UsernamePasswordAuthenticationToken(testUser, null, testUser.getAuthorities()))))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data.content[0].id").value(1))
                    .andExpect(jsonPath("$.data.total_elements").value(1));
        }
    }

    @Nested
    @DisplayName("GET /api/v1/projects/my")
    class GetMyProjectsEndpoint {

        @Test
        @DisplayName("should return current user projects with 200")
        void shouldReturnMyProjects() throws Exception {
            PageResponse<ProjectResponse> pageResponse = PageResponse.<ProjectResponse>builder()
                    .content(List.of(sampleProject))
                    .pageNumber(0)
                    .pageSize(20)
                    .totalElements(1)
                    .totalPages(1)
                    .isFirst(true)
                    .isLast(true)
                    .build();

            when(projectService.getByUserId(eq(1L), any()))
                    .thenReturn(pageResponse);

            mockMvc.perform(get("/api/v1/projects/my")
                            .with(authentication(new UsernamePasswordAuthenticationToken(testUser, null, testUser.getAuthorities()))))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data.content[0].id").value(1));
        }
    }

    @Nested
    @DisplayName("PUT /api/v1/projects/{id}")
    class UpdateEndpoint {

        @Test
        @DisplayName("should update project successfully and return 200")
        void shouldUpdateProjectSuccessfully() throws Exception {
            ProjectRequest request = ProjectRequest.builder()
                    .name("Updated Project")
                    .description("Updated description")
                    .organizationId(10L)
                    .build();

            ProjectResponse updatedProject = ProjectResponse.builder()
                    .id(1L)
                    .name("Updated Project")
                    .description("Updated description")
                    .organizationId(10L)
                    .build();

            when(projectService.update(eq(1L), any(ProjectRequest.class), nullable(User.class)))
                    .thenReturn(updatedProject);

            mockMvc.perform(put("/api/v1/projects/1")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request))
                            .with(authentication(new UsernamePasswordAuthenticationToken(testUser, null, testUser.getAuthorities()))))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data.id").value(1))
                    .andExpect(jsonPath("$.data.name").value("Updated Project"))
                    .andExpect(jsonPath("$.message").value("Project updated successfully"));
        }
    }

    @Nested
    @DisplayName("DELETE /api/v1/projects/{id}")
    class DeleteEndpoint {

        @Test
        @DisplayName("should delete project successfully and return 200")
        void shouldDeleteProjectSuccessfully() throws Exception {
            doNothing().when(projectService).delete(eq(1L), nullable(User.class));

            mockMvc.perform(delete("/api/v1/projects/1")
                            .with(authentication(new UsernamePasswordAuthenticationToken(testUser, null, testUser.getAuthorities()))))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.message").value("Project deleted successfully"));
        }

        @Test
        @DisplayName("should return 404 when deleting non-existent project")
        void shouldReturn404WhenDeletingNonExistent() throws Exception {
            doThrow(new ResourceNotFoundException("Project not found"))
                    .when(projectService).delete(eq(999L), nullable(User.class));

            mockMvc.perform(delete("/api/v1/projects/999")
                            .with(authentication(new UsernamePasswordAuthenticationToken(testUser, null, testUser.getAuthorities()))))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.success").value(false))
                    .andExpect(jsonPath("$.message").value("Project not found"));
        }
    }
}
