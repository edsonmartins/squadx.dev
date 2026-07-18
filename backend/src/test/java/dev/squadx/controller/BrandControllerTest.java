package dev.squadx.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.squadx.dto.brand.BrandConfigRequest;
import dev.squadx.dto.brand.BrandConfigResponse;
import dev.squadx.exception.GlobalExceptionHandler;
import dev.squadx.exception.ResourceNotFoundException;
import dev.squadx.model.User;
import dev.squadx.model.enums.UserRole;
import dev.squadx.security.JwtAuthenticationFilter;
import dev.squadx.security.JwtService;
import dev.squadx.service.BrandService;
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
        value = BrandController.class,
        excludeAutoConfiguration = org.springframework.boot.autoconfigure.security.oauth2.client.servlet.OAuth2ClientAutoConfiguration.class
)
@AutoConfigureMockMvc(addFilters = false)
@Import(GlobalExceptionHandler.class)
class BrandControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private BrandService brandService;

    @MockBean
    private JwtService jwtService;

    @MockBean
    private JwtAuthenticationFilter jwtAuthenticationFilter;

    @MockBean
    private UserDetailsService userDetailsService;

    private User testUser;
    private BrandConfigResponse sampleBrand;

    @BeforeEach
    void setUp() {
        testUser = User.builder()
                .email("admin@example.com")
                .password("encoded")
                .fullName("Admin User")
                .role(UserRole.ADMIN)
                .build();
        testUser.setId(1L);

        sampleBrand = BrandConfigResponse.builder()
                .id(1L)
                .organizationId(10L)
                .appName("My App")
                .primaryColor("#FF5733")
                .customDomain("app.mycompany.com")
                .enabled(true)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();
    }

    @Nested
    @DisplayName("GET /api/v1/branding/{orgId}")
    class GetBrandConfigEndpoint {

        @Test
        @DisplayName("should return brand config for organization")
        void shouldReturnBrandConfig() throws Exception {
            when(brandService.getByOrganization(eq(10L), nullable(User.class))).thenReturn(sampleBrand);

            mockMvc.perform(get("/api/v1/branding/10")
                            .with(authentication(new UsernamePasswordAuthenticationToken(testUser, null, testUser.getAuthorities()))))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data.app_name").value("My App"))
                    .andExpect(jsonPath("$.data.primary_color").value("#FF5733"));
        }
    }

    @Nested
    @DisplayName("PUT /api/v1/branding/{orgId}")
    class UpsertBrandConfigEndpoint {

        @Test
        @DisplayName("should create or update brand config and return 200")
        void shouldUpsertBrandConfig() throws Exception {
            BrandConfigRequest request = BrandConfigRequest.builder()
                    .appName("My App")
                    .primaryColor("#FF5733")
                    .customDomain("app.mycompany.com")
                    .enabled(true)
                    .build();

            when(brandService.upsert(eq(10L), any(BrandConfigRequest.class), nullable(User.class)))
                    .thenReturn(sampleBrand);

            mockMvc.perform(put("/api/v1/branding/10")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request))
                            .with(authentication(new UsernamePasswordAuthenticationToken(testUser, null, testUser.getAuthorities()))))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.message").value("Brand configuration updated successfully"))
                    .andExpect(jsonPath("$.data.app_name").value("My App"));
        }
    }

    @Nested
    @DisplayName("DELETE /api/v1/branding/{orgId}")
    class DeleteBrandConfigEndpoint {

        @Test
        @DisplayName("should delete brand config and return 200")
        void shouldDeleteBrandConfig() throws Exception {
            doNothing().when(brandService).delete(eq(10L), nullable(User.class));

            mockMvc.perform(delete("/api/v1/branding/10")
                            .with(authentication(new UsernamePasswordAuthenticationToken(testUser, null, testUser.getAuthorities()))))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.message").value("Brand configuration removed successfully"));
        }
    }

    @Nested
    @DisplayName("GET /api/v1/branding/domain/{domain}")
    class GetBrandByDomainEndpoint {

        @Test
        @DisplayName("should return brand config by custom domain")
        void shouldReturnBrandByDomain() throws Exception {
            when(brandService.getByDomain("app.mycompany.com")).thenReturn(sampleBrand);

            mockMvc.perform(get("/api/v1/branding/domain/app.mycompany.com"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data.custom_domain").value("app.mycompany.com"));
        }

        @Test
        @DisplayName("should return 404 when domain not found")
        void shouldReturn404WhenDomainNotFound() throws Exception {
            when(brandService.getByDomain("unknown.com"))
                    .thenThrow(new ResourceNotFoundException("Brand configuration not found"));

            mockMvc.perform(get("/api/v1/branding/domain/unknown.com"))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.success").value(false))
                    .andExpect(jsonPath("$.message").value("Brand configuration not found"));
        }
    }
}
