package dev.squadx.controller;

import dev.squadx.exception.GlobalExceptionHandler;
import dev.squadx.security.JwtAuthenticationFilter;
import dev.squadx.security.JwtService;
import dev.squadx.service.CapabilityRegistryService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Map;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(
        value = CapabilityController.class,
        excludeAutoConfiguration = org.springframework.boot.autoconfigure.security.oauth2.client.servlet.OAuth2ClientAutoConfiguration.class
)
@AutoConfigureMockMvc(addFilters = false)
@Import(GlobalExceptionHandler.class)
class CapabilityControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private CapabilityRegistryService capabilityRegistryService;

    @MockBean
    private JwtService jwtService;

    @MockBean
    private JwtAuthenticationFilter jwtAuthenticationFilter;

    @MockBean
    private UserDetailsService userDetailsService;

    @Test
    @DisplayName("GET /api/v1/capabilities should return registry payload")
    void shouldReturnCapabilityRegistry() throws Exception {
        when(capabilityRegistryService.getCapabilities()).thenReturn(Map.of(
                "summary", Map.of("integrations", 3, "notificationProviders", 1, "proceduralMemoryEnabled", true),
                "integrations", Map.of(
                        "brainsentry", Map.of("status", "UP", "enabled", true),
                        "live", Map.of("status", "DISABLED", "enabled", false),
                        "memoryPolicy", Map.of("status", "ACTIVE", "memoryScope", "adaptive", "proceduralMemoryEnabled", true)
                ),
                "notifications", List.of(
                        Map.of("key", "notifications.slack", "enabled", true, "status", "AVAILABLE")
                )
        ));

        mockMvc.perform(get("/api/v1/capabilities"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.summary.integrations").value(3))
                .andExpect(jsonPath("$.data.integrations.brainsentry.status").value("UP"))
                .andExpect(jsonPath("$.data.integrations.memoryPolicy.status").value("ACTIVE"))
                .andExpect(jsonPath("$.data.notifications[0].key").value("notifications.slack"));
    }
}
