package dev.squadx.service;

import dev.squadx.dto.brand.BrandConfigRequest;
import dev.squadx.dto.brand.BrandConfigResponse;
import dev.squadx.exception.BadRequestException;
import dev.squadx.exception.ForbiddenException;
import dev.squadx.exception.ResourceNotFoundException;
import dev.squadx.model.BrandConfig;
import dev.squadx.model.Organization;
import dev.squadx.model.User;
import dev.squadx.model.enums.UserRole;
import dev.squadx.repository.BrandConfigRepository;
import dev.squadx.repository.OrganizationRepository;
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
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BrandServiceTest {

    @Mock
    private BrandConfigRepository brandConfigRepository;

    @Mock
    private OrganizationRepository organizationRepository;

    @Mock
    private OrganizationAccessGuard accessGuard;

    @InjectMocks
    private BrandService brandService;

    private Organization testOrg;
    private BrandConfig testConfig;
    private User testUser;

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
                .name("Test Org")
                .slug("test-org")
                .build();
        testOrg.setId(1L);

        testConfig = BrandConfig.builder()
                .organization(testOrg)
                .appName("MyApp")
                .primaryColor("#FF5733")
                .customDomain("app.example.com")
                .enabled(true)
                .build();
        testConfig.setId(1L);
        testConfig.setCreatedAt(Instant.now());
        testConfig.setUpdatedAt(Instant.now());
    }

    @Nested
    @DisplayName("getByOrganization()")
    class GetByOrganization {

        @Test
        @DisplayName("should return brand config when found")
        void shouldReturnConfigWhenFound() {
            when(brandConfigRepository.findByOrganizationId(1L)).thenReturn(Optional.of(testConfig));

            BrandConfigResponse response = brandService.getByOrganization(1L, testUser);

            assertThat(response).isNotNull();
            assertThat(response.getAppName()).isEqualTo("MyApp");
            assertThat(response.getPrimaryColor()).isEqualTo("#FF5733");
            assertThat(response.getOrganizationId()).isEqualTo(1L);
        }

        @Test
        @DisplayName("should throw ResourceNotFoundException when config not found")
        void shouldThrowWhenNotFound() {
            when(brandConfigRepository.findByOrganizationId(99L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> brandService.getByOrganization(99L, testUser))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining("Brand config not found");
        }
    }

    @Nested
    @DisplayName("upsert()")
    class Upsert {

        @Test
        @DisplayName("should create a new brand config when none exists")
        void shouldCreateNewConfig() {
            BrandConfigRequest request = BrandConfigRequest.builder()
                    .appName("NewApp")
                    .primaryColor("#AABBCC")
                    .build();

            when(organizationRepository.findById(1L)).thenReturn(Optional.of(testOrg));
            when(brandConfigRepository.findByOrganizationId(1L)).thenReturn(Optional.empty());
            when(brandConfigRepository.save(any(BrandConfig.class))).thenAnswer(invocation -> {
                BrandConfig saved = invocation.getArgument(0);
                saved.setId(2L);
                saved.setCreatedAt(Instant.now());
                saved.setUpdatedAt(Instant.now());
                return saved;
            });

            BrandConfigResponse response = brandService.upsert(1L, request, testUser);

            assertThat(response).isNotNull();
            assertThat(response.getAppName()).isEqualTo("NewApp");
            verify(brandConfigRepository).save(any(BrandConfig.class));
        }

        @Test
        @DisplayName("should update an existing brand config")
        void shouldUpdateExistingConfig() {
            BrandConfigRequest request = BrandConfigRequest.builder()
                    .appName("UpdatedApp")
                    .primaryColor("#112233")
                    .build();

            when(organizationRepository.findById(1L)).thenReturn(Optional.of(testOrg));
            when(brandConfigRepository.findByOrganizationId(1L)).thenReturn(Optional.of(testConfig));
            when(brandConfigRepository.save(any(BrandConfig.class))).thenAnswer(invocation -> invocation.getArgument(0));

            BrandConfigResponse response = brandService.upsert(1L, request, testUser);

            assertThat(response).isNotNull();
            assertThat(response.getAppName()).isEqualTo("UpdatedApp");
            assertThat(response.getPrimaryColor()).isEqualTo("#112233");
            verify(brandConfigRepository).save(any(BrandConfig.class));
        }

        @Test
        @DisplayName("should throw BadRequestException for invalid hex color")
        void shouldThrowForInvalidHexColor() {
            BrandConfigRequest request = BrandConfigRequest.builder()
                    .primaryColor("not-a-color")
                    .build();

            when(organizationRepository.findById(1L)).thenReturn(Optional.of(testOrg));

            assertThatThrownBy(() -> brandService.upsert(1L, request, testUser))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessageContaining("Invalid hex color format");

            verify(brandConfigRepository, never()).save(any(BrandConfig.class));
        }

        @Test
        @DisplayName("should throw BadRequestException for invalid hex color with wrong length")
        void shouldThrowForShortHexColor() {
            BrandConfigRequest request = BrandConfigRequest.builder()
                    .secondaryColor("#FFF")
                    .build();

            when(organizationRepository.findById(1L)).thenReturn(Optional.of(testOrg));

            assertThatThrownBy(() -> brandService.upsert(1L, request, testUser))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessageContaining("Invalid hex color format")
                    .hasMessageContaining("secondaryColor");
        }
    }

    @Nested
    @DisplayName("delete()")
    class Delete {

        @Test
        @DisplayName("should delete brand config when it exists")
        void shouldDeleteConfig() {
            when(brandConfigRepository.existsByOrganizationId(1L)).thenReturn(true);

            brandService.delete(1L, testUser);

            verify(brandConfigRepository).deleteByOrganizationId(1L);
        }

        @Test
        @DisplayName("should throw ResourceNotFoundException when config does not exist")
        void shouldThrowWhenNotFound() {
            when(brandConfigRepository.existsByOrganizationId(99L)).thenReturn(false);

            assertThatThrownBy(() -> brandService.delete(99L, testUser))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining("Brand config not found");
        }
    }

    @Nested
    @DisplayName("domain validation")
    class DomainValidation {

        @Test
        @DisplayName("should throw BadRequestException for invalid domain format")
        void shouldThrowForInvalidDomain() {
            BrandConfigRequest request = BrandConfigRequest.builder()
                    .customDomain("not a domain!")
                    .build();

            when(organizationRepository.findById(1L)).thenReturn(Optional.of(testOrg));

            assertThatThrownBy(() -> brandService.upsert(1L, request, testUser))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessageContaining("Invalid domain format");
        }

        @Test
        @DisplayName("should throw BadRequestException when domain is used by another organization")
        void shouldThrowWhenDomainUsedByAnotherOrg() {
            Organization otherOrg = Organization.builder().name("Other Org").slug("other-org").build();
            otherOrg.setId(2L);

            BrandConfig otherConfig = BrandConfig.builder()
                    .organization(otherOrg)
                    .customDomain("taken.example.com")
                    .build();
            otherConfig.setId(5L);

            BrandConfigRequest request = BrandConfigRequest.builder()
                    .customDomain("taken.example.com")
                    .build();

            when(organizationRepository.findById(1L)).thenReturn(Optional.of(testOrg));
            when(brandConfigRepository.findByCustomDomain("taken.example.com")).thenReturn(Optional.of(otherConfig));

            assertThatThrownBy(() -> brandService.upsert(1L, request, testUser))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessageContaining("Domain already in use");
        }
    }

    @Nested
    @DisplayName("cross-tenant access control")
    class CrossTenantAccess {

        @Test
        @DisplayName("getByOrganization denies a non-member and never reads the config")
        void getByOrganization_deniesNonMember() {
            doThrow(new ForbiddenException("nope"))
                    .when(accessGuard).requireMember(anyLong(), anyLong());

            assertThatThrownBy(() -> brandService.getByOrganization(1L, testUser))
                    .isInstanceOf(ForbiddenException.class);

            verifyNoInteractions(brandConfigRepository);
        }
    }
}
