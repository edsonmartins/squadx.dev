package dev.squadx.integration;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.Map;

/**
 * HTTP client for BrainSentry agent memory system.
 * Used for backend-to-backend communication (execution lifecycle events).
 */
@Component
@Slf4j
public class BrainSentryClient {

    private final RestClient restClient;
    private final IntegrationConfig config;
    private final ServiceJwtProvider jwtProvider;

    public BrainSentryClient(IntegrationConfig config, ServiceJwtProvider jwtProvider) {
        this.config = config;
        this.jwtProvider = jwtProvider;

        if (config.getBrainsentry().isEnabled()) {
            this.restClient = RestClient.builder()
                    .baseUrl(config.getBrainsentry().getUrl())
                    .build();
            log.info("BrainSentry client initialized: url={}", config.getBrainsentry().getUrl());
        } else {
            this.restClient = null;
            log.info("BrainSentry client disabled");
        }
    }

    public boolean isEnabled() {
        return restClient != null && config.getBrainsentry().isEnabled();
    }

    /**
     * Notify BrainSentry that an execution has started.
     */
    public void notifyExecutionStarted(Long executionId, Long taskId, Long agentId) {
        if (!isEnabled()) return;

        try {
            restClient.post()
                    .uri("/api/v1/integration/execution/start")
                    .header("Authorization", "Bearer " + jwtProvider.generateToken("brainsentry"))
                    .header("X-Tenant-ID", config.getBrainsentry().getTenantId())
                    .body(Map.of(
                            "executionId", executionId.toString(),
                            "taskId", taskId.toString(),
                            "agentId", agentId != null ? agentId.toString() : ""
                    ))
                    .retrieve()
                    .toBodilessEntity();
            log.debug("BrainSentry notified: execution {} started", executionId);
        } catch (Exception e) {
            log.warn("BrainSentry notification failed: {}", e.getMessage());
        }
    }

    /**
     * Notify BrainSentry that an execution has completed.
     */
    public void notifyExecutionCompleted(Long executionId, String status, String summary) {
        if (!isEnabled()) return;

        try {
            restClient.post()
                    .uri("/api/v1/integration/execution/end")
                    .header("Authorization", "Bearer " + jwtProvider.generateToken("brainsentry"))
                    .header("X-Tenant-ID", config.getBrainsentry().getTenantId())
                    .body(Map.of(
                            "executionId", executionId.toString(),
                            "status", status,
                            "summary", summary != null ? summary : ""
                    ))
                    .retrieve()
                    .toBodilessEntity();
            log.debug("BrainSentry notified: execution {} {}", executionId, status);
        } catch (Exception e) {
            log.warn("BrainSentry notification failed: {}", e.getMessage());
        }
    }
}
