package dev.squadx.service;

import dev.squadx.dto.organization.OrganizationRequest;
import dev.squadx.dto.organization.OrganizationResponse;
import dev.squadx.exception.BadRequestException;
import dev.squadx.exception.ForbiddenException;
import dev.squadx.exception.ResourceNotFoundException;
import dev.squadx.model.Organization;
import dev.squadx.model.OrganizationMember;
import dev.squadx.model.User;
import dev.squadx.model.enums.OrgRole;
import dev.squadx.model.enums.UserRole;
import dev.squadx.repository.OrganizationMemberRepository;
import dev.squadx.repository.OrganizationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OrganizationServiceTest {

    @Mock
    private OrganizationRepository organizationRepository;

    @Mock
    private OrganizationMemberRepository memberRepository;

    @InjectMocks
    private OrganizationService organizationService;

    private User testUser;
    private Organization testOrg;

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
                .description("Test description")
                .isActive(true)
                .build();
        testOrg.setId(1L);
        testOrg.setCreatedAt(Instant.now());
    }

    @Nested
    @DisplayName("create()")
    class Create {

        @Test
        @DisplayName("should create organization and add owner member")
        void shouldCreateOrganizationAndAddOwnerMember() {
            OrganizationRequest request = new OrganizationRequest();
            request.setName("Test Org");
            request.setDescription("Test description");

            when(organizationRepository.existsBySlug("test-org")).thenReturn(false);
            when(organizationRepository.save(any(Organization.class))).thenAnswer(invocation -> {
                Organization saved = invocation.getArgument(0);
                saved.setId(1L);
                saved.setCreatedAt(Instant.now());
                return saved;
            });
            when(memberRepository.save(any(OrganizationMember.class))).thenAnswer(invocation -> invocation.getArgument(0));
            when(organizationRepository.countMembersByOrganizationId(anyLong())).thenReturn(0L);
            when(organizationRepository.countProjectsByOrganizationId(anyLong())).thenReturn(0L);
            when(organizationRepository.countSquadsByOrganizationId(anyLong())).thenReturn(0L);

            OrganizationResponse response = organizationService.create(request, testUser);

            assertThat(response).isNotNull();
            assertThat(response.getName()).isEqualTo("Test Org");
            assertThat(response.getSlug()).isEqualTo("test-org");

            verify(organizationRepository).save(any(Organization.class));
            verify(memberRepository).save(argThat(member ->
                    member.getRole() == OrgRole.OWNER &&
                    member.getUser().getId().equals(1L)
            ));
        }

        @Test
        @DisplayName("should throw when slug already exists")
        void shouldThrowWhenSlugAlreadyExists() {
            OrganizationRequest request = new OrganizationRequest();
            request.setName("Test Org");

            when(organizationRepository.existsBySlug("test-org")).thenReturn(true);

            assertThatThrownBy(() -> organizationService.create(request, testUser))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessage("Organization with this name already exists");

            verify(organizationRepository, never()).save(any(Organization.class));
            verify(memberRepository, never()).save(any(OrganizationMember.class));
        }
    }

    @Nested
    @DisplayName("getById()")
    class GetById {

        @Test
        @DisplayName("should return org when found")
        void shouldReturnOrgWhenFound() {
            when(organizationRepository.findById(1L)).thenReturn(Optional.of(testOrg));
            when(memberRepository.existsByOrganizationIdAndUserId(1L, 1L)).thenReturn(true);
            when(organizationRepository.countMembersByOrganizationId(1L)).thenReturn(0L);
            when(organizationRepository.countProjectsByOrganizationId(1L)).thenReturn(0L);
            when(organizationRepository.countSquadsByOrganizationId(1L)).thenReturn(0L);

            OrganizationResponse response = organizationService.getById(1L, testUser);

            assertThat(response).isNotNull();
            assertThat(response.getId()).isEqualTo(1L);
            assertThat(response.getName()).isEqualTo("Test Org");
            assertThat(response.getSlug()).isEqualTo("test-org");

            verify(organizationRepository).findById(1L);
        }

        @Test
        @DisplayName("should throw when not found")
        void shouldThrowWhenNotFound() {
            when(organizationRepository.findById(999L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> organizationService.getById(999L, testUser))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessage("Organization not found");
        }

        @Test
        @DisplayName("should throw when user has no access")
        void shouldThrowWhenUserHasNoAccess() {
            when(organizationRepository.findById(1L)).thenReturn(Optional.of(testOrg));
            when(memberRepository.existsByOrganizationIdAndUserId(1L, 1L)).thenReturn(false);

            assertThatThrownBy(() -> organizationService.getById(1L, testUser))
                    .isInstanceOf(ForbiddenException.class)
                    .hasMessage("User does not have access to this organization");
        }
    }

    @Nested
    @DisplayName("getBySlug()")
    class GetBySlug {

        @Test
        @DisplayName("should return org by slug")
        void shouldReturnOrgBySlug() {
            when(organizationRepository.findBySlug("test-org")).thenReturn(Optional.of(testOrg));
            when(memberRepository.existsByOrganizationIdAndUserId(1L, 1L)).thenReturn(true);
            when(organizationRepository.countMembersByOrganizationId(1L)).thenReturn(0L);
            when(organizationRepository.countProjectsByOrganizationId(1L)).thenReturn(0L);
            when(organizationRepository.countSquadsByOrganizationId(1L)).thenReturn(0L);

            OrganizationResponse response = organizationService.getBySlug("test-org", testUser);

            assertThat(response).isNotNull();
            assertThat(response.getSlug()).isEqualTo("test-org");
            assertThat(response.getName()).isEqualTo("Test Org");

            verify(organizationRepository).findBySlug("test-org");
        }

        @Test
        @DisplayName("should throw when slug not found")
        void shouldThrowWhenSlugNotFound() {
            when(organizationRepository.findBySlug("nonexistent")).thenReturn(Optional.empty());

            assertThatThrownBy(() -> organizationService.getBySlug("nonexistent", testUser))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessage("Organization not found");
        }
    }

    @Nested
    @DisplayName("getByUserId()")
    class GetByUserId {

        @Test
        @DisplayName("should return paginated results")
        void shouldReturnPaginatedResults() {
            Pageable pageable = PageRequest.of(0, 10);
            Page<Organization> page = new PageImpl<>(List.of(testOrg), pageable, 1);

            when(organizationRepository.findByUserId(1L, pageable)).thenReturn(page);
            when(organizationRepository.countMembersByOrganizationId(1L)).thenReturn(0L);
            when(organizationRepository.countProjectsByOrganizationId(1L)).thenReturn(0L);
            when(organizationRepository.countSquadsByOrganizationId(1L)).thenReturn(0L);

            var response = organizationService.getByUserId(1L, pageable);

            assertThat(response).isNotNull();
            assertThat(response.getContent()).hasSize(1);
            assertThat(response.getContent().get(0).getName()).isEqualTo("Test Org");
        }
    }

    @Nested
    @DisplayName("update()")
    class Update {

        @Test
        @DisplayName("should update successfully")
        void shouldUpdateSuccessfully() {
            OrganizationRequest request = new OrganizationRequest();
            request.setDescription("Updated description");

            when(organizationRepository.findById(1L)).thenReturn(Optional.of(testOrg));
            when(memberRepository.existsByOrganizationIdAndUserIdAndRoleIn(1L, 1L, OrgRole.OWNER, OrgRole.ADMIN))
                    .thenReturn(true);
            when(organizationRepository.save(any(Organization.class))).thenAnswer(invocation -> invocation.getArgument(0));
            when(organizationRepository.countMembersByOrganizationId(1L)).thenReturn(0L);
            when(organizationRepository.countProjectsByOrganizationId(1L)).thenReturn(0L);
            when(organizationRepository.countSquadsByOrganizationId(1L)).thenReturn(0L);

            OrganizationResponse response = organizationService.update(1L, request, testUser);

            assertThat(response).isNotNull();
            assertThat(response.getDescription()).isEqualTo("Updated description");

            verify(organizationRepository).save(any(Organization.class));
        }

        @Test
        @DisplayName("should throw when not found")
        void shouldThrowWhenNotFound() {
            OrganizationRequest request = new OrganizationRequest();
            request.setName("Updated");

            when(organizationRepository.findById(999L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> organizationService.update(999L, request, testUser))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessage("Organization not found");

            verify(organizationRepository, never()).save(any(Organization.class));
        }

        @Test
        @DisplayName("should throw when non-admin tries to update")
        void shouldThrowWhenNonAdminTriesToUpdate() {
            OrganizationRequest request = new OrganizationRequest();
            request.setName("Updated");

            when(organizationRepository.findById(1L)).thenReturn(Optional.of(testOrg));
            when(memberRepository.existsByOrganizationIdAndUserIdAndRoleIn(1L, 1L, OrgRole.OWNER, OrgRole.ADMIN))
                    .thenReturn(false);

            assertThatThrownBy(() -> organizationService.update(1L, request, testUser))
                    .isInstanceOf(ForbiddenException.class)
                    .hasMessage("User does not have admin access to this organization");

            verify(organizationRepository, never()).save(any(Organization.class));
        }

        @Test
        @DisplayName("should throw when new name conflicts slug")
        void shouldThrowWhenNewNameConflictsSlug() {
            OrganizationRequest request = new OrganizationRequest();
            request.setName("Existing Org");

            when(organizationRepository.findById(1L)).thenReturn(Optional.of(testOrg));
            when(memberRepository.existsByOrganizationIdAndUserIdAndRoleIn(1L, 1L, OrgRole.OWNER, OrgRole.ADMIN))
                    .thenReturn(true);
            when(organizationRepository.existsBySlug("existing-org")).thenReturn(true);

            assertThatThrownBy(() -> organizationService.update(1L, request, testUser))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessage("Organization with this name already exists");

            verify(organizationRepository, never()).save(any(Organization.class));
        }
    }

    @Nested
    @DisplayName("delete()")
    class Delete {

        @Test
        @DisplayName("should throw when non-owner tries to delete")
        void shouldThrowWhenNonOwnerTriesToDelete() {
            when(organizationRepository.findById(1L)).thenReturn(Optional.of(testOrg));
            when(memberRepository.existsByOrganizationIdAndUserIdAndRoleIn(1L, 1L, OrgRole.OWNER))
                    .thenReturn(false);

            assertThatThrownBy(() -> organizationService.delete(1L, testUser))
                    .isInstanceOf(ForbiddenException.class)
                    .hasMessage("Only owner can perform this action");

            verify(organizationRepository, never()).delete(any(Organization.class));
        }
    }
}
