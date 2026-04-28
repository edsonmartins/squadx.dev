package dev.squadx.integration;

import dev.squadx.event.TaskStatusChangedEvent;
import dev.squadx.model.enums.TaskStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Listens to domain events and dispatches integration calls
 * to BrainSentry and SquadX Live services.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class IntegrationEventListener {

    private final SquadxLiveClient squadxLiveClient;

    @TransactionalEventListener
    @Async
    public void onTaskCompleted(TaskStatusChangedEvent event) {
        if (event.newStatus() == TaskStatus.DONE) {
            // End the live session when task is done
            squadxLiveClient.endSessionForTask(event.taskId());
        }
    }
}
