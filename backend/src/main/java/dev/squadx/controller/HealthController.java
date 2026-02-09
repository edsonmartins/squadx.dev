package dev.squadx.controller;

import dev.squadx.dto.common.ApiResponse;
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
}
