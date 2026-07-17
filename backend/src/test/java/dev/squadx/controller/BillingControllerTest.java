package dev.squadx.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.squadx.exception.GlobalExceptionHandler;
import dev.squadx.exception.ResourceNotFoundException;
import dev.squadx.model.Subscription;
import dev.squadx.model.User;
import dev.squadx.model.enums.SubscriptionPlan;
import dev.squadx.model.enums.SubscriptionStatus;
import dev.squadx.model.enums.UserRole;
import dev.squadx.security.JwtAuthenticationFilter;
import dev.squadx.security.JwtService;
import dev.squadx.service.BillingService;
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

import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(
        value = BillingController.class,
        excludeAutoConfiguration = org.springframework.boot.autoconfigure.security.oauth2.client.servlet.OAuth2ClientAutoConfiguration.class
)
@AutoConfigureMockMvc(addFilters = false)
@Import(GlobalExceptionHandler.class)
class BillingControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private BillingService billingService;

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
    }

    @Nested
    @DisplayName("POST /api/v1/billing/checkout")
    class CheckoutEndpoint {

        @Test
        @DisplayName("should create checkout session and return 201")
        void shouldCreateCheckoutSession() throws Exception {
            Map<String, Object> request = Map.of(
                    "organizationId", 10,
                    "plan", "PRO"
            );

            Map<String, String> sessionResponse = Map.of("sessionUrl", "https://checkout.stripe.com/session123");

            when(billingService.createCheckoutSession(eq(10L), eq("PRO"), nullable(User.class)))
                    .thenReturn(sessionResponse);

            mockMvc.perform(post("/api/v1/billing/checkout")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request))
                            .with(authentication(new UsernamePasswordAuthenticationToken(testUser, null, testUser.getAuthorities()))))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data.sessionUrl").value("https://checkout.stripe.com/session123"))
                    .andExpect(jsonPath("$.message").value("Checkout session created successfully"));
        }
    }

    @Nested
    @DisplayName("POST /api/v1/billing/webhook")
    class WebhookEndpoint {

        @Test
        @DisplayName("should handle webhook and return 200")
        void shouldHandleWebhookSuccessfully() throws Exception {
            doNothing().when(billingService).handleWebhook(any(String.class), any(String.class));

            mockMvc.perform(post("/api/v1/billing/webhook")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"type\":\"checkout.session.completed\"}")
                            .header("Stripe-Signature", "sig_test_123"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.message").value("Webhook processed successfully"));
        }
    }

    @Nested
    @DisplayName("GET /api/v1/billing/subscription")
    class GetSubscriptionEndpoint {

        @Test
        @DisplayName("should return subscription with 200")
        void shouldReturnSubscription() throws Exception {
            Subscription subscription = Subscription.builder()
                    .plan(SubscriptionPlan.STARTER)
                    .status(SubscriptionStatus.ACTIVE)
                    .stripeCustomerId("cus_test")
                    .stripeSubscriptionId("sub_test")
                    .build();

            when(billingService.getSubscription(eq(10L), nullable(User.class))).thenReturn(subscription);

            mockMvc.perform(get("/api/v1/billing/subscription")
                            .param("organizationId", "10")
                            .with(authentication(new UsernamePasswordAuthenticationToken(testUser, null, testUser.getAuthorities()))))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true));
        }

        @Test
        @DisplayName("should return 404 when subscription not found")
        void shouldReturn404WhenNotFound() throws Exception {
            when(billingService.getSubscription(eq(999L), nullable(User.class)))
                    .thenThrow(new ResourceNotFoundException("Subscription not found"));

            mockMvc.perform(get("/api/v1/billing/subscription")
                            .param("organizationId", "999")
                            .with(authentication(new UsernamePasswordAuthenticationToken(testUser, null, testUser.getAuthorities()))))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.success").value(false))
                    .andExpect(jsonPath("$.message").value("Subscription not found"));
        }
    }

    @Nested
    @DisplayName("POST /api/v1/billing/cancel")
    class CancelSubscriptionEndpoint {

        @Test
        @DisplayName("should cancel subscription and return 200")
        void shouldCancelSubscription() throws Exception {
            Map<String, Object> request = Map.of("organizationId", 10);

            doNothing().when(billingService).cancelSubscription(eq(10L), nullable(User.class));

            mockMvc.perform(post("/api/v1/billing/cancel")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request))
                            .with(authentication(new UsernamePasswordAuthenticationToken(testUser, null, testUser.getAuthorities()))))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.message").value("Subscription cancelled successfully"));
        }
    }
}
