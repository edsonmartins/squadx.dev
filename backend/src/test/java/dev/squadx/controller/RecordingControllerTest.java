package dev.squadx.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.squadx.dto.recording.CompleteRecordingRequest;
import dev.squadx.dto.recording.RecordingResponse;
import dev.squadx.dto.recording.StartRecordingRequest;
import dev.squadx.exception.GlobalExceptionHandler;
import dev.squadx.exception.ResourceNotFoundException;
import dev.squadx.model.User;
import dev.squadx.model.enums.RecordingStatus;
import dev.squadx.model.enums.UserRole;
import dev.squadx.security.JwtAuthenticationFilter;
import dev.squadx.security.JwtService;
import dev.squadx.service.RecordingService;
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

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(
        value = RecordingController.class,
        excludeAutoConfiguration = org.springframework.boot.autoconfigure.security.oauth2.client.servlet.OAuth2ClientAutoConfiguration.class
)
@AutoConfigureMockMvc(addFilters = false)
@Import(GlobalExceptionHandler.class)
class RecordingControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private RecordingService recordingService;

    @MockBean
    private JwtService jwtService;

    @MockBean
    private JwtAuthenticationFilter jwtAuthenticationFilter;

    @MockBean
    private UserDetailsService userDetailsService;

    private User testUser;
    private RecordingResponse sampleRecording;

    @BeforeEach
    void setUp() {
        testUser = User.builder()
                .email("test@example.com")
                .password("encoded")
                .fullName("Test User")
                .role(UserRole.USER)
                .build();
        testUser.setId(1L);

        sampleRecording = RecordingResponse.builder()
                .id(1L)
                .sessionId(10L)
                .s3Key("recordings/session-10/rec-1.webm")
                .s3Bucket("squadx-recordings")
                .uploadUrl("https://s3.amazonaws.com/upload-url")
                .status(RecordingStatus.RECORDING)
                .startedAt(Instant.now())
                .createdAt(Instant.now())
                .build();
    }

    @Nested
    @DisplayName("POST /api/v1/recordings/start")
    class StartRecordingEndpoint {

        @Test
        @DisplayName("should start recording and return 201")
        void shouldStartRecordingSuccessfully() throws Exception {
            StartRecordingRequest request = new StartRecordingRequest();
            request.setSessionId(10L);

            when(recordingService.startRecording(10L)).thenReturn(sampleRecording);

            mockMvc.perform(post("/api/v1/recordings/start")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request))
                            .with(authentication(new UsernamePasswordAuthenticationToken(testUser, null, testUser.getAuthorities()))))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data.id").value(1))
                    .andExpect(jsonPath("$.message").value("Recording started"));
        }
    }

    @Nested
    @DisplayName("POST /api/v1/recordings/{id}/complete")
    class CompleteRecordingEndpoint {

        @Test
        @DisplayName("should complete recording and return 200")
        void shouldCompleteRecordingSuccessfully() throws Exception {
            CompleteRecordingRequest request = new CompleteRecordingRequest();
            request.setFileSizeBytes(1024000L);
            request.setDurationSeconds(300);

            RecordingResponse completed = RecordingResponse.builder()
                    .id(1L)
                    .sessionId(10L)
                    .status(RecordingStatus.COMPLETED)
                    .fileSizeBytes(1024000L)
                    .durationSeconds(300)
                    .completedAt(Instant.now())
                    .build();

            when(recordingService.completeRecording(eq(1L), eq(1024000L), eq(300)))
                    .thenReturn(completed);

            mockMvc.perform(post("/api/v1/recordings/1/complete")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request))
                            .with(authentication(new UsernamePasswordAuthenticationToken(testUser, null, testUser.getAuthorities()))))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.message").value("Recording completed"));
        }
    }

    @Nested
    @DisplayName("GET /api/v1/recordings/{id}/url")
    class GetRecordingUrlEndpoint {

        @Test
        @DisplayName("should return recording URL with 200")
        void shouldReturnRecordingUrl() throws Exception {
            RecordingResponse withUrl = RecordingResponse.builder()
                    .id(1L)
                    .playbackUrl("https://s3.amazonaws.com/playback-url")
                    .status(RecordingStatus.COMPLETED)
                    .build();

            when(recordingService.getRecordingUrl(1L)).thenReturn(withUrl);

            mockMvc.perform(get("/api/v1/recordings/1/url")
                            .with(authentication(new UsernamePasswordAuthenticationToken(testUser, null, testUser.getAuthorities()))))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data.playback_url").value("https://s3.amazonaws.com/playback-url"));
        }

        @Test
        @DisplayName("should return 404 when recording not found")
        void shouldReturn404WhenNotFound() throws Exception {
            when(recordingService.getRecordingUrl(999L))
                    .thenThrow(new ResourceNotFoundException("Recording not found"));

            mockMvc.perform(get("/api/v1/recordings/999/url")
                            .with(authentication(new UsernamePasswordAuthenticationToken(testUser, null, testUser.getAuthorities()))))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.success").value(false))
                    .andExpect(jsonPath("$.message").value("Recording not found"));
        }
    }

    @Nested
    @DisplayName("GET /api/v1/recordings/session/{sessionId}")
    class ListBySessionEndpoint {

        @Test
        @DisplayName("should return recordings by session with 200")
        void shouldReturnRecordingsBySession() throws Exception {
            when(recordingService.listBySession(10L))
                    .thenReturn(List.of(sampleRecording));

            mockMvc.perform(get("/api/v1/recordings/session/10")
                            .with(authentication(new UsernamePasswordAuthenticationToken(testUser, null, testUser.getAuthorities()))))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data[0].id").value(1))
                    .andExpect(jsonPath("$.data[0].session_id").value(10));
        }
    }
}
