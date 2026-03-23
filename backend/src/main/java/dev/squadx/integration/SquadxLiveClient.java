package dev.squadx.integration;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.Map;

/**
 * HTTP client for SquadX Live collaboration platform.
 * Creates and manages live sessions programmatically.
 */
@Component
@Slf4j
public class SquadxLiveClient {

    private final RestClient restClient;
    private final IntegrationConfig config;
    private final ServiceJwtProvider jwtProvider;

    public SquadxLiveClient(IntegrationConfig config, ServiceJwtProvider jwtProvider) {
        this.config = config;
        this.jwtProvider = jwtProvider;

        if (config.getLive().isEnabled()) {
            this.restClient = RestClient.builder()
                    .baseUrl(config.getLive().getUrl())
                    .build();
            log.info("SquadX Live client initialized: url={}", config.getLive().getUrl());
        } else {
            this.restClient = null;
            log.info("SquadX Live client disabled");
        }
    }

    public boolean isEnabled() {
        return restClient != null && config.getLive().isEnabled();
    }

    /**
     * Create a live session for an agent execution.
     *
     * @return session info map with sessionId, joinCode, joinUrl or null if disabled
     */
    @SuppressWarnings("unchecked")
    public Map<String, String> createSession(Long taskId, Long agentId, String mode) {
        if (!isEnabled()) return null;

        try {
            Map<String, Object> response = restClient.post()
                    .uri("/api/integration/sessions")
                    .header("Authorization", "Bearer " + jwtProvider.generateToken("squadx-live"))
                    .body(Map.of(
                            "taskId", taskId,
                            "agentId", agentId != null ? agentId : 0,
                            "mode", mode != null ? mode : "p2p"
                    ))
                    .retrieve()
                    .body(Map.class);

            if (response == null) {
                log.warn("SquadX Live returned null response for createSession");
                return null;
            }

            Object sessionId = response.get("sessionId");
            Object joinCode = response.get("joinCode");
            Object joinUrl = response.get("joinUrl");

            return Map.of(
                    "sessionId", sessionId != null ? String.valueOf(sessionId) : "",
                    "joinCode", joinCode != null ? String.valueOf(joinCode) : "",
                    "joinUrl", joinUrl != null ? String.valueOf(joinUrl) : ""
            );
        } catch (Exception e) {
            log.warn("Failed to create SquadX Live session: {}", e.getMessage());
            return null;
        }
    }

    /**
     * End a live session by task ID.
     */
    public void endSessionForTask(Long taskId) {
        if (!isEnabled()) return;

        log.info("Ending SquadX Live session for task {}", taskId);
        // In a full implementation, we'd look up the sessionId by taskId
        // For now, this is a placeholder for the webhook-based approach
    }

    /**
     * End a specific live session.
     */
    public void endSession(String sessionId) {
        if (!isEnabled()) return;

        try {
            restClient.delete()
                    .uri("/api/integration/sessions/{sessionId}", sessionId)
                    .header("Authorization", "Bearer " + jwtProvider.generateToken("squadx-live"))
                    .retrieve()
                    .toBodilessEntity();
            log.debug("SquadX Live session {} ended", sessionId);
        } catch (Exception e) {
            log.warn("Failed to end SquadX Live session: {}", e.getMessage());
        }
    }
}
