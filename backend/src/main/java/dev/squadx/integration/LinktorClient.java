package dev.squadx.integration;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
@Slf4j
public class LinktorClient {

    private final RestClient restClient;
    private final IntegrationConfig config;
    private final Map<Long, String> conversationCache = new ConcurrentHashMap<>();
    private volatile String accessToken;

    public LinktorClient(IntegrationConfig config) {
        this.config = config;

        if (config.getLinktor().isEnabled()) {
            this.restClient = RestClient.builder()
                    .baseUrl(config.getLinktor().getUrl())
                    .build();
            log.info("Linktor client initialized: url={}", config.getLinktor().getUrl());
        } else {
            this.restClient = null;
            log.info("Linktor client disabled");
        }
    }

    public boolean isEnabled() {
        return restClient != null && config.getLinktor().isEnabled();
    }

    public Map<String, Object> healthCheck() {
        Map<String, Object> status = new LinkedHashMap<>();
        IntegrationConfig.LinktorConfig linktor = config.getLinktor();

        status.put("enabled", linktor.isEnabled());
        status.put("configured", isConfigured(linktor));
        status.put("url", linktor.getUrl());
        status.put("defaultConversationId", linktor.getDefaultConversationId());
        status.put("autoCreateConversation", linktor.isAutoCreateConversation());
        status.put("channelConfigured", hasText(linktor.getChannelId()));
        status.put("contactConfigured", hasText(linktor.getContactId()));

        if (!isEnabled()) {
            status.put("reachable", false);
            status.put("authenticated", false);
            status.put("status", "DISABLED");
            return status;
        }

        try {
            restClient.get()
                    .uri("/health")
                    .retrieve()
                    .toBodilessEntity();
            status.put("reachable", true);

            String token = authenticate();
            status.put("authenticated", hasText(token));
            status.put("status", hasText(token) ? "UP" : "DEGRADED");
        } catch (Exception e) {
            status.put("reachable", false);
            status.put("authenticated", false);
            status.put("status", "DOWN");
            status.put("error", e.getMessage());
        }

        return status;
    }

    public boolean sendOperationalMessage(Long organizationId, String title, String message, Map<String, Object> metadata) {
        if (!isEnabled()) {
            return false;
        }

        try {
            String token = authenticate();
            if (!hasText(token)) {
                log.warn("Linktor authentication unavailable, skipping message for org {}", organizationId);
                return false;
            }

            String conversationId = resolveConversationId(organizationId, token);
            if (!hasText(conversationId)) {
                log.warn("Linktor conversation unavailable, skipping message for org {}", organizationId);
                return false;
            }

            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("content_type", "text");
            payload.put("content", formatContent(title, message));
            payload.put("metadata", stringifyMetadata(metadata));

            restClient.post()
                    .uri("/api/v1/conversations/{id}/messages", conversationId)
                    .header("Authorization", "Bearer " + token)
                    .body(payload)
                    .retrieve()
                    .toBodilessEntity();

            return true;
        } catch (Exception e) {
            log.warn("Linktor message delivery failed for org {}: {}", organizationId, e.getMessage());
            return false;
        }
    }

    @SuppressWarnings("unchecked")
    private String authenticate() {
        if (hasText(accessToken)) {
            return accessToken;
        }

        IntegrationConfig.LinktorConfig linktor = config.getLinktor();
        if (!hasText(linktor.getEmail()) || !hasText(linktor.getPassword())) {
            return null;
        }

        Map<String, Object> response = restClient.post()
                .uri("/api/v1/auth/login")
                .body(Map.of(
                        "email", linktor.getEmail(),
                        "password", linktor.getPassword()
                ))
                .retrieve()
                .body(Map.class);

        Map<String, Object> data = unwrapData(response);
        String token = asString(data.get("access_token"));
        if (hasText(token)) {
            accessToken = token;
        }
        return token;
    }

    private String resolveConversationId(Long organizationId, String token) {
        IntegrationConfig.LinktorConfig linktor = config.getLinktor();
        if (hasText(linktor.getDefaultConversationId())) {
            return linktor.getDefaultConversationId();
        }

        if (organizationId != null) {
            String cached = conversationCache.get(organizationId);
            if (hasText(cached)) {
                return cached;
            }
        }

        if (!linktor.isAutoCreateConversation() || !hasText(linktor.getChannelId()) || !hasText(linktor.getContactId())) {
            return null;
        }

        String conversationId = createConversation(organizationId, token);
        if (hasText(conversationId) && organizationId != null) {
            conversationCache.put(organizationId, conversationId);
        }
        return conversationId;
    }

    @SuppressWarnings("unchecked")
    private String createConversation(Long organizationId, String token) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("contact_id", config.getLinktor().getContactId());
        payload.put("channel_id", config.getLinktor().getChannelId());
        payload.put("subject", "SquadX Org " + organizationId + " Operations");
        payload.put("priority", "high");
        payload.put("tags", List.of("squadx", "operations", "org:" + organizationId));

        Map<String, Object> response = restClient.post()
                .uri("/api/v1/conversations")
                .header("Authorization", "Bearer " + token)
                .body(payload)
                .retrieve()
                .body(Map.class);

        return asString(unwrapData(response).get("id"));
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> unwrapData(Map<String, Object> response) {
        if (response == null) {
            return Map.of();
        }
        Object data = response.get("data");
        if (data instanceof Map<?, ?> dataMap) {
            return (Map<String, Object>) dataMap;
        }
        return Map.of();
    }

    private Map<String, String> stringifyMetadata(Map<String, Object> metadata) {
        if (metadata == null || metadata.isEmpty()) {
            return Map.of("source", "squadx");
        }

        Map<String, String> values = new LinkedHashMap<>();
        values.put("source", "squadx");
        metadata.forEach((key, value) -> values.put(key, value != null ? String.valueOf(value) : ""));
        return values;
    }

    private boolean isConfigured(IntegrationConfig.LinktorConfig linktor) {
        boolean credentials = hasText(linktor.getEmail()) && hasText(linktor.getPassword());
        boolean routing = hasText(linktor.getDefaultConversationId())
                || (linktor.isAutoCreateConversation() && hasText(linktor.getChannelId()) && hasText(linktor.getContactId()));
        return hasText(linktor.getUrl()) && credentials && routing;
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private String asString(Object value) {
        return value != null ? String.valueOf(value) : null;
    }

    private String formatContent(String title, String message) {
        if (!hasText(title)) {
            return message != null ? message : "";
        }
        if (!hasText(message)) {
            return title;
        }
        return title + "\n\n" + message;
    }
}
