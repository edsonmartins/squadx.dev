package dev.squadx.service;

import dev.squadx.event.ExecutionCompletedEvent;
import dev.squadx.integration.SquadxLiveClient;
import dev.squadx.model.LiveSession;
import dev.squadx.model.enums.ExecutionStatus;
import dev.squadx.repository.LiveSessionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.event.TransactionalEventListener;

@Service
@RequiredArgsConstructor
@Slf4j
public class LiveAgentCommunicationBridge {

    private final LiveSessionRepository liveSessionRepository;
    private final SquadxLiveClient squadxLiveClient;

    @TransactionalEventListener
    public void onExecutionCompleted(ExecutionCompletedEvent event) {
        if (event.status() != ExecutionStatus.COMPLETED && event.status() != ExecutionStatus.FAILED) {
            return;
        }

        liveSessionRepository.findActiveByTaskId(event.taskId()).ifPresent(this::sendExecutionUpdate);
    }

    private void sendExecutionUpdate(LiveSession session) {
        if (session.getExternalSessionId() == null || session.getExternalAgentParticipantId() == null) {
            return;
        }

        Long executionId = session.getExecution() != null ? session.getExecution().getId() : null;
        String status = session.getExecution() != null && session.getExecution().getStatus() != null
                ? session.getExecution().getStatus().name()
                : "UPDATED";

        String message = executionId != null
                ? "Execution #" + executionId + " is now " + status + "."
                : "Task execution updated: " + status + ".";

        try {
            squadxLiveClient.sendChatMessage(
                    session.getExternalSessionId(),
                    message,
                    session.getExternalAgentParticipantId(),
                    null
            );
        } catch (Exception e) {
            log.warn("Failed to bridge execution update into SquadX Live session {}: {}", session.getId(), e.getMessage());
        }
    }
}
