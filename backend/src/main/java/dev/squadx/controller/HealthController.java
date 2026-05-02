package dev.squadx.controller;

import dev.squadx.dto.common.ApiResponse;
import dev.squadx.integration.BrainSentryClient;
import dev.squadx.integration.IntegrationConfig;
import dev.squadx.integration.LinktorClient;
import dev.squadx.integration.SquadxLiveClient;
import dev.squadx.service.MemoryPolicyService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.sql.DataSource;
import java.sql.Connection;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/health")
@RequiredArgsConstructor
@Tag(name = "Health", description = "Health check endpoints")
public class HealthController {

    private final DataSource dataSource;
    private final RedisTemplate<String, Object> redisTemplate;
    private final BrainSentryClient brainSentryClient;
    private final SquadxLiveClient squadxLiveClient;
    private final LinktorClient linktorClient;
    private final IntegrationConfig integrationConfig;
    private final MemoryPolicyService memoryPolicyService;

    @GetMapping
    @Operation(summary = "Check service health")
    public ResponseEntity<ApiResponse<Map<String, Object>>> health() {
        Map<String, Object> status = new HashMap<>();
        status.put("status", "UP");
        status.put("service", "squadx-backend");
        status.put("version", "0.1.0");
        status.put("database", checkDatabase());
        status.put("redis", checkRedis());

        return ResponseEntity.ok(ApiResponse.success(status));
    }

    @GetMapping("/doctor")
    @Operation(summary = "Deep readiness and integration diagnostics")
    public ResponseEntity<ApiResponse<Map<String, Object>>> doctor() {
        Map<String, Object> status = new HashMap<>();
        status.put("service", "squadx-backend");
        status.put("version", "0.1.0");
        status.put("database", checkDatabase());
        status.put("redis", checkRedis());
        status.put("brainsentry", brainSentryClient.healthCheck());
        status.put("memory_policy", memoryPolicyService.describePolicy());
        status.put("live", squadxLiveClient.healthCheck());
        status.put("linktor", linktorClient.healthCheck());
        status.put("service_secret_configured",
                integrationConfig.getServiceSecret() != null && !integrationConfig.getServiceSecret().isBlank());

        boolean ready = "UP".equals(status.get("database"))
                && "UP".equals(status.get("redis"))
                && integrationReady((Map<String, Object>) status.get("brainsentry"))
                && memoryPolicyReady((Map<String, Object>) status.get("memory_policy"))
                && integrationReady((Map<String, Object>) status.get("live"))
                && integrationReady((Map<String, Object>) status.get("linktor"));

        status.put("status", ready ? "READY" : "DEGRADED");

        if (ready) {
            return ResponseEntity.ok(ApiResponse.success(status));
        }

        return ResponseEntity.status(503).body(ApiResponse.error("Service diagnostics detected degraded dependencies", status));
    }

    @GetMapping("/ready")
    @Operation(summary = "Check if service is ready")
    public ResponseEntity<ApiResponse<Map<String, String>>> ready() {
        Map<String, String> status = new HashMap<>();

        boolean dbReady = "UP".equals(checkDatabase());
        boolean redisReady = "UP".equals(checkRedis());

        if (dbReady && redisReady) {
            status.put("status", "ready");
            return ResponseEntity.ok(ApiResponse.success(status));
        } else {
            status.put("status", "not_ready");
            if (!dbReady) status.put("database", "DOWN");
            if (!redisReady) status.put("redis", "DOWN");
            return ResponseEntity.status(503).body(ApiResponse.error("Service not ready", status));
        }
    }

    @GetMapping("/live")
    @Operation(summary = "Check if service is alive")
    public ResponseEntity<ApiResponse<Map<String, String>>> live() {
        return ResponseEntity.ok(ApiResponse.success(Map.of("status", "alive")));
    }

    private String checkDatabase() {
        try (Connection conn = dataSource.getConnection()) {
            if (conn.isValid(2)) {
                return "UP";
            }
        } catch (Exception e) {
            return "DOWN";
        }
        return "DOWN";
    }

    private String checkRedis() {
        try {
            redisTemplate.getConnectionFactory().getConnection().ping();
            return "UP";
        } catch (Exception e) {
            return "DOWN";
        }
    }

    private boolean integrationReady(Map<String, Object> integrationStatus) {
        if (integrationStatus == null) {
            return false;
        }
        Object enabled = integrationStatus.get("enabled");
        if (Boolean.FALSE.equals(enabled)) {
            return true;
        }
        return "UP".equals(integrationStatus.get("status"));
    }

    private boolean memoryPolicyReady(Map<String, Object> memoryPolicy) {
        if (memoryPolicy == null) {
            return false;
        }
        Object enabled = memoryPolicy.get("enabled");
        if (Boolean.FALSE.equals(enabled)) {
            return true;
        }
        return memoryPolicy.get("memoryScope") != null
                && memoryPolicy.get("proceduralLimit") != null
                && memoryPolicy.get("status") != null;
    }
}
