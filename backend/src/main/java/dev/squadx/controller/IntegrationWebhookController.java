package dev.squadx.controller;

import dev.squadx.dto.common.ApiResponse;
import dev.squadx.integration.ServiceJwtProvider;
import dev.squadx.service.IntegrationWebhookService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * Receives inbound webhooks from brainsentry and squadx-live.
 * Validates service JWT before processing.
 */
@RestController
@RequestMapping("/api/v1/webhooks")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Integration Webhooks", description = "Inbound webhooks from integrated services")
public class IntegrationWebhookController {

    private final ServiceJwtProvider serviceJwtProvider;
    private final IntegrationWebhookService integrationWebhookService;

    @PostMapping("/brainsentry")
    @Operation(summary = "Receive webhook from BrainSentry")
    public ResponseEntity<ApiResponse<Void>> receiveBrainSentryWebhook(
            @RequestHeader(value = "Authorization", required = false) String authHeader,
            @RequestBody Map<String, Object> payload
    ) {
        if (!validateServiceAuth(authHeader, "brainsentry")) {
            return ResponseEntity.status(401).body(ApiResponse.error("Unauthorized"));
        }

        String eventType = (String) payload.getOrDefault("event", "unknown");
        log.info("Received BrainSentry webhook: event={}", eventType);
        integrationWebhookService.handleBrainSentryWebhook(payload);

        return ResponseEntity.ok(ApiResponse.success(null, "Webhook received"));
    }

    @PostMapping("/live")
    @Operation(summary = "Receive webhook from SquadX Live")
    public ResponseEntity<ApiResponse<Void>> receiveLiveWebhook(
            @RequestHeader(value = "Authorization", required = false) String authHeader,
            @RequestBody Map<String, Object> payload
    ) {
        if (!validateServiceAuth(authHeader, "squadx-live")) {
            return ResponseEntity.status(401).body(ApiResponse.error("Unauthorized"));
        }

        String eventType = (String) payload.getOrDefault("event", "unknown");
        log.info("Received SquadX Live webhook: event={}", eventType);
        integrationWebhookService.handleLiveWebhook(payload);

        return ResponseEntity.ok(ApiResponse.success(null, "Webhook received"));
    }

    private boolean validateServiceAuth(String authHeader, String expectedIssuer) {
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            return false;
        }
        String token = authHeader.substring(7);
        return serviceJwtProvider.validateToken(token, expectedIssuer);
    }
}
