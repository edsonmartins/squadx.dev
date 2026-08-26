package dev.squadx.controlpanel.event;

import lombok.RequiredArgsConstructor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.Map;

/**
 * Notifica a UI do Control Panel quando o estado de uma tarefa é reprojetado. Espelha o padrão de
 * {@code WebSocketEventService}: roda após o commit da transação.
 */
@Component
@RequiredArgsConstructor
public class SpecTaskEventListener {

    private final SimpMessagingTemplate messagingTemplate;

    @TransactionalEventListener
    public void onProjected(SpecTaskProjectedEvent event) {
        messagingTemplate.convertAndSend(
                "/topic/control-panel/projects/" + event.projectId() + "/tasks",
                Map.of(
                        "type", "spec_task_updated",
                        "spec_task_id", event.specTaskId(),
                        "change_id", event.changeId(),
                        "status", event.status().name()
                ));
    }
}

