package dev.squadx.integration;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import dev.squadx.model.LiveSession;
import dev.squadx.repository.LiveSessionRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SquadxLiveClientTest {

    private HttpServer server;

    @Mock
    private LiveSessionRepository liveSessionRepository;

    @AfterEach
    void tearDown() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    @DisplayName("healthCheck should report UP when live service is reachable")
    void healthCheckShouldReportUp() throws Exception {
        server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/api/ready", exchange -> respond(exchange, 200, "{\"status\":\"ok\"}"));
        server.start();

        SquadxLiveClient client = new SquadxLiveClient(configFor(server), jwtProviderFor(server), liveSessionRepository);

        Map<String, Object> status = client.healthCheck();

        assertThat(status.get("status")).isEqualTo("UP");
        assertThat(status.get("reachable")).isEqualTo(true);
        assertThat(status.get("enabled")).isEqualTo(true);
    }

    @Test
    @DisplayName("createSession should fail open and return null when live service errors")
    void createSessionShouldFailOpen() throws Exception {
        server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/api/integration/sessions", exchange -> respond(exchange, 500, "{\"error\":\"boom\"}"));
        server.start();

        SquadxLiveClient client = new SquadxLiveClient(configFor(server), jwtProviderFor(server), liveSessionRepository);

        Map<String, String> session = client.createSession(10L, 2L, "p2p");

        assertThat(session).isNull();
    }

    @Test
    @DisplayName("createSession should parse live session response")
    void createSessionShouldParseResponse() throws Exception {
        server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/api/integration/sessions", exchange -> respond(exchange, 200,
                "{\"sessionId\":\"sess-1\",\"joinCode\":\"ABCD1234\",\"joinUrl\":\"https://live.test/join/ABCD1234\"}"));
        server.start();

        SquadxLiveClient client = new SquadxLiveClient(configFor(server), jwtProviderFor(server), liveSessionRepository);

        Map<String, String> session = client.createSession(10L, 2L, "p2p");

        assertThat(session).isNotNull();
        assertThat(session.get("sessionId")).isEqualTo("sess-1");
        assertThat(session.get("joinCode")).isEqualTo("ABCD1234");
        assertThat(session.get("joinUrl")).contains("ABCD1234");
    }

    @Test
    @DisplayName("endSession should swallow live service failures")
    void endSessionShouldSwallowFailures() throws Exception {
        server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/api/integration/sessions/sess-1", exchange -> respond(exchange, 500, "{\"error\":\"boom\"}"));
        server.start();

        SquadxLiveClient client = new SquadxLiveClient(configFor(server), jwtProviderFor(server), liveSessionRepository);

        client.endSession("sess-1");
    }

    @Test
    @DisplayName("endSessionForTask should resolve latest external session id and end it")
    void endSessionForTaskShouldResolveStoredExternalSessionId() throws Exception {
        server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/api/integration/sessions/sess-1", exchange -> respond(exchange, 200, "{\"ok\":true}"));
        server.start();

        LiveSession session = new LiveSession();
        session.setExternalSessionId("sess-1");
        when(liveSessionRepository.findByTaskIdOrderByCreatedAtDesc(10L)).thenReturn(List.of(session));

        SquadxLiveClient client = new SquadxLiveClient(configFor(server), jwtProviderFor(server), liveSessionRepository);

        client.endSessionForTask(10L);

        verify(liveSessionRepository).findByTaskIdOrderByCreatedAtDesc(10L);
    }

    @Test
    @DisplayName("joinSession should unwrap participant data")
    void joinSessionShouldUnwrapParticipantData() throws Exception {
        server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/api/sessions/join/ABCD1234", exchange -> respond(exchange, 200,
                "{\"success\":true,\"data\":{\"id\":\"participant-1\",\"display_name\":\"Agent Dev\"}}"));
        server.start();

        SquadxLiveClient client = new SquadxLiveClient(configFor(server), jwtProviderFor(server), liveSessionRepository);

        Map<String, Object> participant = client.joinSession("ABCD1234", "Agent Dev");

        assertThat(participant.get("id")).isEqualTo("participant-1");
        assertThat(participant.get("display_name")).isEqualTo("Agent Dev");
    }

    @Test
    @DisplayName("sendChatMessage should unwrap created message data")
    void sendChatMessageShouldUnwrapCreatedMessageData() throws Exception {
        server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/api/chat/send", exchange -> respond(exchange, 201,
                "{\"success\":true,\"data\":{\"id\":\"msg-1\",\"content\":\"Hello team\",\"session_id\":\"sess-1\"}}"));
        server.start();

        SquadxLiveClient client = new SquadxLiveClient(configFor(server), jwtProviderFor(server), liveSessionRepository);

        Map<String, Object> message = client.sendChatMessage("sess-1", "Hello team", "participant-1", null);

        assertThat(message.get("id")).isEqualTo("msg-1");
        assertThat(message.get("content")).isEqualTo("Hello team");
    }

    @Test
    @DisplayName("getChatHistory should unwrap message list")
    void getChatHistoryShouldUnwrapMessageList() throws Exception {
        server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/api/chat/history", exchange -> respond(exchange, 200,
                "{\"success\":true,\"data\":{\"messages\":[{\"id\":\"msg-1\",\"content\":\"First\"},{\"id\":\"msg-2\",\"content\":\"Second\"}]}}"));
        server.start();

        SquadxLiveClient client = new SquadxLiveClient(configFor(server), jwtProviderFor(server), liveSessionRepository);

        List<Map<String, Object>> messages = client.getChatHistory("sess-1", 50, null, null);

        assertThat(messages).hasSize(2);
        assertThat(messages.get(0).get("id")).isEqualTo("msg-1");
    }

    private IntegrationConfig configFor(HttpServer httpServer) {
        IntegrationConfig config = new IntegrationConfig();
        config.setServiceSecret("12345678901234567890123456789012");
        config.getLive().setEnabled(true);
        config.getLive().setUrl("http://127.0.0.1:" + httpServer.getAddress().getPort());
        return config;
    }

    private ServiceJwtProvider jwtProviderFor(HttpServer httpServer) {
        return new ServiceJwtProvider(configFor(httpServer));
    }

    private void respond(HttpExchange exchange, int status, String body) throws IOException {
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream outputStream = exchange.getResponseBody()) {
            outputStream.write(bytes);
        }
    }
}
