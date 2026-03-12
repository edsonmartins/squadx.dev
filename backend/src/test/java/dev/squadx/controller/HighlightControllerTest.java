package dev.squadx.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.squadx.dto.highlight.GenerateHighlightsRequest;
import dev.squadx.dto.highlight.HighlightResponse;
import dev.squadx.exception.GlobalExceptionHandler;
import dev.squadx.exception.ResourceNotFoundException;
import dev.squadx.model.User;
import dev.squadx.model.enums.HighlightType;
import dev.squadx.model.enums.UserRole;
import dev.squadx.security.JwtAuthenticationFilter;
import dev.squadx.security.JwtService;
import dev.squadx.service.HighlightService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.bean.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(HighlightController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(GlobalExceptionHandler.class)
class HighlightControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private HighlightService highlightService;

    @MockBean
    private JwtService jwtService;

    @MockBean
    private JwtAuthenticationFilter jwtAuthenticationFilter;

    @MockBean
    private UserDetailsService userDetailsService;

    private User testUser;
    private HighlightResponse sampleHighlight;

    @BeforeEach
    void setUp() {
        testUser = User.builder()
                .email("dev@example.com")
                .password("encoded")
                .fullName("Dev User")
                .role(UserRole.USER)
                .build();
        testUser.setId(1L);

        sampleHighlight = HighlightResponse.builder()
                .id(1L)
                .recordingId(100L)
                .highlightType(HighlightType.BUG_FOUND)
                .timestampSeconds(120)
                .title("Bug detected in login flow")
                .description("NullPointerException at line 42")
                .confidence(0.95f)
                .createdAt(Instant.now())
                .build();
    }

    @Nested
    @DisplayName("POST /api/v1/highlights/generate")
    class GenerateEndpoint {

        @Test
        @DisplayName("should generate highlights and return 201")
        void shouldGenerateHighlights() throws Exception {
            GenerateHighlightsRequest request = GenerateHighlightsRequest.builder()
                    .recordingId(100L)
                    .executionLogs(List.of("log1", "log2"))
                    .build();

            when(highlightService.generateHighlights(eq(100L), any()))
                    .thenReturn(List.of(sampleHighlight));

            mockMvc.perform(post("/api/v1/highlights/generate")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request))
                            .with(user(testUser)))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.message").value("Highlights generated"))
                    .andExpect(jsonPath("$.data[0].title").value("Bug detected in login flow"));
        }

        @Test
        @DisplayName("should return 400 when recording id is missing")
        void shouldReturn400WhenRecordingIdMissing() throws Exception {
            String requestBody = "{\"execution_logs\":[\"log1\"]}";

            mockMvc.perform(post("/api/v1/highlights/generate")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(requestBody)
                            .with(user(testUser)))
                    .andExpect(status().isBadRequest());
        }
    }

    @Nested
    @DisplayName("GET /api/v1/highlights/recording/{recordingId}")
    class GetByRecordingEndpoint {

        @Test
        @DisplayName("should return highlights for a recording")
        void shouldReturnHighlightsForRecording() throws Exception {
            when(highlightService.getByRecording(100L)).thenReturn(List.of(sampleHighlight));

            mockMvc.perform(get("/api/v1/highlights/recording/100")
                            .with(user(testUser)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data[0].recording_id").value(100))
                    .andExpect(jsonPath("$.data[0].highlight_type").value("BUG_FOUND"));
        }
    }

    @Nested
    @DisplayName("GET /api/v1/highlights/recording/{recordingId}/summary")
    class GetSummaryEndpoint {

        @Test
        @DisplayName("should return AI summary for a recording")
        void shouldReturnSummary() throws Exception {
            when(highlightService.getSummary(100L)).thenReturn("Session had 2 bugs and 1 deploy.");

            mockMvc.perform(get("/api/v1/highlights/recording/100/summary")
                            .with(user(testUser)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data.summary").value("Session had 2 bugs and 1 deploy."));
        }

        @Test
        @DisplayName("should return 404 when recording not found")
        void shouldReturn404WhenRecordingNotFound() throws Exception {
            when(highlightService.getSummary(999L))
                    .thenThrow(new ResourceNotFoundException("Recording not found"));

            mockMvc.perform(get("/api/v1/highlights/recording/999/summary")
                            .with(user(testUser)))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.success").value(false))
                    .andExpect(jsonPath("$.message").value("Recording not found"));
        }
    }
}
