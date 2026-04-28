package dev.squadx.integration;

import dev.squadx.model.LiveSession;
import dev.squadx.repository.LiveSessionRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.List;
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
    private final LiveSessionRepository liveSessionRepository;

    public SquadxLiveClient(IntegrationConfig config, ServiceJwtProvider jwtProvider,
                            LiveSessionRepository liveSessionRepository) {
        this.config = config;
        this.jwtProvider = jwtProvider;
        this.liveSessionRepository = liveSessionRepository;

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

    public Map<String, Object> healthCheck() {
        Map<String, Object> status = new java.util.LinkedHashMap<>();
        status.put("enabled", config.getLive().isEnabled());
        status.put("configured", config.getLive().getUrl() != null && !config.getLive().getUrl().isBlank());
        status.put("url", config.getLive().getUrl());

        if (!isEnabled()) {
            status.put("reachable", false);
            status.put("status", "DISABLED");
            return status;
        }

        try {
            restClient.get()
                    .uri("/health")
                    .retrieve()
                    .toBodilessEntity();
            status.put("reachable", true);
            status.put("status", "UP");
        } catch (Exception e) {
            status.put("reachable", false);
            status.put("status", "DOWN");
            status.put("error", e.getMessage());
        }

        return status;
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

        List<LiveSession> sessions = liveSessionRepository.findByTaskIdOrderByCreatedAtDesc(taskId);
        for (LiveSession session : sessions) {
            String externalSessionId = session.getExternalSessionId();
            if (externalSessionId != null && !externalSessionId.isBlank()) {
                log.info("Ending SquadX Live session {} for task {}", externalSessionId, taskId);
                endSession(externalSessionId);
                return;
            }
        }

        log.debug("No external SquadX Live session found for task {}", taskId);
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
