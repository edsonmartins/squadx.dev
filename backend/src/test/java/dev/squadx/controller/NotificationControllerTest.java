package dev.squadx.controller;

import dev.squadx.dto.notification.NotificationConfigRequest;
import dev.squadx.dto.notification.NotificationConfigResponse;
import dev.squadx.exception.GlobalExceptionHandler;
import dev.squadx.model.User;
import dev.squadx.model.enums.UserRole;
import dev.squadx.repository.OrganizationMemberRepository;
import dev.squadx.security.JwtAuthenticationFilter;
import dev.squadx.security.JwtService;
import dev.squadx.service.NotificationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
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
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(
        value = NotificationController.class,
        excludeAutoConfiguration = org.springframework.boot.autoconfigure.security.oauth2.client.servlet.OAuth2ClientAutoConfiguration.class
)
@AutoConfigureMockMvc(addFilters = false)
@Import(GlobalExceptionHandler.class)
class NotificationControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private NotificationService notificationService;

    @MockBean
    private OrganizationMemberRepository memberRepository;

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
                .email("test@example.com")
                .password("encoded")
                .fullName("Test User")
                .role(UserRole.USER)
                .build();
        testUser.setId(1L);

        when(memberRepository.existsByOrganizationIdAndUserId(1L, 1L)).thenReturn(true);
    }

    @Test
    @DisplayName("GET /api/v1/organizations/{orgId}/notifications - should list configs")
    void shouldListConfigs() throws Exception {
        NotificationConfigResponse response = NotificationConfigResponse.builder()
                .id(1L)
                .organizationId(1L)
                .channel("SLACK")
                .webhookUrl("https://hooks.slack.com/services/test")
                .channelName("#general")
                .notifyTaskCompleted(true)
                .notifyTaskFailed(true)
                .notifyApprovalRequired(true)
                .notifyAgentError(true)
                .enabled(true)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();

        when(notificationService.listByOrganization(1L)).thenReturn(List.of(response));

        mockMvc.perform(get("/api/v1/organizations/1/notifications")
                        .with(authentication(new UsernamePasswordAuthenticationToken(testUser, null, testUser.getAuthorities()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data[0].channel").value("SLACK"))
                .andExpect(jsonPath("$.data[0].webhook_url").value("https://hooks.slack.com/services/test"));
    }

    @Test
    @DisplayName("POST /api/v1/organizations/{orgId}/notifications - should create config")
    void shouldCreateConfig() throws Exception {
        NotificationConfigResponse response = NotificationConfigResponse.builder()
                .id(1L)
                .organizationId(1L)
                .channel("DISCORD")
                .webhookUrl("https://discord.com/api/webhooks/test")
                .enabled(true)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();

        when(notificationService.create(eq(1L), any(NotificationConfigRequest.class))).thenReturn(response);

        mockMvc.perform(post("/api/v1/organizations/1/notifications")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "channel": "DISCORD",
                                    "webhookUrl": "https://discord.com/api/webhooks/test"
                                }
                                """)
                        .with(authentication(new UsernamePasswordAuthenticationToken(testUser, null, testUser.getAuthorities()))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.channel").value("DISCORD"));
    }

    @Test
    @DisplayName("PUT /api/v1/organizations/{orgId}/notifications/{configId} - should update config")
    void shouldUpdateConfig() throws Exception {
        NotificationConfigResponse response = NotificationConfigResponse.builder()
                .id(1L)
                .organizationId(1L)
                .channel("SLACK")
                .webhookUrl("https://hooks.slack.com/services/updated")
                .enabled(true)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();

        when(notificationService.update(eq(1L), any(NotificationConfigRequest.class), nullable(User.class))).thenReturn(response);

        mockMvc.perform(put("/api/v1/organizations/1/notifications/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "channel": "SLACK",
                                    "webhookUrl": "https://hooks.slack.com/services/updated"
                                }
                                """)
                        .with(authentication(new UsernamePasswordAuthenticationToken(testUser, null, testUser.getAuthorities()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.webhook_url").value("https://hooks.slack.com/services/updated"));
    }

    @Test
    @DisplayName("DELETE /api/v1/organizations/{orgId}/notifications/{configId} - should delete config")
    void shouldDeleteConfig() throws Exception {
        mockMvc.perform(delete("/api/v1/organizations/1/notifications/1")
                        .with(authentication(new UsernamePasswordAuthenticationToken(testUser, null, testUser.getAuthorities()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        verify(notificationService).delete(eq(1L), nullable(User.class));
    }

    @Test
    @DisplayName("POST /api/v1/organizations/{orgId}/notifications/test - should send test notification")
    void shouldSendTestNotification() throws Exception {
        mockMvc.perform(post("/api/v1/organizations/1/notifications/test")
                        .with(authentication(new UsernamePasswordAuthenticationToken(testUser, null, testUser.getAuthorities()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        verify(notificationService).notifyTaskCompleted(1L, "Test Task", "Test Project");
    }
}
