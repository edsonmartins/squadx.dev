package dev.squadx.controlpanel.service;

import dev.squadx.controlpanel.event.SpecTaskMergedEvent;
import dev.squadx.controlpanel.model.enums.EventSource;
import dev.squadx.controlpanel.model.enums.TaskEventType;
import dev.squadx.controlpanel.repository.SpecTaskRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Ingestão de webhooks de Git (GitHub-shaped) como eventos de tarefa (RFC-0003, execution-tracking
 * R1). Associa refs a tarefas pela convenção {@code spec-task-<id>}. Verifica HMAC SHA-256.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class GitWebhookService {

    private final SpecEventService specEventService;
    private final SpecTaskRepository specTaskRepository;
    private final ApplicationEventPublisher eventPublisher;

    @Value("${squadx.git.webhook-secret:}")
    private String webhookSecret;

    @Value("${squadx.git.task-ref-pattern:spec-task-(\\d+)}")
    private String taskRefPattern;

    /** Verifica a assinatura {@code X-Hub-Signature-256: sha256=<hex>} sobre o corpo cru. */
    public boolean verifySignature(String signatureHeader, byte[] rawBody) {
        if (webhookSecret == null || webhookSecret.isBlank() || signatureHeader == null) {
            return false;
        }
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(webhookSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            String expected = "sha256=" + HexFormat.of().formatHex(mac.doFinal(rawBody));
            return MessageDigest.isEqual(
                    expected.getBytes(StandardCharsets.UTF_8),
                    signatureHeader.getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            log.warn("Failed to verify Git webhook signature", e);
            return false;
        }
    }

    @Transactional
    public void handle(String eventType, Map<String, Object> payload) {
        if (eventType == null) {
            return;
        }
        switch (eventType) {
            case "push" -> handlePush(payload);
            case "pull_request" -> handlePullRequest(payload);
            default -> log.debug("Unhandled Git webhook event: {}", eventType);
        }
    }

    @SuppressWarnings("unchecked")
    private void handlePush(Map<String, Object> payload) {
        String ref = asString(payload.get("ref"));            // refs/heads/spec-task-42
        String after = asString(payload.get("after"));        // commit sha
        extractTaskId(ref).ifPresent(taskId ->
                recordIfExists(taskId, TaskEventType.STARTED, after != null ? after : ref, null));
    }

    @SuppressWarnings("unchecked")
    private void handlePullRequest(Map<String, Object> payload) {
        String action = asString(payload.get("action"));
        Map<String, Object> pr = (Map<String, Object>) payload.get("pull_request");
        if (pr == null) {
            return;
        }
        Map<String, Object> head = (Map<String, Object>) pr.get("head");
        String headRef = head != null ? asString(head.get("ref")) : null;
        String headSha = head != null ? asString(head.get("sha")) : null;
        String number = asString(pr.get("number"));
        String title = asString(pr.get("title"));

        Optional<Long> taskId = extractTaskId(headRef);
        if (taskId.isEmpty()) {
            taskId = extractTaskId(title);
        }
        if (taskId.isEmpty()) {
            return;
        }

        if ("opened".equals(action) || "reopened".equals(action)) {
            recordIfExists(taskId.get(), TaskEventType.PR_OPENED, "pr-" + number, null);
        } else if ("closed".equals(action) && Boolean.TRUE.equals(pr.get("merged"))) {
            // Gatilho do Pass 5 (consumidor real em pass5-validation).
            if (specTaskRepository.existsById(taskId.get())) {
                eventPublisher.publishEvent(new SpecTaskMergedEvent(taskId.get(), "pr-" + number, headSha, number));
            }
        }
    }

    private void recordIfExists(Long taskId, TaskEventType type, String sourceRef, String payload) {
        if (specTaskRepository.existsById(taskId)) {
            specEventService.record(taskId, type, EventSource.GIT, sourceRef, payload, Instant.now());
        } else {
            log.debug("Git webhook references unknown spec task {}", taskId);
        }
    }

    private Optional<Long> extractTaskId(String text) {
        if (text == null) {
            return Optional.empty();
        }
        Matcher m = Pattern.compile(taskRefPattern).matcher(text);
        if (m.find()) {
            try {
                return Optional.of(Long.parseLong(m.group(1)));
            } catch (NumberFormatException ignored) {
                return Optional.empty();
            }
        }
        return Optional.empty();
    }

    private String asString(Object value) {
        return value != null ? String.valueOf(value) : null;
    }
}
