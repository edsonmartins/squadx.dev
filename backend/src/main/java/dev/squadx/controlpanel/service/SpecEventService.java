package dev.squadx.controlpanel.service;

import dev.squadx.controlpanel.event.SpecTaskProjectedEvent;
import dev.squadx.controlpanel.model.SpecEvent;
import dev.squadx.controlpanel.model.SpecTask;
import dev.squadx.controlpanel.model.enums.EventSource;
import dev.squadx.controlpanel.model.enums.TaskEventType;
import dev.squadx.controlpanel.repository.SpecEventRepository;
import dev.squadx.controlpanel.repository.SpecTaskRepository;
import dev.squadx.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;

/**
 * Ingestão append-only de eventos e reprojeção do estado das tarefas (ADR-0002, RFC-0003).
 * Único caminho de escrita do {@code status} das {@link SpecTask}.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class SpecEventService {

    private final SpecEventRepository specEventRepository;
    private final SpecTaskRepository specTaskRepository;
    private final SpecTaskProjector projector;
    private final ApplicationEventPublisher eventPublisher;

    /**
     * Registra um evento (idempotente por {@code dedup_key}) e reprojeta o estado da tarefa.
     * {@code sourceRef} deve ser estável para fontes que reentregam (Git: id/sha) e único para
     * ações distintas (MCP/Pass 5: um UUID por ação).
     */
    @Transactional
    public void record(Long specTaskId, TaskEventType type, EventSource source,
                        String sourceRef, String payload, Instant occurredAt) {
        String dedupKey = dedupKey(specTaskId, type, source, sourceRef);
        if (specEventRepository.existsByDedupKey(dedupKey)) {
            log.debug("Duplicate spec event ignored: {}", dedupKey);
            return; // idempotente (R4)
        }
        SpecTask task = specTaskRepository.findById(specTaskId)
                .orElseThrow(() -> new ResourceNotFoundException("Task not found"));

        specEventRepository.save(SpecEvent.builder()
                .specTask(task)
                .type(type)
                .source(source)
                .sourceRef(sourceRef)
                .payload(payload)
                .dedupKey(dedupKey)
                .occurredAt(occurredAt != null ? occurredAt : Instant.now())
                .receivedAt(Instant.now())
                .build());

        reproject(task);
    }

    /** Recalcula o estado materializado a partir de todos os eventos da tarefa (reprocessável). */
    @Transactional
    public void reproject(SpecTask task) {
        List<SpecEvent> events =
                specEventRepository.findBySpecTaskIdOrderByOccurredAtAscIdAsc(task.getId());
        SpecTaskProjector.ProjectedState state = projector.project(events);

        task.setStatus(state.status());
        task.setBlockerReason(state.blockerReason());
        task.setReviseReason(state.reviseReason());
        task.setPass5(state.pass5());
        specTaskRepository.save(task);

        eventPublisher.publishEvent(new SpecTaskProjectedEvent(
                task.getId(),
                task.getChange().getId(),
                task.getChange().getProject().getId(),
                state.status()));
    }

    private String dedupKey(Long taskId, TaskEventType type, EventSource source, String sourceRef) {
        String raw = source + "|" + (sourceRef == null ? "" : sourceRef) + "|" + type + "|" + taskId;
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(raw.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }
}
