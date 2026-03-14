package dev.squadx.service;

import dev.squadx.dto.liveview.JoinSessionRequest;
import dev.squadx.dto.liveview.LiveSessionRequest;
import dev.squadx.dto.liveview.LiveSessionResponse;
import dev.squadx.exception.BadRequestException;
import dev.squadx.exception.ForbiddenException;
import dev.squadx.exception.ResourceNotFoundException;
import dev.squadx.model.*;
import dev.squadx.model.enums.LiveSessionStatus;
import dev.squadx.model.enums.UserRole;
import dev.squadx.repository.LiveSessionParticipantRepository;
import dev.squadx.repository.LiveSessionRepository;
import dev.squadx.repository.OrganizationMemberRepository;
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
    private OrganizationMemberRepository orgMemberRepository;

    @Mock
    private WebSocketEventService webSocketEventService;

    @InjectMocks
    private LiveViewService liveViewService;

    private User hostUser;
    private User viewerUser;
    private Organization organization;
    private Project project;
    private Task task;
    private LiveSession activeSession;

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
    @DisplayName("endSession()")
    class EndSession {

        @Test
        @DisplayName("should end session and mark all participants as left")
        void shouldEndSession() {
            LiveSessionParticipant participant = new LiveSessionParticipant();
            participant.setSession(activeSession);
            participant.setUser(viewerUser);
            participant.setIsHost(false);

            when(liveSessionRepository.findById(20L)).thenReturn(Optional.of(activeSession));
            when(liveSessionRepository.save(any(LiveSession.class))).thenAnswer(inv -> inv.getArgument(0));
            when(participantRepository.findBySessionIdAndLeftAtIsNull(20L))
                    .thenReturn(List.of(participant));
            when(participantRepository.save(any(LiveSessionParticipant.class)))
                    .thenAnswer(inv -> inv.getArgument(0));

            LiveSessionResponse response = liveViewService.endSession(20L, hostUser);

            assertThat(response.getStatus()).isEqualTo(LiveSessionStatus.ENDED);
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
        @DisplayName("should throw BadRequestException when session is not pending")
        void shouldThrowWhenNotPending() {
            activeSession.setStatus(LiveSessionStatus.ACTIVE);

            when(liveSessionRepository.findById(20L)).thenReturn(Optional.of(activeSession));

            assertThatThrownBy(() -> liveViewService.startSession(20L, hostUser))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessage("Session is not in pending status");
        }
    }
}
