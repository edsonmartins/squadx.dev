package dev.squadx.controlpanel.service;

import dev.squadx.controlpanel.event.SpecTaskMergedEvent;
import dev.squadx.controlpanel.model.enums.EventSource;
import dev.squadx.controlpanel.model.enums.TaskEventType;
import dev.squadx.controlpanel.repository.SpecTaskRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class GitWebhookServiceTest {

    @Mock private SpecEventService specEventService;
    @Mock private SpecTaskRepository specTaskRepository;
    @Mock private ApplicationEventPublisher eventPublisher;

    @InjectMocks private GitWebhookService service;

    private static final String SECRET = "test-secret-0123456789";

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(service, "webhookSecret", SECRET);
        ReflectionTestUtils.setField(service, "taskRefPattern", "spec-task-(\\d+)");
    }

    private String sign(byte[] body) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        return "sha256=" + HexFormat.of().formatHex(mac.doFinal(body));
    }

    @Test
    void verifiesValidSignature() throws Exception {
        byte[] body = "{\"a\":1}".getBytes(StandardCharsets.UTF_8);
        assertThat(service.verifySignature(sign(body), body)).isTrue();
    }

    @Test
    void rejectsInvalidSignature() {
        byte[] body = "{\"a\":1}".getBytes(StandardCharsets.UTF_8);
        assertThat(service.verifySignature("sha256=deadbeef", body)).isFalse();
        assertThat(service.verifySignature(null, body)).isFalse();
    }

    @Test
    void pushRecordsStarted() {  // R1
        when(specTaskRepository.existsById(42L)).thenReturn(true);
        service.handle("push", Map.of("ref", "refs/heads/spec-task-42", "after", "abc123"));
        verify(specEventService).record(eq(42L), eq(TaskEventType.STARTED), eq(EventSource.GIT),
                eq("abc123"), isNull(), any());
    }

    @Test
    void pullRequestOpenedRecordsPrOpened() {  // R1
        when(specTaskRepository.existsById(7L)).thenReturn(true);
        Map<String, Object> payload = Map.of(
                "action", "opened",
                "pull_request", Map.of(
                        "number", 3,
                        "title", "feat",
                        "head", Map.of("ref", "spec-task-7", "sha", "sha7")));
        service.handle("pull_request", payload);
        verify(specEventService).record(eq(7L), eq(TaskEventType.PR_OPENED), eq(EventSource.GIT),
                eq("pr-3"), isNull(), any());
    }

    @Test
    void mergedPullRequestPublishesTrigger() {  // R1 (gatilho Pass 5)
        when(specTaskRepository.existsById(7L)).thenReturn(true);
        Map<String, Object> payload = Map.of(
                "action", "closed",
                "pull_request", Map.of(
                        "number", 3,
                        "merged", true,
                        "head", Map.of("ref", "spec-task-7", "sha", "sha7")));
        service.handle("pull_request", payload);
        verify(eventPublisher).publishEvent(any(SpecTaskMergedEvent.class));
        verify(specEventService, never()).record(anyLong(), any(), any(), any(), any(), any());
    }

    @Test
    void ignoresUnknownTask() {
        when(specTaskRepository.existsById(99L)).thenReturn(false);
        service.handle("push", Map.of("ref", "refs/heads/spec-task-99", "after", "x"));
        verify(specEventService, never()).record(anyLong(), any(), any(), any(), any(), any());
    }
}
