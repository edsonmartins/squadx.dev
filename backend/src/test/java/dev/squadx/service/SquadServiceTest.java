package dev.squadx.service;

import dev.squadx.dto.common.PageResponse;
import dev.squadx.dto.squad.SquadRequest;
import dev.squadx.dto.squad.SquadResponse;
import dev.squadx.exception.ForbiddenException;
import dev.squadx.exception.ResourceNotFoundException;
import dev.squadx.model.Organization;
import dev.squadx.model.Squad;
import dev.squadx.model.User;
import dev.squadx.model.enums.UserRole;
import dev.squadx.repository.OrganizationMemberRepository;
import dev.squadx.repository.OrganizationRepository;
import dev.squadx.repository.SquadRepository;
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

import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SquadServiceTest {

    @Mock
    private SquadRepository squadRepository;

    @Mock
    private OrganizationRepository organizationRepository;

    @Mock
    private OrganizationMemberRepository memberRepository;

    @InjectMocks
    private SquadService squadService;

    private User testUser;
    private Organization testOrg;
    private Squad testSquad;

    @BeforeEach
    void setUp() {
        testUser = User.builder()
                .email("test@example.com")
                .fullName("Test User")
                .role(UserRole.USER)
                .isActive(true)
                .build();
        testUser.setId(1L);

        testOrg = Organization.builder()
                .name("Org")
                .slug("org")
                .build();
        testOrg.setId(1L);

        testSquad = Squad.builder()
                .name("Squad")
                .description("desc")
                .organization(testOrg)
                .isActive(true)
                .build();
        testSquad.setId(1L);
    }

    @Nested
    @DisplayName("create()")
    class Create {

        @Test
        @DisplayName("should create squad successfully")
        void shouldCreateSquadSuccessfully() {
            SquadRequest request = SquadRequest.builder()
                    .name("New Squad")
                    .description("A new squad")
                    .organizationId(1L)
                    .build();

            when(organizationRepository.findById(1L)).thenReturn(Optional.of(testOrg));
            when(memberRepository.existsByOrganizationIdAndUserId(1L, 1L)).thenReturn(true);
            when(squadRepository.save(any(Squad.class))).thenAnswer(invocation -> {
                Squad saved = invocation.getArgument(0);
                saved.setId(1L);
                return saved;
            });

            SquadResponse response = squadService.create(request, testUser);

            assertThat(response).isNotNull();
            assertThat(response.getName()).isEqualTo("New Squad");
            assertThat(response.getDescription()).isEqualTo("A new squad");
            assertThat(response.getOrganizationId()).isEqualTo(1L);

            verify(squadRepository).save(any(Squad.class));
        }

        @Test
        @DisplayName("should throw when organization not found")
        void shouldThrowWhenOrgNotFound() {
            SquadRequest request = SquadRequest.builder()
                    .name("Squad")
                    .organizationId(99L)
                    .build();

            when(organizationRepository.findById(99L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> squadService.create(request, testUser))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessage("Organization not found");

            verify(squadRepository, never()).save(any(Squad.class));
        }

        @Test
        @DisplayName("should throw when user has no access to organization")
        void shouldThrowWhenUserHasNoAccess() {
            SquadRequest request = SquadRequest.builder()
                    .name("Squad")
                    .organizationId(1L)
                    .build();

            when(organizationRepository.findById(1L)).thenReturn(Optional.of(testOrg));
            when(memberRepository.existsByOrganizationIdAndUserId(1L, 1L)).thenReturn(false);

            assertThatThrownBy(() -> squadService.create(request, testUser))
                    .isInstanceOf(ForbiddenException.class)
                    .hasMessage("User does not have access to this organization");

            verify(squadRepository, never()).save(any(Squad.class));
        }
    }

    @Nested
    @DisplayName("getById()")
    class GetById {

        @Test
        @DisplayName("should return squad when found")
        void shouldReturnSquad() {
            when(squadRepository.findById(1L)).thenReturn(Optional.of(testSquad));
            when(memberRepository.existsByOrganizationIdAndUserId(1L, 1L)).thenReturn(true);

            SquadResponse response = squadService.getById(1L, testUser);

            assertThat(response).isNotNull();
            assertThat(response.getId()).isEqualTo(1L);
            assertThat(response.getName()).isEqualTo("Squad");
            assertThat(response.getOrganizationId()).isEqualTo(1L);
        }

        @Test
        @DisplayName("should throw when squad not found")
        void shouldThrowWhenNotFound() {
            when(squadRepository.findById(99L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> squadService.getById(99L, testUser))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessage("Squad not found");
        }
    }

    @Nested
    @DisplayName("getByOrganizationId()")
    class GetByOrganizationId {

        @Test
        @DisplayName("should return paginated results")
        void shouldReturnPaginatedResults() {
            Pageable pageable = PageRequest.of(0, 10);
            Page<Squad> page = new PageImpl<>(List.of(testSquad), pageable, 1);

            when(memberRepository.existsByOrganizationIdAndUserId(1L, 1L)).thenReturn(true);
            when(squadRepository.findActiveByOrganizationId(1L, pageable)).thenReturn(page);

            PageResponse<SquadResponse> response = squadService.getByOrganizationId(1L, pageable, testUser);

            assertThat(response).isNotNull();
            assertThat(response.getContent()).hasSize(1);
            assertThat(response.getContent().get(0).getName()).isEqualTo("Squad");
        }
    }

    @Nested
    @DisplayName("update()")
    class Update {

        @Test
        @DisplayName("should update partial fields")
        void shouldUpdatePartialFields() {
            SquadRequest request = SquadRequest.builder()
                    .name("Updated Squad")
                    .organizationId(1L)
                    .build();

            when(squadRepository.findById(1L)).thenReturn(Optional.of(testSquad));
            when(memberRepository.existsByOrganizationIdAndUserId(1L, 1L)).thenReturn(true);
            when(squadRepository.save(any(Squad.class))).thenAnswer(invocation -> invocation.getArgument(0));

            SquadResponse response = squadService.update(1L, request, testUser);

            assertThat(response).isNotNull();
            assertThat(response.getName()).isEqualTo("Updated Squad");
            assertThat(response.getDescription()).isEqualTo("desc");

            verify(squadRepository).save(any(Squad.class));
        }

        @Test
        @DisplayName("should throw when squad not found")
        void shouldThrowWhenNotFound() {
            SquadRequest request = SquadRequest.builder()
                    .name("Updated")
                    .organizationId(1L)
                    .build();

            when(squadRepository.findById(99L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> squadService.update(99L, request, testUser))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessage("Squad not found");
        }
    }

    @Nested
    @DisplayName("delete()")
    class Delete {

        @Test
        @DisplayName("should delete squad successfully")
        void shouldDeleteSuccessfully() {
            when(squadRepository.findById(1L)).thenReturn(Optional.of(testSquad));
            when(memberRepository.existsByOrganizationIdAndUserId(1L, 1L)).thenReturn(true);

            squadService.delete(1L, testUser);

            verify(squadRepository).delete(testSquad);
        }

        @Test
        @DisplayName("should throw when squad not found")
        void shouldThrowWhenNotFound() {
            when(squadRepository.findById(99L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> squadService.delete(99L, testUser))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessage("Squad not found");

            verify(squadRepository, never()).delete(any(Squad.class));
        }
    }

    @Nested
    @DisplayName("toggleActive()")
    class ToggleActive {

        @Test
        @DisplayName("should toggle active flag")
        void shouldToggleActiveFlag() {
            testSquad.setActive(true);

            when(squadRepository.findById(1L)).thenReturn(Optional.of(testSquad));
            when(memberRepository.existsByOrganizationIdAndUserId(1L, 1L)).thenReturn(true);
            when(squadRepository.save(any(Squad.class))).thenAnswer(invocation -> invocation.getArgument(0));

            SquadResponse response = squadService.toggleActive(1L, testUser);

            assertThat(response).isNotNull();
            assertThat(testSquad.isActive()).isFalse();

            verify(squadRepository).save(any(Squad.class));
        }
    }
}
