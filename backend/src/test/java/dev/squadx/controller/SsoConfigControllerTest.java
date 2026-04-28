package dev.squadx.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.squadx.dto.auth.SsoConfigRequest;
import dev.squadx.dto.auth.SsoConfigResponse;
import dev.squadx.exception.GlobalExceptionHandler;
import dev.squadx.exception.ResourceNotFoundException;
import dev.squadx.model.User;
import dev.squadx.model.enums.UserRole;
import dev.squadx.security.JwtAuthenticationFilter;
import dev.squadx.security.JwtService;
import dev.squadx.service.SsoConfigService;
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
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(
        value = SsoConfigController.class,
        excludeAutoConfiguration = org.springframework.boot.autoconfigure.security.oauth2.client.servlet.OAuth2ClientAutoConfiguration.class
)
@AutoConfigureMockMvc(addFilters = false)
@Import(GlobalExceptionHandler.class)
class SsoConfigControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private SsoConfigService ssoConfigService;

    @MockBean
    private JwtService jwtService;

    @MockBean
    private JwtAuthenticationFilter jwtAuthenticationFilter;

    @MockBean
    private UserDetailsService userDetailsService;

    private User testUser;
    private SsoConfigResponse sampleSsoConfig;

    @BeforeEach
    void setUp() {
        testUser = User.builder()
                .email("admin@example.com")
                .password("encoded")
                .fullName("Admin User")
                .role(UserRole.ADMIN)
                .build();
        testUser.setId(1L);

        sampleSsoConfig = SsoConfigResponse.builder()
                .id(1L)
                .organizationId(10L)
                .provider("okta")
                .clientId("client-id-123")
                .issuerUri("https://dev-123.okta.com")
                .enabled(true)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();
    }

    @Nested
    @DisplayName("POST /api/v1/organizations/{orgId}/sso")
    class CreateOrUpdateEndpoint {

        @Test
        @DisplayName("should create or update SSO config and return 200")
        void shouldCreateOrUpdateSsoConfig() throws Exception {
            SsoConfigRequest request = SsoConfigRequest.builder()
                    .provider("okta")
                    .clientId("client-id-123")
                    .clientSecret("secret-456")
                    .issuerUri("https://dev-123.okta.com")
                    .enabled(true)
                    .build();

            when(ssoConfigService.createOrUpdate(eq(10L), any(SsoConfigRequest.class)))
                    .thenReturn(sampleSsoConfig);

            mockMvc.perform(post("/api/v1/organizations/10/sso")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request))
                            .with(authentication(new UsernamePasswordAuthenticationToken(testUser, null, testUser.getAuthorities()))))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.message").value("SSO configuration saved successfully"))
                    .andExpect(jsonPath("$.data.provider").value("okta"));
        }
    }

    @Nested
    @DisplayName("GET /api/v1/organizations/{orgId}/sso")
    class GetAllEndpoint {

        @Test
        @DisplayName("should return all SSO configs for organization")
        void shouldReturnAllSsoConfigs() throws Exception {
            when(ssoConfigService.getByOrganization(10L)).thenReturn(List.of(sampleSsoConfig));

            mockMvc.perform(get("/api/v1/organizations/10/sso")
                            .with(authentication(new UsernamePasswordAuthenticationToken(testUser, null, testUser.getAuthorities()))))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data[0].provider").value("okta"))
                    .andExpect(jsonPath("$.data[0].enabled").value(true));
        }
    }

    @Nested
    @DisplayName("GET /api/v1/organizations/{orgId}/sso/{provider}")
    class GetByProviderEndpoint {

        @Test
        @DisplayName("should return SSO config by provider")
        void shouldReturnSsoConfigByProvider() throws Exception {
            when(ssoConfigService.getByOrganizationAndProvider(10L, "okta"))
                    .thenReturn(sampleSsoConfig);

            mockMvc.perform(get("/api/v1/organizations/10/sso/okta")
                            .with(authentication(new UsernamePasswordAuthenticationToken(testUser, null, testUser.getAuthorities()))))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data.provider").value("okta"))
                    .andExpect(jsonPath("$.data.issuerUri").value("https://dev-123.okta.com"));
        }
    }

    @Nested
    @DisplayName("DELETE /api/v1/organizations/{orgId}/sso/{provider}")
    class DeleteEndpoint {

        @Test
        @DisplayName("should delete SSO config and return 200")
        void shouldDeleteSsoConfig() throws Exception {
            doNothing().when(ssoConfigService).delete(10L, "okta");

            mockMvc.perform(delete("/api/v1/organizations/10/sso/okta")
                            .with(authentication(new UsernamePasswordAuthenticationToken(testUser, null, testUser.getAuthorities()))))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.message").value("SSO configuration deleted successfully"));
        }
    }

    @Nested
    @DisplayName("PATCH /api/v1/organizations/{orgId}/sso/{provider}/toggle")
    class ToggleEndpoint {

        @Test
        @DisplayName("should toggle SSO enabled state and return 200")
        void shouldToggleSsoConfig() throws Exception {
            SsoConfigResponse toggled = SsoConfigResponse.builder()
                    .id(1L)
                    .organizationId(10L)
                    .provider("okta")
                    .clientId("client-id-123")
                    .enabled(false)
                    .build();

            when(ssoConfigService.toggleEnabled(10L, "okta", false)).thenReturn(toggled);

            mockMvc.perform(patch("/api/v1/organizations/10/sso/okta/toggle")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(Map.of("enabled", false)))
                            .with(authentication(new UsernamePasswordAuthenticationToken(testUser, null, testUser.getAuthorities()))))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.message").value("SSO configuration updated successfully"))
                    .andExpect(jsonPath("$.data.enabled").value(false));
        }
    }
}
