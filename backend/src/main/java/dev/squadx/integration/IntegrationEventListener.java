package dev.squadx.integration;

import dev.squadx.event.ExecutionCompletedEvent;
import dev.squadx.event.TaskCreatedEvent;
import dev.squadx.event.TaskStatusChangedEvent;
import dev.squadx.model.enums.ExecutionStatus;
import dev.squadx.model.enums.TaskStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

/**
 * Listens to domain events and dispatches integration calls
 * to BrainSentry and SquadX Live services.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class IntegrationEventListener {

    private final BrainSentryClient brainSentryClient;
    private final SquadxLiveClient squadxLiveClient;

    @EventListener
    @Async
    public void onExecutionCompleted(ExecutionCompletedEvent event) {
        if (event.status() == ExecutionStatus.COMPLETED || event.status() == ExecutionStatus.FAILED) {
            brainSentryClient.notifyExecutionCompleted(
                    event.executionId(),
                    event.status().name(),
                    "Execution " + event.executionId() + " " + event.status().name().toLowerCase()
            );
        }
    }

    @EventListener
    @Async
    public void onTaskCompleted(TaskStatusChangedEvent event) {
        if (event.newStatus() == TaskStatus.DONE) {
            // End the live session when task is done
            squadxLiveClient.endSessionForTask(event.taskId());
        }
    }
}
