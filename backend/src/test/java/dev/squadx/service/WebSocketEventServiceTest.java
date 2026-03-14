package dev.squadx.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class WebSocketEventServiceTest {

    @Mock
    private SimpMessagingTemplate messagingTemplate;

    @InjectMocks
    private WebSocketEventService webSocketEventService;

    @Nested
    @DisplayName("sendTaskCreated()")
    class SendTaskCreated {

        @Test
        @DisplayName("should broadcast task_created event to project topic")
        @SuppressWarnings("unchecked")
        void shouldBroadcastTaskCreated() {
            Object taskData = Map.of("id", 1L, "title", "My Task");

            webSocketEventService.sendTaskCreated(10L, taskData);

            ArgumentCaptor<Map<String, Object>> captor = ArgumentCaptor.forClass(Map.class);
            verify(messagingTemplate).convertAndSend(
                    eq("/topic/projects/10/tasks"),
                    captor.capture()
            );

            Map<String, Object> payload = captor.getValue();
            assertThat(payload.get("type")).isEqualTo("task_created");
            assertThat(payload.get("task")).isEqualTo(taskData);
        }
    }

    @Nested
    @DisplayName("sendExecutionLog()")
    class SendExecutionLog {

        @Test
        @DisplayName("should broadcast execution log to execution topic")
        @SuppressWarnings("unchecked")
        void shouldBroadcastExecutionLog() {
            webSocketEventService.sendExecutionLog(42L, "INFO", "Build started");

            ArgumentCaptor<Map<String, Object>> captor = ArgumentCaptor.forClass(Map.class);
            verify(messagingTemplate).convertAndSend(
                    eq("/topic/executions/42/logs"),
                    captor.capture()
            );

            Map<String, Object> payload = captor.getValue();
            assertThat(payload.get("type")).isEqualTo("execution_log");
            assertThat(payload.get("executionId")).isEqualTo(42L);
            assertThat(payload.get("level")).isEqualTo("INFO");
            assertThat(payload.get("message")).isEqualTo("Build started");
            assertThat(payload).containsKey("timestamp");
        }
    }

    @Nested
    @DisplayName("sendLiveSessionStarted()")
    class SendLiveSessionStarted {

        @Test
        @DisplayName("should broadcast session_started event to live topic")
        @SuppressWarnings("unchecked")
        void shouldBroadcastSessionStarted() {
            webSocketEventService.sendLiveSessionStarted("CODE1234", 20L);

            ArgumentCaptor<Map<String, Object>> captor = ArgumentCaptor.forClass(Map.class);
            verify(messagingTemplate).convertAndSend(
                    eq("/topic/live/CODE1234"),
                    captor.capture()
            );

            Map<String, Object> payload = captor.getValue();
            assertThat(payload.get("type")).isEqualTo("session_started");
            assertThat(payload.get("sessionId")).isEqualTo(20L);
            assertThat(payload.get("code")).isEqualTo("CODE1234");
        }
    }

    @Nested
    @DisplayName("sendParticipantJoined()")
    class SendParticipantJoined {

        @Test
        @DisplayName("should broadcast participant joined event with viewer count")
        @SuppressWarnings("unchecked")
        void shouldBroadcastParticipantJoined() {
            webSocketEventService.sendParticipantJoined("CODE1234", 5L, "John Doe", 3);

            ArgumentCaptor<Map<String, Object>> captor = ArgumentCaptor.forClass(Map.class);
            verify(messagingTemplate).convertAndSend(
                    eq("/topic/live/CODE1234/participants"),
                    captor.capture()
            );

            Map<String, Object> payload = captor.getValue();
            assertThat(payload.get("action")).isEqualTo("joined");
            assertThat(payload.get("userId")).isEqualTo(5L);
            assertThat(payload.get("userName")).isEqualTo("John Doe");
            assertThat(payload.get("currentViewers")).isEqualTo(3);
        }
    }
}
