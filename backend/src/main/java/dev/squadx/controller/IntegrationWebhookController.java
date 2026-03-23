package dev.squadx.controller;

import dev.squadx.dto.common.ApiResponse;
import dev.squadx.integration.ServiceJwtProvider;
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

        // Process based on event type
        switch (eventType) {
            case "memory.flagged" -> log.info("Memory flagged: {}", payload.get("memoryId"));
            case "knowledge.conflict" -> log.warn("Knowledge conflict detected: {}", payload.get("details"));
            case "pattern.detected" -> log.info("New pattern detected: {}", payload.get("pattern"));
            default -> log.debug("Unhandled BrainSentry event: {}", eventType);
        }

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

        switch (eventType) {
            case "session.ended" -> log.info("Live session ended: {}", payload.get("sessionId"));
            case "recording.ready" -> log.info("Recording ready: {}", payload.get("recordingUrl"));
            case "participant.joined" -> log.debug("Participant joined: {}", payload.get("userId"));
            default -> log.debug("Unhandled Live event: {}", eventType);
        }

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
