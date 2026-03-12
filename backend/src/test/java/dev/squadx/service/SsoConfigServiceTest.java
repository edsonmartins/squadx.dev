package dev.squadx.service;

import dev.squadx.dto.auth.SsoConfigRequest;
import dev.squadx.dto.auth.SsoConfigResponse;
import dev.squadx.exception.BadRequestException;
import dev.squadx.exception.ResourceNotFoundException;
import dev.squadx.model.Organization;
import dev.squadx.model.SsoConfig;
import dev.squadx.repository.OrganizationRepository;
import dev.squadx.repository.SsoConfigRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SsoConfigServiceTest {

    @Mock
    private SsoConfigRepository ssoConfigRepository;

    @Mock
    private OrganizationRepository organizationRepository;

    @InjectMocks
    private SsoConfigService ssoConfigService;

    private Organization testOrg;
    private SsoConfig testConfig;

    @BeforeEach
    void setUp() {
        testOrg = Organization.builder()
                .name("Test Org")
                .slug("test-org")
                .build();
        testOrg.setId(1L);

        testConfig = SsoConfig.builder()
                .organization(testOrg)
                .provider("google")
                .clientId("google-client-id")
                .clientSecret("google-client-secret")
                .issuerUri("https://accounts.google.com")
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
        @DisplayName("should return all SSO configs for an organization")
        void shouldReturnAllConfigs() {
            when(ssoConfigRepository.findByOrganizationId(1L)).thenReturn(List.of(testConfig));

            List<SsoConfigResponse> results = ssoConfigService.getByOrganization(1L);

            assertThat(results).hasSize(1);
            assertThat(results.get(0).getProvider()).isEqualTo("google");
            assertThat(results.get(0).getClientId()).isEqualTo("google-client-id");
            verify(ssoConfigRepository).findByOrganizationId(1L);
        }
    }

    @Nested
    @DisplayName("createOrUpdate()")
    class CreateOrUpdate {

        @Test
        @DisplayName("should create a new SSO config when none exists for provider")
        void shouldCreateNewConfig() {
            SsoConfigRequest request = SsoConfigRequest.builder()
                    .provider("okta")
                    .clientId("okta-client-id")
                    .clientSecret("okta-client-secret")
                    .issuerUri("https://dev-123456.okta.com")
                    .enabled(true)
                    .build();

            when(organizationRepository.findById(1L)).thenReturn(Optional.of(testOrg));
            when(ssoConfigRepository.findByOrganizationIdAndProvider(1L, "okta"))
                    .thenReturn(Optional.empty());
            when(ssoConfigRepository.save(any(SsoConfig.class))).thenAnswer(invocation -> {
                SsoConfig saved = invocation.getArgument(0);
                saved.setId(2L);
                saved.setCreatedAt(Instant.now());
                saved.setUpdatedAt(Instant.now());
                return saved;
            });

            SsoConfigResponse response = ssoConfigService.createOrUpdate(1L, request);

            assertThat(response).isNotNull();
            assertThat(response.getProvider()).isEqualTo("okta");
            assertThat(response.getClientId()).isEqualTo("okta-client-id");
            verify(ssoConfigRepository).save(any(SsoConfig.class));
        }

        @Test
        @DisplayName("should update an existing SSO config for the same provider")
        void shouldUpdateExistingConfig() {
            SsoConfigRequest request = SsoConfigRequest.builder()
                    .provider("google")
                    .clientId("updated-client-id")
                    .clientSecret("updated-client-secret")
                    .issuerUri("https://accounts.google.com")
                    .enabled(false)
                    .build();

            when(organizationRepository.findById(1L)).thenReturn(Optional.of(testOrg));
            when(ssoConfigRepository.findByOrganizationIdAndProvider(1L, "google"))
                    .thenReturn(Optional.of(testConfig));
            when(ssoConfigRepository.save(any(SsoConfig.class))).thenAnswer(invocation -> invocation.getArgument(0));

            SsoConfigResponse response = ssoConfigService.createOrUpdate(1L, request);

            assertThat(response).isNotNull();
            assertThat(response.getClientId()).isEqualTo("updated-client-id");
            verify(ssoConfigRepository).save(any(SsoConfig.class));
        }

        @Test
        @DisplayName("should throw BadRequestException for unsupported provider")
        void shouldThrowForUnsupportedProvider() {
            SsoConfigRequest request = SsoConfigRequest.builder()
                    .provider("facebook")
                    .clientId("fb-id")
                    .clientSecret("fb-secret")
                    .enabled(true)
                    .build();

            when(organizationRepository.findById(1L)).thenReturn(Optional.of(testOrg));

            assertThatThrownBy(() -> ssoConfigService.createOrUpdate(1L, request))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessageContaining("Unsupported SSO provider");

            verify(ssoConfigRepository, never()).save(any());
        }

        @Test
        @DisplayName("should throw ResourceNotFoundException when organization not found")
        void shouldThrowWhenOrgNotFound() {
            SsoConfigRequest request = SsoConfigRequest.builder()
                    .provider("google")
                    .clientId("id")
                    .clientSecret("secret")
                    .enabled(true)
                    .build();

            when(organizationRepository.findById(99L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> ssoConfigService.createOrUpdate(99L, request))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining("Organization not found");
        }
    }

    @Nested
    @DisplayName("delete()")
    class Delete {

        @Test
        @DisplayName("should delete SSO config when it exists")
        void shouldDeleteConfig() {
            when(ssoConfigRepository.findByOrganizationIdAndProvider(1L, "google"))
                    .thenReturn(Optional.of(testConfig));

            ssoConfigService.delete(1L, "google");

            verify(ssoConfigRepository).delete(testConfig);
        }

        @Test
        @DisplayName("should throw ResourceNotFoundException when config not found for provider")
        void shouldThrowWhenNotFound() {
            when(ssoConfigRepository.findByOrganizationIdAndProvider(1L, "okta"))
                    .thenReturn(Optional.empty());

            assertThatThrownBy(() -> ssoConfigService.delete(1L, "okta"))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining("SSO config not found");
        }
    }

    @Nested
    @DisplayName("toggleEnabled()")
    class ToggleEnabled {

        @Test
        @DisplayName("should toggle enabled status of SSO config")
        void shouldToggleEnabled() {
            when(ssoConfigRepository.findByOrganizationIdAndProvider(1L, "google"))
                    .thenReturn(Optional.of(testConfig));
            when(ssoConfigRepository.save(any(SsoConfig.class))).thenAnswer(invocation -> invocation.getArgument(0));

            SsoConfigResponse response = ssoConfigService.toggleEnabled(1L, "google", false);

            assertThat(response).isNotNull();
            assertThat(response.isEnabled()).isFalse();
            verify(ssoConfigRepository).save(any(SsoConfig.class));
        }
    }
}
