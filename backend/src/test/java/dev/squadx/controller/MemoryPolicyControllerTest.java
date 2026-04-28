package dev.squadx.controller;

import dev.squadx.exception.GlobalExceptionHandler;
import dev.squadx.security.JwtAuthenticationFilter;
import dev.squadx.security.JwtService;
import dev.squadx.service.MemoryPolicyService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Map;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(
        value = MemoryPolicyController.class,
        excludeAutoConfiguration = org.springframework.boot.autoconfigure.security.oauth2.client.servlet.OAuth2ClientAutoConfiguration.class
)
@AutoConfigureMockMvc(addFilters = false)
@Import(GlobalExceptionHandler.class)
class MemoryPolicyControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private MemoryPolicyService memoryPolicyService;

    @MockBean
    private JwtService jwtService;

    @MockBean
    private JwtAuthenticationFilter jwtAuthenticationFilter;

    @MockBean
    private UserDetailsService userDetailsService;

    @Test
    @DisplayName("GET /api/v1/memory/policy should return active policy")
    void shouldReturnMemoryPolicy() throws Exception {
        when(memoryPolicyService.describePolicy()).thenReturn(Map.of(
                "provider", "brainsentry",
                "enabled", true,
                "memoryScope", "project-agent",
                "proceduralMemoryEnabled", true,
                "proceduralLimit", 5,
                "status", "ACTIVE"
        ));

        mockMvc.perform(get("/api/v1/memory/policy"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.provider").value("brainsentry"))
                .andExpect(jsonPath("$.data.memoryScope").value("project-agent"))
                .andExpect(jsonPath("$.data.proceduralMemoryEnabled").value(true));
    }
}
