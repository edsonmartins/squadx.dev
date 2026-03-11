package dev.squadx.service;

import dev.squadx.dto.project.ProjectRequest;
import dev.squadx.dto.project.ProjectResponse;
import dev.squadx.exception.BadRequestException;
import dev.squadx.exception.ForbiddenException;
import dev.squadx.exception.ResourceNotFoundException;
import dev.squadx.model.Organization;
import dev.squadx.model.Project;
import dev.squadx.model.Squad;
import dev.squadx.model.User;
import dev.squadx.model.enums.UserRole;
import dev.squadx.repository.OrganizationMemberRepository;
import dev.squadx.repository.OrganizationRepository;
import dev.squadx.repository.ProjectRepository;
import dev.squadx.repository.SquadRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ProjectServiceTest {

    @Mock
    private ProjectRepository projectRepository;

    @Mock
    private OrganizationRepository organizationRepository;

    @Mock
    private OrganizationMemberRepository memberRepository;

    @Mock
    private SquadRepository squadRepository;

    @InjectMocks
    private ProjectService projectService;

    private User testUser;
    private Organization testOrg;
    private Squad testSquad;
    private Project testProject;

    @BeforeEach
    void setUp() {
        testUser = User.builder()
                .email("test@example.com")
                .fullName("Test User")
                .role(UserRole.USER)
                .isActive(true)
                .build();
        testUser.setId(1L);
        testUser.setCreatedAt(Instant.now());

        testOrg = Organization.builder()
                .name("Test Org")
                .slug("test-org")
                .build();
        testOrg.setId(1L);
        testOrg.setCreatedAt(Instant.now());

        testSquad = Squad.builder()
                .name("Test Squad")
                .organization(testOrg)
                .build();
        testSquad.setId(1L);
        testSquad.setCreatedAt(Instant.now());

        testProject = Project.builder()
                .name("Test Project")
                .slug("test-project")
                .description("desc")
                .organization(testOrg)
                .isActive(true)
                .build();
        testProject.setId(1L);
        testProject.setCreatedAt(Instant.now());
    }

    @Nested
    @DisplayName("create()")
    class Create {

        @Test
        @DisplayName("should create project successfully")
        void shouldCreateProjectSuccessfully() {
            ProjectRequest request = new ProjectRequest();
            request.setName("Test Project");
            request.setDescription("desc");
            request.setOrganizationId(1L);

            when(organizationRepository.findById(1L)).thenReturn(Optional.of(testOrg));
            when(memberRepository.existsByOrganizationIdAndUserId(1L, 1L)).thenReturn(true);
            when(projectRepository.existsBySlug(anyString())).thenReturn(false);
            when(projectRepository.save(any(Project.class))).thenAnswer(invocation -> {
                Project saved = invocation.getArgument(0);
                saved.setId(1L);
                saved.setCreatedAt(Instant.now());
                return saved;
            });
            when(projectRepository.countTasksByProjectId(anyLong())).thenReturn(0L);

            ProjectResponse response = projectService.create(request, testUser);

            assertThat(response).isNotNull();
            assertThat(response.getName()).isEqualTo("Test Project");
            assertThat(response.getSlug()).isEqualTo("test-project");
            assertThat(response.getOrganizationId()).isEqualTo(1L);

            verify(projectRepository).save(any(Project.class));
            verify(memberRepository).existsByOrganizationIdAndUserId(1L, 1L);
        }

        @Test
        @DisplayName("should throw when organization not found")
        void shouldThrowWhenOrganizationNotFound() {
            ProjectRequest request = new ProjectRequest();
            request.setName("Test Project");
            request.setOrganizationId(999L);

            when(organizationRepository.findById(999L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> projectService.create(request, testUser))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessage("Organization not found");

            verify(projectRepository, never()).save(any(Project.class));
        }

        @Test
        @DisplayName("should throw when user has no access")
        void shouldThrowWhenUserHasNoAccess() {
            ProjectRequest request = new ProjectRequest();
            request.setName("Test Project");
            request.setOrganizationId(1L);

            when(organizationRepository.findById(1L)).thenReturn(Optional.of(testOrg));
            when(memberRepository.existsByOrganizationIdAndUserId(1L, 1L)).thenReturn(false);

            assertThatThrownBy(() -> projectService.create(request, testUser))
                    .isInstanceOf(ForbiddenException.class)
                    .hasMessage("User does not have access to this organization");

            verify(projectRepository, never()).save(any(Project.class));
        }

        @Test
        @DisplayName("should handle duplicate slug by appending timestamp")
        void shouldHandleDuplicateSlugByAppendingTimestamp() {
            ProjectRequest request = new ProjectRequest();
            request.setName("Test Project");
            request.setOrganizationId(1L);

            when(organizationRepository.findById(1L)).thenReturn(Optional.of(testOrg));
            when(memberRepository.existsByOrganizationIdAndUserId(1L, 1L)).thenReturn(true);
            when(projectRepository.existsBySlug("test-project")).thenReturn(true);
            when(projectRepository.save(any(Project.class))).thenAnswer(invocation -> {
                Project saved = invocation.getArgument(0);
                saved.setId(1L);
                saved.setCreatedAt(Instant.now());
                return saved;
            });
            when(projectRepository.countTasksByProjectId(anyLong())).thenReturn(0L);

            ProjectResponse response = projectService.create(request, testUser);

            assertThat(response).isNotNull();
            assertThat(response.getSlug()).startsWith("test-project-");
            assertThat(response.getSlug()).isNotEqualTo("test-project");

            verify(projectRepository).save(any(Project.class));
        }

        @Test
        @DisplayName("should throw when organization ID is null")
        void shouldThrowWhenOrganizationIdIsNull() {
            ProjectRequest request = new ProjectRequest();
            request.setName("Test Project");
            request.setOrganizationId(null);

            assertThatThrownBy(() -> projectService.create(request, testUser))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessage("Organization ID is required");

            verify(projectRepository, never()).save(any(Project.class));
        }

        @Test
        @DisplayName("should create with squad assignment")
        void shouldCreateWithSquadAssignment() {
            ProjectRequest request = new ProjectRequest();
            request.setName("Test Project");
            request.setOrganizationId(1L);
            request.setSquadId(1L);

            when(organizationRepository.findById(1L)).thenReturn(Optional.of(testOrg));
            when(memberRepository.existsByOrganizationIdAndUserId(1L, 1L)).thenReturn(true);
            when(projectRepository.existsBySlug(anyString())).thenReturn(false);
            when(squadRepository.findById(1L)).thenReturn(Optional.of(testSquad));
            when(projectRepository.save(any(Project.class))).thenAnswer(invocation -> {
                Project saved = invocation.getArgument(0);
                saved.setId(1L);
                saved.setCreatedAt(Instant.now());
                return saved;
            });
            when(projectRepository.countTasksByProjectId(anyLong())).thenReturn(0L);

            ProjectResponse response = projectService.create(request, testUser);

            assertThat(response).isNotNull();
            assertThat(response.getSquadId()).isEqualTo(1L);
            assertThat(response.getSquadName()).isEqualTo("Test Squad");

            verify(squadRepository).findById(1L);
            verify(projectRepository).save(any(Project.class));
        }
    }

    @Nested
    @DisplayName("getById()")
    class GetById {

        @Test
        @DisplayName("should return project when found")
        void shouldReturnProjectWhenFound() {
            when(projectRepository.findById(1L)).thenReturn(Optional.of(testProject));
            when(memberRepository.existsByOrganizationIdAndUserId(1L, 1L)).thenReturn(true);
            when(projectRepository.countTasksByProjectId(1L)).thenReturn(0L);

            ProjectResponse response = projectService.getById(1L, testUser);

            assertThat(response).isNotNull();
            assertThat(response.getId()).isEqualTo(1L);
            assertThat(response.getName()).isEqualTo("Test Project");
            assertThat(response.getSlug()).isEqualTo("test-project");

            verify(projectRepository).findById(1L);
        }

        @Test
        @DisplayName("should throw when not found")
        void shouldThrowWhenNotFound() {
            when(projectRepository.findById(999L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> projectService.getById(999L, testUser))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessage("Project not found");
        }

        @Test
        @DisplayName("should throw when user has no access")
        void shouldThrowWhenUserHasNoAccess() {
            when(projectRepository.findById(1L)).thenReturn(Optional.of(testProject));
            when(memberRepository.existsByOrganizationIdAndUserId(1L, 1L)).thenReturn(false);

            assertThatThrownBy(() -> projectService.getById(1L, testUser))
                    .isInstanceOf(ForbiddenException.class)
                    .hasMessage("User does not have access to this organization");
        }
    }

    @Nested
    @DisplayName("getBySlug()")
    class GetBySlug {

        @Test
        @DisplayName("should return project by slug")
        void shouldReturnProjectBySlug() {
            when(projectRepository.findBySlug("test-project")).thenReturn(Optional.of(testProject));
            when(memberRepository.existsByOrganizationIdAndUserId(1L, 1L)).thenReturn(true);
            when(projectRepository.countTasksByProjectId(1L)).thenReturn(0L);

            ProjectResponse response = projectService.getBySlug("test-project", testUser);

            assertThat(response).isNotNull();
            assertThat(response.getSlug()).isEqualTo("test-project");
            assertThat(response.getName()).isEqualTo("Test Project");

            verify(projectRepository).findBySlug("test-project");
        }

        @Test
        @DisplayName("should throw when slug not found")
        void shouldThrowWhenSlugNotFound() {
            when(projectRepository.findBySlug("nonexistent")).thenReturn(Optional.empty());

            assertThatThrownBy(() -> projectService.getBySlug("nonexistent", testUser))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessage("Project not found");
        }
    }

    @Nested
    @DisplayName("update()")
    class Update {

        @Test
        @DisplayName("should update partial fields")
        void shouldUpdatePartialFields() {
            ProjectRequest request = new ProjectRequest();
            request.setName("Updated Name");
            // description, repositoryUrl, defaultBranch, squadId are null

            when(projectRepository.findById(1L)).thenReturn(Optional.of(testProject));
            when(memberRepository.existsByOrganizationIdAndUserId(1L, 1L)).thenReturn(true);
            when(projectRepository.save(any(Project.class))).thenAnswer(invocation -> invocation.getArgument(0));
            when(projectRepository.countTasksByProjectId(1L)).thenReturn(0L);

            ProjectResponse response = projectService.update(1L, request, testUser);

            assertThat(response).isNotNull();
            assertThat(response.getName()).isEqualTo("Updated Name");
            assertThat(response.getDescription()).isEqualTo("desc");

            verify(projectRepository).save(any(Project.class));
        }

        @Test
        @DisplayName("should throw when project not found")
        void shouldThrowWhenProjectNotFound() {
            ProjectRequest request = new ProjectRequest();
            request.setName("Updated Name");

            when(projectRepository.findById(999L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> projectService.update(999L, request, testUser))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessage("Project not found");

            verify(projectRepository, never()).save(any(Project.class));
        }
    }

    @Nested
    @DisplayName("delete()")
    class Delete {

        @Test
        @DisplayName("should delete successfully")
        void shouldDeleteSuccessfully() {
            when(projectRepository.findById(1L)).thenReturn(Optional.of(testProject));
            when(memberRepository.existsByOrganizationIdAndUserId(1L, 1L)).thenReturn(true);

            projectService.delete(1L, testUser);

            verify(projectRepository).delete(testProject);
        }
    }
}
