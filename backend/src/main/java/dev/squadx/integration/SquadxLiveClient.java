package dev.squadx.integration;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.Map;

/**
 * HTTP client for SquadX Live collaboration platform.
 * Creates and manages live sessions programmatically.
 */
@Component
@Slf4j
public class SquadxLiveClient {

    private final WebClient webClient;
    private final IntegrationConfig config;
    private final ServiceJwtProvider jwtProvider;

    public SquadxLiveClient(IntegrationConfig config, ServiceJwtProvider jwtProvider) {
        this.config = config;
        this.jwtProvider = jwtProvider;

        if (config.getLive().isEnabled()) {
            this.webClient = WebClient.builder()
                    .baseUrl(config.getLive().getUrl())
                    .build();
            log.info("SquadX Live client initialized: url={}", config.getLive().getUrl());
        } else {
            this.webClient = null;
            log.info("SquadX Live client disabled");
        }
    }

    public boolean isEnabled() {
        return webClient != null && config.getLive().isEnabled();
    }

    /**
     * Create a live session for an agent execution.
     *
     * @return session info map with sessionId, joinCode, joinUrl or null if disabled
     */
    public Map<String, String> createSession(Long taskId, Long agentId, String mode) {
        if (!isEnabled()) return null;

        try {
            return webClient.post()
                    .uri("/api/integration/sessions")
                    .header("Authorization", "Bearer " + jwtProvider.generateToken("squadx-live"))
                    .bodyValue(Map.of(
                            "taskId", taskId,
                            "agentId", agentId != null ? agentId : 0,
                            "mode", mode != null ? mode : "p2p"
                    ))
                    .retrieve()
                    .bodyToMono(Map.class)
                    .map(m -> {
                        @SuppressWarnings("unchecked")
                        Map<String, String> result = Map.of(
                                "sessionId", String.valueOf(m.get("sessionId")),
                                "joinCode", String.valueOf(m.get("joinCode")),
                                "joinUrl", String.valueOf(m.get("joinUrl"))
                        );
                        return result;
                    })
                    .block();
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
            webClient.delete()
                    .uri("/api/integration/sessions/{sessionId}", sessionId)
                    .header("Authorization", "Bearer " + jwtProvider.generateToken("squadx-live"))
                    .retrieve()
                    .toBodilessEntity()
                    .subscribe(
                            ok -> log.debug("SquadX Live session {} ended", sessionId),
                            err -> log.warn("Failed to end SquadX Live session: {}", err.getMessage())
                    );
        } catch (Exception e) {
            log.warn("Failed to end SquadX Live session: {}", e.getMessage());
        }
    }
}
