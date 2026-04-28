package dev.squadx.controller;

import dev.squadx.exception.GlobalExceptionHandler;
import dev.squadx.integration.ServiceJwtProvider;
import dev.squadx.security.JwtAuthenticationFilter;
import dev.squadx.security.JwtService;
import dev.squadx.service.IntegrationWebhookService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(
        value = IntegrationWebhookController.class,
        excludeAutoConfiguration = org.springframework.boot.autoconfigure.security.oauth2.client.servlet.OAuth2ClientAutoConfiguration.class
)
@AutoConfigureMockMvc(addFilters = false)
@Import(GlobalExceptionHandler.class)
class IntegrationWebhookControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ServiceJwtProvider serviceJwtProvider;

    @MockBean
    private IntegrationWebhookService integrationWebhookService;

    @MockBean
    private JwtService jwtService;

    @MockBean
    private JwtAuthenticationFilter jwtAuthenticationFilter;

    @MockBean
    private UserDetailsService userDetailsService;

    @Test
    @DisplayName("POST /api/v1/webhooks/brainsentry should dispatch webhook payload")
    void shouldDispatchBrainSentryWebhook() throws Exception {
        when(serviceJwtProvider.validateToken("token", "brainsentry")).thenReturn(true);

        mockMvc.perform(post("/api/v1/webhooks/brainsentry")
                        .header("Authorization", "Bearer token")
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {"event":"pattern.detected","pattern":"controller-test-harness"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        verify(integrationWebhookService).handleBrainSentryWebhook(anyMap());
    }

    @Test
    @DisplayName("POST /api/v1/webhooks/live should reject invalid token")
    void shouldRejectInvalidLiveWebhookToken() throws Exception {
        when(serviceJwtProvider.validateToken("bad-token", "squadx-live")).thenReturn(false);

        mockMvc.perform(post("/api/v1/webhooks/live")
                        .header("Authorization", "Bearer bad-token")
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {"event":"session.ended","sessionId":"sess-1"}
                                """))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    @DisplayName("POST /api/v1/webhooks/live should dispatch live reconciliation")
    void shouldDispatchLiveWebhook() throws Exception {
        when(serviceJwtProvider.validateToken("token", "squadx-live")).thenReturn(true);

        mockMvc.perform(post("/api/v1/webhooks/live")
                        .header("Authorization", "Bearer token")
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {"event":"recording.ready","sessionId":"sess-1","durationSeconds":120}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        verify(integrationWebhookService).handleLiveWebhook(anyMap());
    }
}
