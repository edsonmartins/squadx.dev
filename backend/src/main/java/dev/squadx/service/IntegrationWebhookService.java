package dev.squadx.service;

import dev.squadx.model.LiveSession;
import dev.squadx.model.enums.LiveSessionStatus;
import dev.squadx.repository.LiveSessionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class IntegrationWebhookService {

    private final LiveSessionRepository liveSessionRepository;
    private final RecordingService recordingService;
    private final AuditService auditService;

    public void handleBrainSentryWebhook(Map<String, Object> payload) {
        String eventType = asString(payload.getOrDefault("event", "unknown"));
        switch (eventType) {
            case "memory.flagged" -> auditService.log(
                    null,
                    "BRAINSENTRY_MEMORY_FLAGGED",
                    "BRAINSENTRY_MEMORY",
                    parseLong(payload.get("memoryId")),
                    stringify(payload),
                    null
            );
            case "knowledge.conflict" -> auditService.log(
                    null,
                    "BRAINSENTRY_KNOWLEDGE_CONFLICT",
                    "BRAINSENTRY_MEMORY",
                    null,
                    stringify(payload),
                    null
            );
            case "pattern.detected" -> auditService.log(
                    null,
                    "BRAINSENTRY_PATTERN_DETECTED",
                    "BRAINSENTRY_MEMORY",
                    null,
                    stringify(payload),
                    null
            );
            default -> log.debug("Unhandled BrainSentry webhook event: {}", eventType);
        }
    }

    @Transactional
    public void handleLiveWebhook(Map<String, Object> payload) {
        String eventType = asString(payload.getOrDefault("event", "unknown"));
        switch (eventType) {
            case "session.ended" -> handleLiveSessionEnded(payload);
            case "recording.ready" -> handleRecordingReady(payload);
            case "participant.joined" -> auditService.log(
                    null,
                    "LIVE_PARTICIPANT_JOINED",
                    "LIVE_SESSION",
                    resolveSession(payload).map(LiveSession::getId).orElse(null),
                    stringify(payload),
                    null
            );
            default -> log.debug("Unhandled Live webhook event: {}", eventType);
        }
    }

    private void handleLiveSessionEnded(Map<String, Object> payload) {
        resolveSession(payload).ifPresentOrElse(session -> {
            if (session.getStatus() != LiveSessionStatus.ENDED) {
                session.setStatus(LiveSessionStatus.ENDED);
                if (session.getEndedAt() == null) {
                    session.setEndedAt(LocalDateTime.now());
                }
                liveSessionRepository.save(session);
            }
            auditService.log(
                    null,
                    "LIVE_SESSION_ENDED",
                    "LIVE_SESSION",
                    session.getId(),
                    stringify(payload),
                    null
            );
        }, () -> log.warn("Received session.ended webhook for unknown live session: {}", payload.get("sessionId")));
    }

    private void handleRecordingReady(Map<String, Object> payload) {
        resolveSession(payload).ifPresentOrElse(session -> {
            Long fileSizeBytes = parseLong(payload.get("fileSizeBytes"));
            Integer durationSeconds = parseInteger(payload.get("durationSeconds"));

            try {
                recordingService.completeLatestRecordingForSession(session.getId(), fileSizeBytes, durationSeconds);
            } catch (Exception e) {
                log.warn("Failed to reconcile recording.ready for session {}: {}", session.getId(), e.getMessage());
            }

            auditService.log(
                    null,
                    "LIVE_RECORDING_READY",
                    "LIVE_RECORDING",
                    session.getId(),
                    stringify(payload),
                    null
            );
        }, () -> log.warn("Received recording.ready webhook for unknown live session: {}", payload.get("sessionId")));
    }

    private java.util.Optional<LiveSession> resolveSession(Map<String, Object> payload) {
        String externalSessionId = asString(payload.get("sessionId"));
        if (externalSessionId != null && !externalSessionId.isBlank()) {
            return liveSessionRepository.findByExternalSessionId(externalSessionId);
        }

        Long localSessionId = parseLong(payload.get("localSessionId"));
        if (localSessionId != null) {
            return liveSessionRepository.findById(localSessionId);
        }

        return java.util.Optional.empty();
    }

    private String asString(Object value) {
        return value != null ? String.valueOf(value) : null;
    }

    private Long parseLong(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return Long.valueOf(String.valueOf(value));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private Integer parseInteger(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return Integer.valueOf(String.valueOf(value));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private String stringify(Map<String, Object> payload) {
        return payload.toString();
    }
}
