package dev.squadx.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.squadx.dto.liveview.JoinSessionRequest;
import dev.squadx.dto.liveview.LiveSessionRequest;
import dev.squadx.dto.liveview.LiveSessionResponse;
import dev.squadx.exception.GlobalExceptionHandler;
import dev.squadx.exception.ResourceNotFoundException;
import dev.squadx.model.User;
import dev.squadx.model.enums.LiveSessionStatus;
import dev.squadx.model.enums.UserRole;
import dev.squadx.security.JwtAuthenticationFilter;
import dev.squadx.security.JwtService;
import dev.squadx.service.LiveViewService;
import dev.squadx.service.SupabaseLiveSessionService;
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
        value = LiveViewController.class,
        excludeAutoConfiguration = org.springframework.boot.autoconfigure.security.oauth2.client.servlet.OAuth2ClientAutoConfiguration.class
)
@AutoConfigureMockMvc(addFilters = false)
@Import(GlobalExceptionHandler.class)
class LiveViewControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private LiveViewService liveViewService;

    @MockBean
    private SupabaseLiveSessionService supabaseLiveSessionService;

    @MockBean
    private JwtService jwtService;

    @MockBean
    private JwtAuthenticationFilter jwtAuthenticationFilter;

    @MockBean
    private UserDetailsService userDetailsService;

    private User testUser;
    private LiveSessionResponse sampleSession;

    @BeforeEach
    void setUp() {
        testUser = User.builder()
                .email("test@example.com")
                .password("encoded")
                .fullName("Test User")
                .role(UserRole.USER)
                .build();
        testUser.setId(1L);

        sampleSession = LiveSessionResponse.builder()
                .id(1L)
                .code("ABC123")
                .taskId(10L)
                .taskTitle("Test Task")
                .hostUserId(1L)
                .hostUserName("Test User")
                .status(LiveSessionStatus.PENDING)
                .maxViewers(5)
                .currentViewers(0)
                .resolution("1280x720")
                .createdAt(Instant.now())
                .build();
    }

    @Nested
    @DisplayName("POST /api/v1/live-view/sessions")
    class CreateSessionEndpoint {

        @Test
        @DisplayName("should create live session and return 201")
        void shouldCreateSessionSuccessfully() throws Exception {
            LiveSessionRequest request = new LiveSessionRequest();
            request.setTaskId(10L);
            request.setMaxViewers(5);

            when(liveViewService.createSession(any(LiveSessionRequest.class), nullable(User.class)))
                    .thenReturn(sampleSession);

            mockMvc.perform(post("/api/v1/live-view/sessions")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request))
                            .with(authentication(new UsernamePasswordAuthenticationToken(testUser, null, testUser.getAuthorities()))))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data.id").value(1))
                    .andExpect(jsonPath("$.data.code").value("ABC123"))
                    .andExpect(jsonPath("$.message").value("Live session created"));
        }
    }

    @Nested
    @DisplayName("POST /api/v1/live-view/sessions/join")
    class JoinSessionEndpoint {

        @Test
        @DisplayName("should join live session and return 200")
        void shouldJoinSessionSuccessfully() throws Exception {
            JoinSessionRequest request = new JoinSessionRequest();
            request.setCode("ABC123");

            when(liveViewService.joinSession(any(JoinSessionRequest.class), nullable(User.class)))
                    .thenReturn(sampleSession);

            mockMvc.perform(post("/api/v1/live-view/sessions/join")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request))
                            .with(authentication(new UsernamePasswordAuthenticationToken(testUser, null, testUser.getAuthorities()))))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.message").value("Joined live session"));
        }
    }

    @Nested
    @DisplayName("POST /api/v1/live-view/sessions/{sessionId}/end")
    class EndSessionEndpoint {

        @Test
        @DisplayName("should end live session and return 200")
        void shouldEndSessionSuccessfully() throws Exception {
            LiveSessionResponse endedSession = LiveSessionResponse.builder()
                    .id(1L)
                    .status(LiveSessionStatus.ENDED)
                    .build();

            when(liveViewService.endSession(eq(1L), nullable(User.class)))
                    .thenReturn(endedSession);

            mockMvc.perform(post("/api/v1/live-view/sessions/1/end")
                            .with(authentication(new UsernamePasswordAuthenticationToken(testUser, null, testUser.getAuthorities()))))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.message").value("Live session ended"));
        }
    }

    @Nested
    @DisplayName("GET /api/v1/live-view/sessions/code/{code}")
    class GetByCodeEndpoint {

        @Test
        @DisplayName("should return session by code with 200")
        void shouldReturnSessionByCode() throws Exception {
            when(liveViewService.getByCode(eq("ABC123"), nullable(User.class)))
                    .thenReturn(sampleSession);

            mockMvc.perform(get("/api/v1/live-view/sessions/code/ABC123")
                            .with(authentication(new UsernamePasswordAuthenticationToken(testUser, null, testUser.getAuthorities()))))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data.code").value("ABC123"));
        }

        @Test
        @DisplayName("should return 404 when session code not found")
        void shouldReturn404WhenCodeNotFound() throws Exception {
            when(liveViewService.getByCode(eq("XXXXXX"), nullable(User.class)))
                    .thenThrow(new ResourceNotFoundException("Session not found"));

            mockMvc.perform(get("/api/v1/live-view/sessions/code/XXXXXX")
                            .with(authentication(new UsernamePasswordAuthenticationToken(testUser, null, testUser.getAuthorities()))))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.success").value(false))
                    .andExpect(jsonPath("$.message").value("Session not found"));
        }
    }
}
