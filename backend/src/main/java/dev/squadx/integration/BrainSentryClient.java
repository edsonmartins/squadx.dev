package dev.squadx.integration;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.Map;

/**
 * HTTP client for BrainSentry agent memory system.
 * Used for backend-to-backend communication (execution lifecycle events).
 */
@Component
@Slf4j
public class BrainSentryClient {

    private final WebClient webClient;
    private final IntegrationConfig config;
    private final ServiceJwtProvider jwtProvider;

    public BrainSentryClient(IntegrationConfig config, ServiceJwtProvider jwtProvider) {
        this.config = config;
        this.jwtProvider = jwtProvider;

        if (config.getBrainsentry().isEnabled()) {
            this.webClient = WebClient.builder()
                    .baseUrl(config.getBrainsentry().getUrl())
                    .build();
            log.info("BrainSentry client initialized: url={}", config.getBrainsentry().getUrl());
        } else {
            this.webClient = null;
            log.info("BrainSentry client disabled");
        }
    }

    public boolean isEnabled() {
        return webClient != null && config.getBrainsentry().isEnabled();
    }

    /**
     * Notify BrainSentry that an execution has started.
     */
    public void notifyExecutionStarted(Long executionId, Long taskId, Long agentId) {
        if (!isEnabled()) return;

        try {
            webClient.post()
                    .uri("/api/v1/integration/execution/start")
                    .header("Authorization", "Bearer " + jwtProvider.generateToken("brainsentry"))
                    .header("X-Tenant-ID", config.getBrainsentry().getTenantId())
                    .bodyValue(Map.of(
                            "executionId", executionId.toString(),
                            "taskId", taskId.toString(),
                            "agentId", agentId != null ? agentId.toString() : ""
                    ))
                    .retrieve()
                    .toBodilessEntity()
                    .subscribe(
                            ok -> log.debug("BrainSentry notified: execution {} started", executionId),
                            err -> log.warn("Failed to notify BrainSentry: {}", err.getMessage())
                    );
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
            webClient.post()
                    .uri("/api/v1/integration/execution/end")
                    .header("Authorization", "Bearer " + jwtProvider.generateToken("brainsentry"))
                    .header("X-Tenant-ID", config.getBrainsentry().getTenantId())
                    .bodyValue(Map.of(
                            "executionId", executionId.toString(),
                            "status", status,
                            "summary", summary != null ? summary : ""
                    ))
                    .retrieve()
                    .toBodilessEntity()
                    .subscribe(
                            ok -> log.debug("BrainSentry notified: execution {} {}", executionId, status),
                            err -> log.warn("Failed to notify BrainSentry: {}", err.getMessage())
                    );
        } catch (Exception e) {
            log.warn("BrainSentry notification failed: {}", e.getMessage());
        }
    }
}
