package dev.squadx.controlpanel.event;

import dev.squadx.controlpanel.service.Pass5Service;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Dispara o Pass 5 quando o PR de uma tarefa é mergeado (RFC-0003/RFC-0004). Roda após o commit
 * da transação que ingeriu o webhook de merge.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class Pass5TriggerListener {

    private final Pass5Service pass5Service;

    @TransactionalEventListener
    public void onMerged(SpecTaskMergedEvent event) {
        log.info("Triggering Pass 5 for spec task {} (sha {})", event.specTaskId(), event.headSha());
        pass5Service.validate(event.specTaskId(), event.headSha());
    }
}

