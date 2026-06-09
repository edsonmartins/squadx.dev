package dev.squadx.service;

import dev.squadx.model.LiveSession;
import dev.squadx.model.Agent;
import dev.squadx.model.Squad;
import dev.squadx.model.Organization;
import dev.squadx.model.enums.AgentType;
import dev.squadx.model.enums.LiveSessionStatus;
import dev.squadx.integration.SquadxLiveClient;
import dev.squadx.repository.LiveSessionRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class IntegrationWebhookServiceTest {

    @Mock
    private LiveSessionRepository liveSessionRepository;

    @Mock
    private RecordingService recordingService;

    @Mock
    private AuditService auditService;

    @Mock
    private WebSocketEventService webSocketEventService;

    @Mock
    private DirectAgentChatService directAgentChatService;

    @Mock
    private SquadxLiveClient squadxLiveClient;

    @InjectMocks
    private IntegrationWebhookService integrationWebhookService;

    @Test
    @DisplayName("should end local live session when session.ended webhook arrives")
    void shouldReconcileEndedLiveSession() {
        LiveSession session = new LiveSession();
        session.setId(10L);
        session.setExternalSessionId("sess-1");
        session.setStatus(LiveSessionStatus.ACTIVE);

        when(liveSessionRepository.findByExternalSessionId("sess-1")).thenReturn(Optional.of(session));
        when(liveSessionRepository.save(any(LiveSession.class))).thenAnswer(inv -> inv.getArgument(0));

        integrationWebhookService.handleLiveWebhook(Map.of(
                "event", "session.ended",
                "sessionId", "sess-1"
        ));

        verify(liveSessionRepository).save(session);
        verify(auditService).log(isNull(), eq("LIVE_SESSION_ENDED"), eq("LIVE_SESSION"), eq(10L), anyString(), isNull());
    }

    @Test
    @DisplayName("should complete latest recording when recording.ready webhook arrives")
    void shouldCompleteLatestRecordingForReadyWebhook() {
        LiveSession session = new LiveSession();
        session.setId(10L);
        session.setExternalSessionId("sess-1");

        when(liveSessionRepository.findByExternalSessionId("sess-1")).thenReturn(Optional.of(session));

        integrationWebhookService.handleLiveWebhook(Map.of(
                "event", "recording.ready",
                "sessionId", "sess-1",
                "fileSizeBytes", 5000,
                "durationSeconds", 120
        ));

        verify(recordingService).completeLatestRecordingForSession(10L, 5000L, 120);
        verify(auditService).log(isNull(), eq("LIVE_RECORDING_READY"), eq("LIVE_RECORDING"), eq(10L), anyString(), isNull());
    }

    @Test
    @DisplayName("should broadcast external participant join when participant.joined webhook arrives")
    void shouldBroadcastParticipantJoined() {
        LiveSession session = new LiveSession();
        session.setId(10L);
        session.setCode("JOIN1234");
        session.setExternalSessionId("sess-1");

        when(liveSessionRepository.findByExternalSessionId("sess-1")).thenReturn(Optional.of(session));

        integrationWebhookService.handleLiveWebhook(Map.of(
                "event", "participant.joined",
                "sessionId", "sess-1",
                "participantId", "guest-1",
                "displayName", "Human Reviewer",
                "role", "viewer"
        ));

        verify(auditService).log(isNull(), eq("LIVE_PARTICIPANT_JOINED"), eq("LIVE_SESSION"), eq(10L), anyString(), isNull());
        verify(webSocketEventService).sendExternalParticipantJoined("JOIN1234", "guest-1", "Human Reviewer", "viewer");
    }

    @Test
    @DisplayName("should auto-reply when message.created arrives for direct agent session")
    void shouldAutoReplyToDirectAgentMessage() {
        Organization org = Organization.builder().name("Org").slug("org").build();
        Squad squad = Squad.builder().name("Alpha").organization(org).build();
        Agent agent = Agent.builder().name("Builder").agentType(AgentType.BACKEND).squad(squad).build();

        LiveSession session = new LiveSession();
        session.setId(10L);
        session.setCode("JOIN1234");
        session.setAgent(agent);
        session.setExternalSessionId("sess-1");
        session.setExternalAgentParticipantId("agent-1");
        session.setStatus(LiveSessionStatus.ACTIVE);

        when(liveSessionRepository.findByExternalSessionId("sess-1")).thenReturn(Optional.of(session));
        when(directAgentChatService.generateReply(session, "Can you help?", "Edson"))
                .thenReturn(Optional.of("Yes, working on it."));

        integrationWebhookService.handleLiveWebhook(Map.of(
                "event", "message.created",
                "sessionId", "sess-1",
                "participantId", "human-1",
                "displayName", "Edson",
                "messageType", "text",
                "content", "Can you help?"
        ));

        verify(squadxLiveClient).sendChatMessage("sess-1", "Yes, working on it.", "agent-1", null);
        verify(auditService).log(isNull(), eq("LIVE_AGENT_AUTO_REPLIED"), eq("LIVE_SESSION"), eq(10L), anyString(), isNull());
    }

    @Test
    @DisplayName("should ignore message.created sent by agent participant itself")
    void shouldIgnoreAgentOwnMessage() {
        Organization org = Organization.builder().name("Org").slug("org").build();
        Squad squad = Squad.builder().name("Alpha").organization(org).build();
        Agent agent = Agent.builder().name("Builder").agentType(AgentType.BACKEND).squad(squad).build();

        LiveSession session = new LiveSession();
        session.setId(10L);
        session.setAgent(agent);
        session.setExternalSessionId("sess-1");
        session.setExternalAgentParticipantId("agent-1");
        session.setStatus(LiveSessionStatus.ACTIVE);

        when(liveSessionRepository.findByExternalSessionId("sess-1")).thenReturn(Optional.of(session));

        integrationWebhookService.handleLiveWebhook(Map.of(
                "event", "message.created",
                "sessionId", "sess-1",
                "participantId", "agent-1",
                "messageType", "text",
                "content", "Loop?"
        ));

        verify(directAgentChatService, never()).generateReply(any(), anyString(), any());
        verify(squadxLiveClient, never()).sendChatMessage(anyString(), anyString(), anyString(), any());
    }

    @Test
    @DisplayName("should audit BrainSentry pattern webhook")
    void shouldAuditBrainSentryPattern() {
        integrationWebhookService.handleBrainSentryWebhook(Map.of(
                "event", "pattern.detected",
                "pattern", "controller-test-harness"
        ));

        verify(auditService).log(isNull(), eq("BRAINSENTRY_PATTERN_DETECTED"), eq("BRAINSENTRY_MEMORY"), isNull(), anyString(), isNull());
    }
}
