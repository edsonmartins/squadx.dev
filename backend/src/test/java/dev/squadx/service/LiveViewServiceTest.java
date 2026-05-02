package dev.squadx.service;

import dev.squadx.dto.liveview.JoinSessionRequest;
import dev.squadx.dto.liveview.LiveChatMessageRequest;
import dev.squadx.dto.liveview.LiveChatMessageResponse;
import dev.squadx.dto.liveview.LiveSessionRequest;
import dev.squadx.dto.liveview.LiveSessionResponse;
import dev.squadx.exception.BadRequestException;
import dev.squadx.exception.ForbiddenException;
import dev.squadx.exception.ResourceNotFoundException;
import dev.squadx.integration.SquadxLiveClient;
import dev.squadx.model.*;
import dev.squadx.model.enums.LiveSessionStatus;
import dev.squadx.model.enums.UserRole;
import dev.squadx.repository.LiveSessionParticipantRepository;
import dev.squadx.repository.LiveSessionRepository;
import dev.squadx.repository.OrganizationMemberRepository;
import dev.squadx.repository.AgentRepository;
import dev.squadx.repository.TaskRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class LiveViewServiceTest {

    @Mock
    private LiveSessionRepository liveSessionRepository;

    @Mock
    private LiveSessionParticipantRepository participantRepository;

    @Mock
    private TaskRepository taskRepository;

    @Mock
    private AgentRepository agentRepository;

    @Mock
    private OrganizationMemberRepository orgMemberRepository;

    @Mock
    private WebSocketEventService webSocketEventService;

    @Mock
    private SquadxLiveClient squadxLiveClient;

    @InjectMocks
    private LiveViewService liveViewService;

    private User hostUser;
    private User viewerUser;
    private Organization organization;
    private Project project;
    private Task task;
    private LiveSession activeSession;
    private Agent assignedAgent;

    @BeforeEach
    void setUp() {
        hostUser = User.builder()
                .email("host@example.com")
                .password("encoded")
                .fullName("Host User")
                .role(UserRole.USER)
                .isActive(true)
                .build();
        hostUser.setId(1L);

        viewerUser = User.builder()
                .email("viewer@example.com")
                .password("encoded")
                .fullName("Viewer User")
                .role(UserRole.USER)
                .isActive(true)
                .build();
        viewerUser.setId(2L);

        organization = Organization.builder()
                .name("Org")
                .slug("org")
                .build();
        organization.setId(100L);

        project = Project.builder()
                .name("Project")
                .slug("project")
                .organization(organization)
                .build();
        project.setId(10L);

        task = Task.builder()
                .title("Test Task")
                .project(project)
                .build();
        task.setId(5L);

        assignedAgent = Agent.builder()
                .name("Builder")
                .agentType(dev.squadx.model.enums.AgentType.BACKEND)
                .squad(Squad.builder().name("Alpha").organization(organization).build())
                .build();
        assignedAgent.setId(9L);

        activeSession = new LiveSession();
        activeSession.setId(20L);
        activeSession.setCode("ABCD1234");
        activeSession.setTask(task);
        activeSession.setHostUser(hostUser);
        activeSession.setStatus(LiveSessionStatus.ACTIVE);
        activeSession.setMaxViewers(10);
        activeSession.setResolution("1280x720");
    }

    @Nested
    @DisplayName("createSession()")
    class CreateSession {

        @Test
        @DisplayName("should create a new live session for a task")
        void shouldCreateSession() {
            LiveSessionRequest request = new LiveSessionRequest();
            request.setTaskId(5L);
            request.setMaxViewers(5);
            request.setResolution("1920x1080");

            when(taskRepository.findById(5L)).thenReturn(Optional.of(task));
            when(orgMemberRepository.existsByOrganizationIdAndUserId(100L, 1L)).thenReturn(true);
            when(liveSessionRepository.findByTaskIdAndStatus(5L, LiveSessionStatus.ACTIVE))
                    .thenReturn(Optional.empty());
            when(liveSessionRepository.existsByCode(anyString())).thenReturn(false);
            when(liveSessionRepository.save(any(LiveSession.class))).thenAnswer(inv -> {
                LiveSession s = inv.getArgument(0);
                s.setId(20L);
                return s;
            });
            when(participantRepository.save(any(LiveSessionParticipant.class)))
                    .thenAnswer(inv -> inv.getArgument(0));
            when(participantRepository.findBySessionIdAndLeftAtIsNull(any()))
                    .thenReturn(List.of());

            LiveSessionResponse response = liveViewService.createSession(request, hostUser);

            assertThat(response).isNotNull();
            assertThat(response.getStatus()).isEqualTo(LiveSessionStatus.PENDING);
            verify(liveSessionRepository).save(any(LiveSession.class));
            verify(participantRepository).save(any(LiveSessionParticipant.class));
        }

        @Test
        @DisplayName("should throw when task not found")
        void shouldThrowWhenTaskNotFound() {
            LiveSessionRequest request = new LiveSessionRequest();
            request.setTaskId(999L);

            when(taskRepository.findById(999L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> liveViewService.createSession(request, hostUser))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessage("Task not found");
        }

        @Test
        @DisplayName("should throw when user has no access to the task organization")
        void shouldThrowWhenNoAccess() {
            LiveSessionRequest request = new LiveSessionRequest();
            request.setTaskId(5L);

            when(taskRepository.findById(5L)).thenReturn(Optional.of(task));
            when(orgMemberRepository.existsByOrganizationIdAndUserId(100L, 1L)).thenReturn(false);

            assertThatThrownBy(() -> liveViewService.createSession(request, hostUser))
                    .isInstanceOf(ForbiddenException.class);
        }

        @Test
        @DisplayName("should throw when task already has an active session")
        void shouldThrowWhenActiveSessionExists() {
            LiveSessionRequest request = new LiveSessionRequest();
            request.setTaskId(5L);

            when(taskRepository.findById(5L)).thenReturn(Optional.of(task));
            when(orgMemberRepository.existsByOrganizationIdAndUserId(100L, 1L)).thenReturn(true);
            when(liveSessionRepository.findByTaskIdAndStatus(5L, LiveSessionStatus.ACTIVE))
                    .thenReturn(Optional.of(activeSession));

            assertThatThrownBy(() -> liveViewService.createSession(request, hostUser))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessage("Task already has an active live session");
        }
    }

    @Nested
    @DisplayName("ensureDirectAgentSession()")
    class EnsureDirectAgentSession {

        @Test
        @DisplayName("should create and return an active direct session for agent")
        void shouldCreateDirectAgentSession() {
            when(agentRepository.findById(9L)).thenReturn(Optional.of(assignedAgent));
            when(orgMemberRepository.existsByOrganizationIdAndUserId(100L, 1L)).thenReturn(true);
            when(liveSessionRepository.findByAgentIdAndStatus(9L, LiveSessionStatus.ACTIVE)).thenReturn(Optional.empty());
            when(liveSessionRepository.existsByCode(anyString())).thenReturn(false);
            when(liveSessionRepository.save(any(LiveSession.class))).thenAnswer(inv -> {
                LiveSession session = inv.getArgument(0);
                if (session.getId() == null) {
                    session.setId(30L);
                }
                return session;
            });
            when(participantRepository.save(any(LiveSessionParticipant.class))).thenAnswer(inv -> inv.getArgument(0));
            when(participantRepository.findBySessionIdAndLeftAtIsNull(30L)).thenReturn(List.of());
            when(squadxLiveClient.createSession(null, 9L, "p2p")).thenReturn(Map.of(
                    "sessionId", "direct-1",
                    "joinCode", "JOIN9999",
                    "joinUrl", "https://live.example/join/JOIN9999"
            ));
            when(squadxLiveClient.joinSession("JOIN9999", "Agent Builder")).thenReturn(Map.of("id", "guest-99"));

            LiveSessionResponse response = liveViewService.ensureDirectAgentSession(9L, hostUser);

            assertThat(response.getStatus()).isEqualTo(LiveSessionStatus.ACTIVE);
            assertThat(response.getAgentId()).isEqualTo(9L);
            assertThat(response.getSessionMode()).isEqualTo("DIRECT_AGENT");
            assertThat(response.getExternalSessionId()).isEqualTo("direct-1");
            verify(webSocketEventService).sendLiveSessionStarted(anyString(), eq(30L));
        }
    }

    @Nested
    @DisplayName("endSession()")
    class EndSession {

        @Test
        @DisplayName("should end session and mark all participants as left")
        void shouldEndSession() {
            LiveSessionParticipant participant = new LiveSessionParticipant();
            participant.setSession(activeSession);
            participant.setUser(viewerUser);
            participant.setIsHost(false);
            activeSession.setExternalSessionId("live-session-1");

            when(liveSessionRepository.findById(20L)).thenReturn(Optional.of(activeSession));
            when(liveSessionRepository.save(any(LiveSession.class))).thenAnswer(inv -> inv.getArgument(0));
            when(participantRepository.findBySessionIdAndLeftAtIsNull(20L))
                    .thenReturn(List.of(participant));
            when(participantRepository.save(any(LiveSessionParticipant.class)))
                    .thenAnswer(inv -> inv.getArgument(0));

            LiveSessionResponse response = liveViewService.endSession(20L, hostUser);

            assertThat(response.getStatus()).isEqualTo(LiveSessionStatus.ENDED);
            verify(squadxLiveClient).endSession("live-session-1");
            verify(webSocketEventService).sendLiveSessionEnded("ABCD1234", 20L);
            verify(participantRepository).save(participant);
        }

        @Test
        @DisplayName("should throw ForbiddenException when non-host tries to end session")
        void shouldThrowWhenNonHostEnds() {
            when(liveSessionRepository.findById(20L)).thenReturn(Optional.of(activeSession));

            assertThatThrownBy(() -> liveViewService.endSession(20L, viewerUser))
                    .isInstanceOf(ForbiddenException.class)
                    .hasMessage("Only the host can end the session");
        }
    }

    @Nested
    @DisplayName("startSession()")
    class StartSession {

        @Test
        @DisplayName("should create external live session when starting a pending session")
        void shouldCreateExternalSessionWhenStarting() {
            activeSession.setStatus(LiveSessionStatus.PENDING);

            when(liveSessionRepository.findById(20L)).thenReturn(Optional.of(activeSession));
            when(liveSessionRepository.save(any(LiveSession.class))).thenAnswer(inv -> inv.getArgument(0));
            when(participantRepository.findBySessionIdAndLeftAtIsNull(20L)).thenReturn(List.of());
            when(squadxLiveClient.createSession(5L, null, "p2p")).thenReturn(Map.of(
                    "sessionId", "live-session-1",
                    "joinCode", "JOIN1234",
                    "joinUrl", "https://live.example/join/JOIN1234"
            ));

            LiveSessionResponse response = liveViewService.startSession(20L, hostUser);

            assertThat(response.getStatus()).isEqualTo(LiveSessionStatus.ACTIVE);
            assertThat(response.getExternalSessionId()).isEqualTo("live-session-1");
            assertThat(response.getExternalJoinCode()).isEqualTo("JOIN1234");
            assertThat(response.getViewerUrl()).isEqualTo("https://live.example/join/JOIN1234");
            verify(squadxLiveClient).createSession(5L, null, "p2p");
            verify(webSocketEventService).sendLiveSessionStarted("ABCD1234", 20L);
        }

        @Test
        @DisplayName("should keep local session active when external live creation fails")
        void shouldKeepSessionActiveWhenExternalCreationFails() {
            activeSession.setStatus(LiveSessionStatus.PENDING);

            when(liveSessionRepository.findById(20L)).thenReturn(Optional.of(activeSession));
            when(liveSessionRepository.save(any(LiveSession.class))).thenAnswer(inv -> inv.getArgument(0));
            when(participantRepository.findBySessionIdAndLeftAtIsNull(20L)).thenReturn(List.of());
            when(squadxLiveClient.createSession(5L, null, "p2p")).thenReturn(null);

            LiveSessionResponse response = liveViewService.startSession(20L, hostUser);

            assertThat(response.getStatus()).isEqualTo(LiveSessionStatus.ACTIVE);
            assertThat(response.getExternalSessionId()).isNull();
            verify(webSocketEventService).sendLiveSessionStarted("ABCD1234", 20L);
        }

        @Test
        @DisplayName("should throw BadRequestException when session is not pending")
        void shouldThrowWhenNotPending() {
            activeSession.setStatus(LiveSessionStatus.ACTIVE);

            when(liveSessionRepository.findById(20L)).thenReturn(Optional.of(activeSession));

            assertThatThrownBy(() -> liveViewService.startSession(20L, hostUser))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessage("Session is not in pending status");
        }

        @Test
        @DisplayName("should register external agent participant when task has assigned agent")
        void shouldRegisterExternalAgentParticipantWhenAgentAssigned() {
            activeSession.setStatus(LiveSessionStatus.PENDING);
            task.setAssignedAgent(assignedAgent);

            when(liveSessionRepository.findById(20L)).thenReturn(Optional.of(activeSession));
            when(liveSessionRepository.save(any(LiveSession.class))).thenAnswer(inv -> inv.getArgument(0));
            when(participantRepository.findBySessionIdAndLeftAtIsNull(20L)).thenReturn(List.of());
            when(squadxLiveClient.createSession(5L, 9L, "p2p")).thenReturn(Map.of(
                    "sessionId", "live-session-1",
                    "joinCode", "JOIN1234",
                    "joinUrl", "https://live.example/join/JOIN1234"
            ));
            when(squadxLiveClient.joinSession("JOIN1234", "Agent Builder")).thenReturn(Map.of(
                    "id", "guest-1"
            ));

            LiveSessionResponse response = liveViewService.startSession(20L, hostUser);

            assertThat(response.getExternalSessionId()).isEqualTo("live-session-1");
            verify(squadxLiveClient).joinSession("JOIN1234", "Agent Builder");
        }
    }

    @Nested
    @DisplayName("agent live chat")
    class AgentLiveChat {

        @Test
        @DisplayName("should send agent message to linked squadx live session")
        void shouldSendAgentMessage() {
            task.setAssignedAgent(assignedAgent);
            activeSession.setExternalSessionId("sess-1");
            activeSession.setExternalJoinCode("JOIN1234");
            activeSession.setExternalAgentParticipantId("guest-1");

            LiveChatMessageRequest request = new LiveChatMessageRequest();
            request.setContent("Working on the fix now");

            when(liveSessionRepository.findById(20L)).thenReturn(Optional.of(activeSession));
            when(orgMemberRepository.existsByOrganizationIdAndUserId(100L, 1L)).thenReturn(true);
            when(liveSessionRepository.save(any(LiveSession.class))).thenAnswer(inv -> inv.getArgument(0));
            when(squadxLiveClient.sendChatMessage("sess-1", "Working on the fix now", "guest-1", null)).thenReturn(Map.of(
                    "id", "msg-1",
                    "session_id", "sess-1",
                    "participant_id", "guest-1",
                    "display_name", "Agent Builder",
                    "content", "Working on the fix now",
                    "message_type", "text",
                    "created_at", "2026-04-29T00:00:00Z"
            ));

            LiveChatMessageResponse response = liveViewService.sendAgentMessage(20L, request, hostUser);

            assertThat(response.getId()).isEqualTo("msg-1");
            assertThat(response.getParticipantId()).isEqualTo("guest-1");
            verify(squadxLiveClient).sendChatMessage("sess-1", "Working on the fix now", "guest-1", null);
        }

        @Test
        @DisplayName("should fetch chat history from linked squadx live session")
        void shouldFetchChatHistory() {
            activeSession.setExternalSessionId("sess-1");

            when(liveSessionRepository.findById(20L)).thenReturn(Optional.of(activeSession));
            when(orgMemberRepository.existsByOrganizationIdAndUserId(100L, 1L)).thenReturn(true);
            when(squadxLiveClient.getChatHistory("sess-1", 20, null, null)).thenReturn(List.of(
                    Map.of(
                            "id", "msg-1",
                            "content", "Hello",
                            "message_type", "text",
                            "created_at", "2026-04-29T00:00:00Z"
                    )
            ));

            List<LiveChatMessageResponse> history = liveViewService.getChatHistory(20L, 20, null, null, hostUser);

            assertThat(history).hasSize(1);
            assertThat(history.get(0).getContent()).isEqualTo("Hello");
        }
    }
}
