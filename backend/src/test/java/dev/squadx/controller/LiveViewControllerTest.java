package dev.squadx.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.squadx.dto.liveview.JoinSessionRequest;
import dev.squadx.dto.liveview.LiveChatMessageRequest;
import dev.squadx.dto.liveview.LiveChatMessageResponse;
import dev.squadx.dto.liveview.LiveSessionRequest;
import dev.squadx.dto.liveview.LiveSessionResponse;
import dev.squadx.dto.supabase.SupabaseLiveSession;
import dev.squadx.exception.GlobalExceptionHandler;
import dev.squadx.exception.ResourceNotFoundException;
import dev.squadx.model.Organization;
import dev.squadx.model.Project;
import dev.squadx.model.Task;
import dev.squadx.model.User;
import dev.squadx.model.enums.LiveSessionStatus;
import dev.squadx.model.enums.UserRole;
import dev.squadx.repository.OrganizationMemberRepository;
import dev.squadx.repository.TaskRepository;
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
    private TaskRepository taskRepository;

    @MockBean
    private OrganizationMemberRepository memberRepository;

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
    @DisplayName("POST /api/v1/live-view/agents/{agentId}/direct-session")
    class EnsureDirectAgentSessionEndpoint {

        @Test
        @DisplayName("should ensure direct agent session and return 201")
        void shouldEnsureDirectAgentSessionSuccessfully() throws Exception {
            LiveSessionResponse response = LiveSessionResponse.builder()
                    .id(2L)
                    .code("DIRECT01")
                    .agentId(9L)
                    .agentName("Builder")
                    .sessionMode("DIRECT_AGENT")
                    .status(LiveSessionStatus.ACTIVE)
                    .hostUserId(1L)
                    .hostUserName("Test User")
                    .maxViewers(25)
                    .currentViewers(1)
                    .resolution("1280x720")
                    .createdAt(Instant.now())
                    .build();

            when(liveViewService.ensureDirectAgentSession(eq(9L), nullable(User.class)))
                    .thenReturn(response);

            mockMvc.perform(post("/api/v1/live-view/agents/9/direct-session")
                            .with(authentication(new UsernamePasswordAuthenticationToken(testUser, null, testUser.getAuthorities()))))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data.agentId").value(9))
                    .andExpect(jsonPath("$.data.sessionMode").value("DIRECT_AGENT"))
                    .andExpect(jsonPath("$.message").value("Direct agent session ready"));
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

    @Nested
    @DisplayName("live chat endpoints")
    class LiveChatEndpoints {

        @Test
        @DisplayName("should return chat history with 200")
        void shouldReturnChatHistory() throws Exception {
            LiveChatMessageResponse message = LiveChatMessageResponse.builder()
                    .id("msg-1")
                    .content("Hello")
                    .messageType("text")
                    .build();

            when(liveViewService.getChatHistory(eq(1L), eq(100), nullable(String.class), nullable(String.class), nullable(User.class)))
                    .thenReturn(List.of(message));

            mockMvc.perform(get("/api/v1/live-view/sessions/1/chat")
                            .with(authentication(new UsernamePasswordAuthenticationToken(testUser, null, testUser.getAuthorities()))))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data[0].id").value("msg-1"));
        }

        @Test
        @DisplayName("should send agent live message with 201")
        void shouldSendAgentLiveMessage() throws Exception {
            LiveChatMessageRequest request = new LiveChatMessageRequest();
            request.setContent("Agent online");

            LiveChatMessageResponse response = LiveChatMessageResponse.builder()
                    .id("msg-1")
                    .content("Agent online")
                    .messageType("text")
                    .build();

            when(liveViewService.sendAgentMessage(eq(1L), any(LiveChatMessageRequest.class), nullable(User.class)))
                    .thenReturn(response);

            mockMvc.perform(post("/api/v1/live-view/sessions/1/chat/agent-message")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request))
                            .with(authentication(new UsernamePasswordAuthenticationToken(testUser, null, testUser.getAuthorities()))))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data.id").value("msg-1"))
                    .andExpect(jsonPath("$.message").value("Agent live message sent"));
        }
    }

    @Nested
    @DisplayName("Supabase endpoints — organization scoping")
    class SupabaseEndpoints {

        @org.junit.jupiter.api.AfterEach
        void clearContext() {
            org.springframework.security.core.context.SecurityContextHolder.clearContext();
        }

        // With addFilters=false the security filter chain never populates the
        // SecurityContextHolder, so @AuthenticationPrincipal resolves to null.
        // Set it directly so the resolver sees a real member for the allow-path tests.
        private void authenticateAsTestUser() {
            org.springframework.security.core.context.SecurityContextHolder.getContext()
                    .setAuthentication(new UsernamePasswordAuthenticationToken(
                            testUser, null, testUser.getAuthorities()));
        }

        private Task taskInOrg(Long taskId, Long orgId) {
            Organization org = Organization.builder().build();
            org.setId(orgId);
            Project project = Project.builder().organization(org).build();
            project.setId(100L);
            Task task = Task.builder().title("t").project(project).build();
            task.setId(taskId);
            return task;
        }

        private SupabaseLiveSession supaSession(Long taskId, String code) {
            SupabaseLiveSession s = new SupabaseLiveSession();
            s.setTaskId(taskId);
            s.setJoinCode(code);
            s.setStatus("active");
            s.setMaxViewers(25);
            return s;
        }

        private org.springframework.test.web.servlet.request.RequestPostProcessor asTestUser() {
            return authentication(new UsernamePasswordAuthenticationToken(
                    testUser, null, testUser.getAuthorities()));
        }

        @Test
        @DisplayName("active: filters to sessions in the caller's organizations")
        void activeFiltersByOrg() throws Exception {
            when(supabaseLiveSessionService.getActiveSessions())
                    .thenReturn(List.of(supaSession(10L, "AAA"), supaSession(20L, "BBB")));
            when(taskRepository.findById(10L)).thenReturn(java.util.Optional.of(taskInOrg(10L, 1L)));
            when(taskRepository.findById(20L)).thenReturn(java.util.Optional.of(taskInOrg(20L, 2L)));
            when(memberRepository.existsByOrganizationIdAndUserId(eq(1L), eq(1L))).thenReturn(true);
            when(memberRepository.existsByOrganizationIdAndUserId(eq(2L), eq(1L))).thenReturn(false);
            when(supabaseLiveSessionService.toResponse(any())).thenReturn(sampleSession);
            authenticateAsTestUser();

            mockMvc.perform(get("/api/v1/live-view/supabase/sessions/active").with(asTestUser()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.length()").value(1));
        }

        @Test
        @DisplayName("by-task: 403 when the caller is not an organization member")
        void byTaskForbiddenForNonMember() throws Exception {
            when(taskRepository.findById(20L)).thenReturn(java.util.Optional.of(taskInOrg(20L, 2L)));
            when(memberRepository.existsByOrganizationIdAndUserId(eq(2L), eq(1L))).thenReturn(false);

            mockMvc.perform(get("/api/v1/live-view/supabase/sessions/task/20").with(asTestUser()))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("by-code: 404 (no cross-tenant leak) when the caller is not a member")
        void byCodeNotFoundForNonMember() throws Exception {
            when(supabaseLiveSessionService.getSessionByCode("BBB"))
                    .thenReturn(java.util.Optional.of(supaSession(20L, "BBB")));
            when(taskRepository.findById(20L)).thenReturn(java.util.Optional.of(taskInOrg(20L, 2L)));
            when(memberRepository.existsByOrganizationIdAndUserId(eq(2L), eq(1L))).thenReturn(false);

            mockMvc.perform(get("/api/v1/live-view/supabase/sessions/code/BBB").with(asTestUser()))
                    .andExpect(status().isNotFound());
        }

        @Test
        @DisplayName("by-code: 200 when the caller is an organization member")
        void byCodeOkForMember() throws Exception {
            when(supabaseLiveSessionService.getSessionByCode("AAA"))
                    .thenReturn(java.util.Optional.of(supaSession(10L, "AAA")));
            when(supabaseLiveSessionService.toResponse(any())).thenReturn(sampleSession);
            when(taskRepository.findById(10L)).thenReturn(java.util.Optional.of(taskInOrg(10L, 1L)));
            when(memberRepository.existsByOrganizationIdAndUserId(eq(1L), eq(1L))).thenReturn(true);
            authenticateAsTestUser();

            mockMvc.perform(get("/api/v1/live-view/supabase/sessions/code/AAA").with(asTestUser()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.code").value("ABC123"));
        }
    }
}
