package dev.squadx.integration;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class LinktorClientTest {

    private HttpServer server;

    @AfterEach
    void tearDown() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    @DisplayName("healthCheck should report UP when linktor is reachable and credentials work")
    void healthCheckShouldReportUp() throws Exception {
        server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/health", exchange -> respond(exchange, 200, "{\"status\":\"ok\"}"));
        server.createContext("/api/v1/auth/login", exchange -> respond(exchange, 200,
                "{\"success\":true,\"data\":{\"access_token\":\"token-1\"}}"));
        server.start();

        LinktorClient client = new LinktorClient(configFor(server));

        Map<String, Object> status = client.healthCheck();

        assertThat(status.get("status")).isEqualTo("UP");
        assertThat(status.get("reachable")).isEqualTo(true);
        assertThat(status.get("authenticated")).isEqualTo(true);
    }

    @Test
    @DisplayName("sendOperationalMessage should use configured conversation id")
    void sendOperationalMessageShouldUseConfiguredConversationId() throws Exception {
        AtomicInteger sends = new AtomicInteger();

        server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/api/v1/auth/login", exchange -> respond(exchange, 200,
                "{\"success\":true,\"data\":{\"access_token\":\"token-1\"}}"));
        server.createContext("/api/v1/conversations/conv-1/messages", exchange -> {
            sends.incrementAndGet();
            respond(exchange, 201, "{\"success\":true,\"data\":{\"id\":\"msg-1\"}}");
        });
        server.start();

        IntegrationConfig config = configFor(server);
        config.getLinktor().setDefaultConversationId("conv-1");
        LinktorClient client = new LinktorClient(config);

        boolean delivered = client.sendOperationalMessage(10L, "Execution update", "Execution completed", Map.of("event", "execution_completed"));

        assertThat(delivered).isTrue();
        assertThat(sends.get()).isEqualTo(1);
    }

    @Test
    @DisplayName("sendOperationalMessage should auto-create conversation when configured")
    void sendOperationalMessageShouldAutoCreateConversation() throws Exception {
        AtomicInteger creates = new AtomicInteger();
        AtomicInteger sends = new AtomicInteger();

        server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/api/v1/auth/login", exchange -> respond(exchange, 200,
                "{\"success\":true,\"data\":{\"access_token\":\"token-1\"}}"));
        server.createContext("/api/v1/conversations", exchange -> {
            creates.incrementAndGet();
            respond(exchange, 201, "{\"success\":true,\"data\":{\"id\":\"conv-auto\"}}");
        });
        server.createContext("/api/v1/conversations/conv-auto/messages", exchange -> {
            sends.incrementAndGet();
            respond(exchange, 201, "{\"success\":true,\"data\":{\"id\":\"msg-1\"}}");
        });
        server.start();

        IntegrationConfig config = configFor(server);
        config.getLinktor().setAutoCreateConversation(true);
        config.getLinktor().setChannelId("channel-1");
        config.getLinktor().setContactId("contact-1");
        LinktorClient client = new LinktorClient(config);

        boolean delivered = client.sendOperationalMessage(22L, "Task update", "Task moved to review", Map.of("event", "task_status_changed"));

        assertThat(delivered).isTrue();
        assertThat(creates.get()).isEqualTo(1);
        assertThat(sends.get()).isEqualTo(1);
    }

    private IntegrationConfig configFor(HttpServer httpServer) {
        IntegrationConfig config = new IntegrationConfig();
        config.getLinktor().setEnabled(true);
        config.getLinktor().setUrl("http://127.0.0.1:" + httpServer.getAddress().getPort());
        config.getLinktor().setEmail("admin@demo.com");
        config.getLinktor().setPassword("admin123");
        return config;
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
