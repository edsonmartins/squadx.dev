package dev.squadx.controller;

import dev.squadx.dto.autopilot.AutopilotRunResponse;
import dev.squadx.dto.common.ApiResponse;
import dev.squadx.service.AutopilotService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Public webhook endpoint for firing autopilots. Authentication is the secret
 * token in the path (this route is permitted under /api/v1/webhooks/**).
 */
@RestController
@RequestMapping("/api/v1/webhooks/autopilots")
@RequiredArgsConstructor
@Tag(name = "Autopilot Webhooks", description = "Public webhook triggers for autopilots")
public class AutopilotWebhookController {

    private final AutopilotService autopilotService;

    @PostMapping("/{token}")
    @Operation(summary = "Fire an autopilot via its webhook token")
    public ResponseEntity<ApiResponse<AutopilotRunResponse>> fire(@PathVariable String token) {
        return ResponseEntity.ok(
                ApiResponse.success(autopilotService.fireByWebhook(token), "Autopilot triggered"));
    }
}
