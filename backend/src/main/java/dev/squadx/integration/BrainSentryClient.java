package dev.squadx.integration;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * HTTP client for BrainSentry memory operations.
 * Uses the public BrainSentry REST API instead of private integration-only endpoints.
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

    public Map<String, Object> healthCheck() {
        Map<String, Object> status = new LinkedHashMap<>();
        status.put("enabled", config.getBrainsentry().isEnabled());
        status.put("configured", config.getBrainsentry().getUrl() != null && !config.getBrainsentry().getUrl().isBlank());
        status.put("tenantMode", config.getBrainsentry().isPerOrganizationTenant() ? "per_organization" : "static");
        status.put("tenantId", config.getBrainsentry().isPerOrganizationTenant()
                ? config.getBrainsentry().getTenantPrefix() + "{organizationId}"
                : config.getBrainsentry().getTenantId());
        status.put("url", config.getBrainsentry().getUrl());

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

    public String startExecutionSession(Long organizationId, Long executionId, Long taskId, Long projectId, Long agentId) {
        if (!isEnabled()) {
            return null;
        }

        String tenantId = resolveTenantId(organizationId);

        try {
            Map<String, Object> session = restClient.post()
                    .uri("/api/v1/sessions")
                    .header("Authorization", "Bearer " + jwtProvider.generateToken("brainsentry"))
                    .header("X-Tenant-ID", tenantId)
                    .body(Map.of("userId", buildExecutionUserId(agentId, executionId)))
                    .retrieve()
                    .body(Map.class);

            String sessionId = session != null ? asString(session.get("id")) : null;
            if (sessionId == null || sessionId.isBlank()) {
                log.warn("BrainSentry session start returned no session id for execution {}", executionId);
                return null;
            }

            createMemory(
                    tenantId,
                    "Execution " + executionId + " started for task " + taskId,
                    "Execution started",
                    "ACTION",
                    "IMPORTANT",
                    "EPISODIC",
                    List.of("squadx", "execution", "session:" + sessionId, "task:" + taskId),
                    buildMetadata(
                            executionId,
                            taskId,
                            projectId,
                            agentId,
                            sessionId,
                            "STARTED"
                    ),
                    "squadx-execution",
                    "execution:" + executionId,
                    buildExecutionUserId(agentId, executionId)
            );

            return sessionId;
        } catch (Exception e) {
            log.warn("BrainSentry session start failed for execution {}: {}", executionId, e.getMessage());
            return null;
        }
    }

    public void completeExecutionSession(Long organizationId, String sessionId, Long executionId,
                                         Long taskId, Long projectId, Long agentId,
                                         String status, String summary) {
        if (!isEnabled() || sessionId == null || sessionId.isBlank()) {
            return;
        }

        String tenantId = resolveTenantId(organizationId);

        try {
            createMemory(
                    tenantId,
                    buildCompletionContent(executionId, status, summary),
                    "Execution completed",
                    "INSIGHT",
                    "IMPORTANT",
                    "EPISODIC",
                    List.of("squadx", "execution", "session:" + sessionId, "task:" + taskId, "status:" + status.toLowerCase()),
                    buildMetadata(
                            executionId,
                            taskId,
                            projectId,
                            agentId,
                            sessionId,
                            status
                    ),
                    "squadx-execution",
                    "execution:" + executionId,
                    buildExecutionUserId(agentId, executionId)
            );

            restClient.post()
                    .uri("/api/v1/sessions/{id}/end", sessionId)
                    .header("Authorization", "Bearer " + jwtProvider.generateToken("brainsentry"))
                    .header("X-Tenant-ID", tenantId)
                    .retrieve()
                    .toBodilessEntity();
        } catch (Exception e) {
            log.warn("BrainSentry session end failed for execution {}: {}", executionId, e.getMessage());
        }
    }

    @SuppressWarnings("unchecked")
    public List<Map<String, Object>> listMemories(Long organizationId, int page, int size) {
        if (!isEnabled()) {
            return List.of();
        }

        try {
            Object response = restClient.get()
                    .uri(uriBuilder -> uriBuilder.path("/api/v1/memories")
                            .queryParam("page", Math.max(page, 0))
                            .queryParam("size", Math.max(size, 1))
                            .build())
                    .header("Authorization", "Bearer " + jwtProvider.generateToken("brainsentry"))
                    .header("X-Tenant-ID", resolveTenantId(organizationId))
                    .retrieve()
                    .body(Object.class);

            return unwrapMemoryList(response);
        } catch (Exception e) {
            log.warn("BrainSentry list memories failed: {}", e.getMessage());
            return List.of();
        }
    }

    @SuppressWarnings("unchecked")
    public List<Map<String, Object>> searchMemories(Long organizationId, String query, int limit) {
        if (!isEnabled() || query == null || query.isBlank()) {
            return List.of();
        }

        try {
            Object response = restClient.post()
                    .uri("/api/v1/memories/search")
                    .header("Authorization", "Bearer " + jwtProvider.generateToken("brainsentry"))
                    .header("X-Tenant-ID", resolveTenantId(organizationId))
                    .body(Map.of("query", query, "limit", Math.max(limit, 1)))
                    .retrieve()
                    .body(Object.class);

            return unwrapMemoryList(response);
        } catch (Exception e) {
            log.warn("BrainSentry memory search failed: {}", e.getMessage());
            return List.of();
        }
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> createMemory(Long organizationId, Map<String, Object> payload) {
        if (!isEnabled()) {
            return Map.of();
        }

        try {
            Map<String, Object> response = restClient.post()
                    .uri("/api/v1/memories")
                    .header("Authorization", "Bearer " + jwtProvider.generateToken("brainsentry"))
                    .header("X-Tenant-ID", resolveTenantId(organizationId))
                    .body(payload)
                    .retrieve()
                    .body(Map.class);
            return response != null ? response : Map.of();
        } catch (Exception e) {
            log.warn("BrainSentry create memory failed: {}", e.getMessage());
            return Map.of();
        }
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> updateMemory(Long organizationId, String memoryId, Map<String, Object> payload) {
        if (!isEnabled() || memoryId == null || memoryId.isBlank()) {
            return Map.of();
        }

        try {
            Map<String, Object> response = restClient.put()
                    .uri("/api/v1/memories/{id}", memoryId)
                    .header("Authorization", "Bearer " + jwtProvider.generateToken("brainsentry"))
                    .header("X-Tenant-ID", resolveTenantId(organizationId))
                    .body(payload)
                    .retrieve()
                    .body(Map.class);
            return response != null ? response : Map.of();
        } catch (Exception e) {
            log.warn("BrainSentry update memory failed: {}", e.getMessage());
            return Map.of();
        }
    }

    public boolean deleteMemory(Long organizationId, String memoryId) {
        if (!isEnabled() || memoryId == null || memoryId.isBlank()) {
            return false;
        }

        try {
            restClient.delete()
                    .uri("/api/v1/memories/{id}", memoryId)
                    .header("Authorization", "Bearer " + jwtProvider.generateToken("brainsentry"))
                    .header("X-Tenant-ID", resolveTenantId(organizationId))
                    .retrieve()
                    .toBodilessEntity();
            return true;
        } catch (Exception e) {
            log.warn("BrainSentry delete memory failed: {}", e.getMessage());
            return false;
        }
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> getProfile(Long organizationId, String userId) {
        if (!isEnabled() || userId == null || userId.isBlank()) {
            return Map.of();
        }

        try {
            Map<String, Object> response = restClient.get()
                    .uri("/api/v1/profile/{userId}", userId)
                    .header("Authorization", "Bearer " + jwtProvider.generateToken("brainsentry"))
                    .header("X-Tenant-ID", resolveTenantId(organizationId))
                    .retrieve()
                    .body(Map.class);
            return response != null ? response : Map.of();
        } catch (Exception e) {
            log.warn("BrainSentry get profile failed: {}", e.getMessage());
            return Map.of();
        }
    }

    @SuppressWarnings("unchecked")
    public List<Map<String, Object>> listActiveSessions(Long organizationId) {
        if (!isEnabled()) {
            return List.of();
        }

        try {
            Object response = restClient.get()
                    .uri("/api/v1/sessions/active")
                    .header("Authorization", "Bearer " + jwtProvider.generateToken("brainsentry"))
                    .header("X-Tenant-ID", resolveTenantId(organizationId))
                    .retrieve()
                    .body(Object.class);

            if (response instanceof List<?> list) {
                return list.stream()
                        .filter(Map.class::isInstance)
                        .map(item -> (Map<String, Object>) item)
                        .toList();
            }
        } catch (Exception e) {
            log.warn("BrainSentry list active sessions failed: {}", e.getMessage());
        }

        return List.of();
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> getSession(Long organizationId, String sessionId) {
        if (!isEnabled() || sessionId == null || sessionId.isBlank()) {
            return Map.of();
        }

        try {
            Map<String, Object> response = restClient.get()
                    .uri("/api/v1/sessions/{id}", sessionId)
                    .header("Authorization", "Bearer " + jwtProvider.generateToken("brainsentry"))
                    .header("X-Tenant-ID", resolveTenantId(organizationId))
                    .retrieve()
                    .body(Map.class);
            return response != null ? response : Map.of();
        } catch (Exception e) {
            log.warn("BrainSentry get session failed: {}", e.getMessage());
            return Map.of();
        }
    }

    @SuppressWarnings("unchecked")
    public List<Map<String, Object>> getSessionCache(Long organizationId, String sessionId, int limit) {
        if (!isEnabled() || sessionId == null || sessionId.isBlank()) {
            return List.of();
        }

        try {
            Map<String, Object> response = restClient.get()
                    .uri(uriBuilder -> uriBuilder.path("/api/v1/session-cache/{sessionId}")
                            .queryParam("limit", Math.max(limit, 1))
                            .build(sessionId))
                    .header("Authorization", "Bearer " + jwtProvider.generateToken("brainsentry"))
                    .header("X-Tenant-ID", resolveTenantId(organizationId))
                    .retrieve()
                    .body(Map.class);

            if (response == null) {
                return List.of();
            }
            Object interactions = response.get("interactions");
            if (interactions instanceof List<?> list) {
                return list.stream()
                        .filter(Map.class::isInstance)
                        .map(item -> (Map<String, Object>) item)
                        .toList();
            }
        } catch (Exception e) {
            log.warn("BrainSentry get session cache failed: {}", e.getMessage());
        }

        return List.of();
    }

    private void createMemory(String tenantId,
                              String content,
                              String summary,
                              String category,
                              String importance,
                              String memoryType,
                              List<String> tags,
                              Map<String, Object> metadata,
                              String sourceType,
                              String sourceReference,
                              String createdBy) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("content", content);
        payload.put("summary", summary);
        payload.put("category", category);
        payload.put("importance", importance);
        payload.put("memoryType", memoryType);
        payload.put("tags", tags);
        payload.put("metadata", metadata);
        payload.put("sourceType", sourceType);
        payload.put("sourceReference", sourceReference);
        payload.put("createdBy", createdBy);

        restClient.post()
                .uri("/api/v1/memories")
                .header("Authorization", "Bearer " + jwtProvider.generateToken("brainsentry"))
                .header("X-Tenant-ID", tenantId)
                .body(payload)
                .retrieve()
                .toBodilessEntity();
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> unwrapMemoryList(Object response) {
        if (response instanceof List<?> list) {
            return list.stream()
                    .filter(Map.class::isInstance)
                    .map(item -> (Map<String, Object>) item)
                    .toList();
        }

        if (response instanceof Map<?, ?> map) {
            Object memories = map.get("memories");
            if (memories instanceof List<?> list) {
                return list.stream()
                        .filter(Map.class::isInstance)
                        .map(item -> (Map<String, Object>) item)
                        .toList();
            }

            Object content = map.get("content");
            if (content instanceof List<?> list) {
                return list.stream()
                        .filter(Map.class::isInstance)
                        .map(item -> (Map<String, Object>) item)
                        .toList();
            }

            if (!map.isEmpty()) {
                List<Map<String, Object>> single = new ArrayList<>();
                single.add((Map<String, Object>) map);
                return single;
            }
        }

        return Collections.emptyList();
    }

    private String resolveTenantId(Long organizationId) {
        if (config.getBrainsentry().isPerOrganizationTenant() && organizationId != null) {
            return config.getBrainsentry().getTenantPrefix() + organizationId;
        }
        return config.getBrainsentry().getTenantId();
    }

    private String buildExecutionUserId(Long agentId, Long executionId) {
        if (agentId != null) {
            return "agent-" + agentId;
        }
        return "execution-" + executionId;
    }

    private String buildCompletionContent(Long executionId, String status, String summary) {
        if (summary != null && !summary.isBlank()) {
            return "Execution " + executionId + " finished with status " + status + ". Summary: " + summary;
        }
        return "Execution " + executionId + " finished with status " + status + ".";
    }

    private Map<String, Object> buildMetadata(Long executionId, Long taskId, Long projectId,
                                              Long agentId, String sessionId, String status) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("executionId", executionId);
        if (taskId != null) {
            metadata.put("taskId", taskId);
        }
        if (projectId != null) {
            metadata.put("projectId", projectId);
        }
        if (agentId != null) {
            metadata.put("agentId", agentId);
        }
        if (sessionId != null && !sessionId.isBlank()) {
            metadata.put("sessionId", sessionId);
        }
        metadata.put("status", status);
        return metadata;
    }

    private String asString(Object value) {
        return value != null ? String.valueOf(value) : null;
    }
}
