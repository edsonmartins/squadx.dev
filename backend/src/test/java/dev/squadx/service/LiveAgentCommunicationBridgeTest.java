package dev.squadx.service;

import dev.squadx.event.ExecutionCompletedEvent;
import dev.squadx.integration.SquadxLiveClient;
import dev.squadx.model.Execution;
import dev.squadx.model.LiveSession;
import dev.squadx.model.enums.ExecutionStatus;
import dev.squadx.repository.LiveSessionRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;
import java.util.Optional;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LiveAgentCommunicationBridgeTest {

    @Mock
    private LiveSessionRepository liveSessionRepository;

    @Mock
    private SquadxLiveClient squadxLiveClient;

    @Test
    @DisplayName("should bridge completed execution into linked live chat")
    void shouldBridgeCompletedExecutionIntoLiveChat() {
        Execution execution = new Execution();
        execution.setId(42L);
        execution.setStatus(ExecutionStatus.COMPLETED);

        LiveSession session = new LiveSession();
        session.setId(10L);
        session.setExternalSessionId("sess-1");
        session.setExternalAgentParticipantId("guest-1");
        session.setExecution(execution);

        when(liveSessionRepository.findActiveByTaskId(11L)).thenReturn(Optional.of(session));
        when(squadxLiveClient.sendChatMessage("sess-1", "Execution #42 is now COMPLETED.", "guest-1", null))
                .thenReturn(Map.of("id", "msg-1"));

        LiveAgentCommunicationBridge bridge = new LiveAgentCommunicationBridge(liveSessionRepository, squadxLiveClient);
        bridge.onExecutionCompleted(new ExecutionCompletedEvent(42L, 11L, 3L, 7L, ExecutionStatus.COMPLETED));

        verify(squadxLiveClient).sendChatMessage("sess-1", "Execution #42 is now COMPLETED.", "guest-1", null);
    }

    @Test
    @DisplayName("should ignore executions without active linked live session")
    void shouldIgnoreExecutionsWithoutLinkedLiveSession() {
        when(liveSessionRepository.findActiveByTaskId(11L)).thenReturn(Optional.empty());

        LiveAgentCommunicationBridge bridge = new LiveAgentCommunicationBridge(liveSessionRepository, squadxLiveClient);
        bridge.onExecutionCompleted(new ExecutionCompletedEvent(42L, 11L, 3L, 7L, ExecutionStatus.FAILED));

        verifyNoInteractions(squadxLiveClient);
    }
}
