package dev.squadx.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.squadx.exception.GlobalExceptionHandler;
import dev.squadx.model.CalendarIntegration;
import dev.squadx.model.User;
import dev.squadx.model.enums.UserRole;
import dev.squadx.security.JwtAuthenticationFilter;
import dev.squadx.security.JwtService;
import dev.squadx.service.GoogleCalendarService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(
        value = CalendarSyncController.class,
        excludeAutoConfiguration = org.springframework.boot.autoconfigure.security.oauth2.client.servlet.OAuth2ClientAutoConfiguration.class
)
@AutoConfigureMockMvc(addFilters = false)
@Import(GlobalExceptionHandler.class)
class CalendarSyncControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private GoogleCalendarService googleCalendarService;

    @MockBean
    private JwtService jwtService;

    @MockBean
    private JwtAuthenticationFilter jwtAuthenticationFilter;

    @MockBean
    private UserDetailsService userDetailsService;

    private User testUser;

    @BeforeEach
    void setUp() {
        testUser = User.builder()
                .email("user@example.com")
                .password("encoded")
                .fullName("Test User")
                .role(UserRole.USER)
                .build();
        testUser.setId(1L);
    }

    @Nested
    @DisplayName("GET /api/v1/calendar-sync/auth-url")
    class GetAuthUrlEndpoint {

        @Test
        @DisplayName("should return Google OAuth2 authorization URL")
        void shouldReturnAuthUrl() throws Exception {
            when(googleCalendarService.getAuthorizationUrl(1L, 10L))
                    .thenReturn("https://accounts.google.com/o/oauth2/auth?...");

            mockMvc.perform(get("/api/v1/calendar-sync/auth-url")
                            .param("organizationId", "10")
                            .with(authentication(new UsernamePasswordAuthenticationToken(testUser, null, testUser.getAuthorities()))))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data.url").value("https://accounts.google.com/o/oauth2/auth?..."));
        }
    }

    @Nested
    @DisplayName("GET /api/v1/calendar-sync/callback")
    class HandleCallbackEndpoint {

        @Test
        @DisplayName("should handle OAuth callback and return integration details")
        void shouldHandleCallback() throws Exception {
            CalendarIntegration integration = CalendarIntegration.builder()
                    .provider("google")
                    .enabled(true)
                    .build();
            integration.setId(5L);

            when(googleCalendarService.handleOAuthCallback("auth-code", 1L, 10L))
                    .thenReturn(integration);

            mockMvc.perform(get("/api/v1/calendar-sync/callback")
                            .param("code", "auth-code")
                            .param("state", "1:10"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.message").value("Google Calendar connected successfully"))
                    .andExpect(jsonPath("$.data.integration_id").value(5))
                    .andExpect(jsonPath("$.data.provider").value("google"))
                    .andExpect(jsonPath("$.data.enabled").value(true));
        }
    }

    @Nested
    @DisplayName("POST /api/v1/calendar-sync/sync")
    class SyncEndpoint {

        @Test
        @DisplayName("should trigger calendar sync and return 200")
        void shouldTriggerSync() throws Exception {
            doNothing().when(googleCalendarService).syncMeetingsToGoogle(1L);
            doNothing().when(googleCalendarService).syncMeetingsFromGoogle(1L);

            mockMvc.perform(post("/api/v1/calendar-sync/sync")
                            .with(authentication(new UsernamePasswordAuthenticationToken(testUser, null, testUser.getAuthorities()))))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.message").value("Calendar sync started in background"));
        }
    }

    @Nested
    @DisplayName("DELETE /api/v1/calendar-sync/disconnect")
    class DisconnectEndpoint {

        @Test
        @DisplayName("should disconnect Google Calendar and return 200")
        void shouldDisconnect() throws Exception {
            doNothing().when(googleCalendarService).disconnect(1L, 10L);

            mockMvc.perform(delete("/api/v1/calendar-sync/disconnect")
                            .param("organizationId", "10")
                            .with(authentication(new UsernamePasswordAuthenticationToken(testUser, null, testUser.getAuthorities()))))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.message").value("Google Calendar disconnected"));
        }
    }

    @Nested
    @DisplayName("GET /api/v1/calendar-sync/status")
    class GetStatusEndpoint {

        @Test
        @DisplayName("should return connected status when integration exists")
        void shouldReturnConnectedStatus() throws Exception {
            CalendarIntegration integration = CalendarIntegration.builder()
                    .provider("google")
                    .enabled(true)
                    .calendarId("primary")
                    .build();
            integration.setId(5L);

            when(googleCalendarService.getIntegrationStatus(1L, 10L)).thenReturn(integration);
            when(googleCalendarService.isConfigured()).thenReturn(true);

            mockMvc.perform(get("/api/v1/calendar-sync/status")
                            .param("organizationId", "10")
                            .with(authentication(new UsernamePasswordAuthenticationToken(testUser, null, testUser.getAuthorities()))))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data.connected").value(true))
                    .andExpect(jsonPath("$.data.configured").value(true))
                    .andExpect(jsonPath("$.data.provider").value("google"));
        }

        @Test
        @DisplayName("should return disconnected status when no integration")
        void shouldReturnDisconnectedStatus() throws Exception {
            when(googleCalendarService.getIntegrationStatus(1L, 10L)).thenReturn(null);
            when(googleCalendarService.isConfigured()).thenReturn(true);

            mockMvc.perform(get("/api/v1/calendar-sync/status")
                            .param("organizationId", "10")
                            .with(authentication(new UsernamePasswordAuthenticationToken(testUser, null, testUser.getAuthorities()))))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data.connected").value(false))
                    .andExpect(jsonPath("$.data.configured").value(true));
        }
    }
}
