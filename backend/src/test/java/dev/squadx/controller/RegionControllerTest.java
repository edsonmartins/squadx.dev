package dev.squadx.controller;

import dev.squadx.config.RegionConfig;
import dev.squadx.exception.GlobalExceptionHandler;
import dev.squadx.security.JwtAuthenticationFilter;
import dev.squadx.security.JwtService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(
        value = RegionController.class,
        excludeAutoConfiguration = org.springframework.boot.autoconfigure.security.oauth2.client.servlet.OAuth2ClientAutoConfiguration.class
)
@AutoConfigureMockMvc(addFilters = false)
@Import(GlobalExceptionHandler.class)
class RegionControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private RegionConfig regionConfig;

    @MockBean
    private JwtService jwtService;

    @MockBean
    private JwtAuthenticationFilter jwtAuthenticationFilter;

    @MockBean
    private UserDetailsService userDetailsService;

    @BeforeEach
    void setUp() {
        when(regionConfig.getCurrent()).thenReturn("us-east-1");
        when(regionConfig.getAvailable()).thenReturn(List.of("us-east-1", "eu-west-1"));
    }

    @Nested
    @DisplayName("GET /api/v1/regions")
    class ListRegionsEndpoint {

        @Test
        @DisplayName("should return all available regions")
        void shouldReturnAllRegions() throws Exception {
            mockMvc.perform(get("/api/v1/regions"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data.length()").value(2))
                    .andExpect(jsonPath("$.data[0].name").value("us-east-1"))
                    .andExpect(jsonPath("$.data[0].status").value("active"))
                    .andExpect(jsonPath("$.data[1].name").value("eu-west-1"))
                    .andExpect(jsonPath("$.data[1].status").value("available"));
        }

        @Test
        @DisplayName("should map display names for known regions")
        void shouldMapDisplayNames() throws Exception {
            mockMvc.perform(get("/api/v1/regions"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data[0].display_name").value("US East (N. Virginia)"))
                    .andExpect(jsonPath("$.data[1].display_name").value("Europe (Ireland)"));
        }
    }

    @Nested
    @DisplayName("GET /api/v1/regions/current")
    class GetCurrentRegionEndpoint {

        @Test
        @DisplayName("should return current active region")
        void shouldReturnCurrentRegion() throws Exception {
            mockMvc.perform(get("/api/v1/regions/current"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data.name").value("us-east-1"))
                    .andExpect(jsonPath("$.data.display_name").value("US East (N. Virginia)"))
                    .andExpect(jsonPath("$.data.status").value("active"));
        }
    }
}
