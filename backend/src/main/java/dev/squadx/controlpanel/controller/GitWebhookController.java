package dev.squadx.controlpanel.controller;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.squadx.controlpanel.service.GitWebhookService;
import dev.squadx.dto.common.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * Recebe webhooks de Git (GitHub-shaped) e os transforma em eventos de tarefa. Verifica HMAC sobre
 * o corpo cru antes de processar (execution-tracking R1).
 */
@RestController
@RequestMapping("/api/v1/webhooks")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Control Panel — Git Webhooks", description = "Git webhook ingestion (execution tracking)")
public class GitWebhookController {

    private final GitWebhookService gitWebhookService;
    private final ObjectMapper objectMapper;

    @PostMapping("/git")
    @Operation(summary = "Receive a Git (GitHub-shaped) webhook")
    public ResponseEntity<ApiResponse<Void>> receive(
            @RequestHeader(value = "X-Hub-Signature-256", required = false) String signature,
            @RequestHeader(value = "X-GitHub-Event", required = false) String eventType,
            @RequestBody byte[] rawBody
    ) {
        if (!gitWebhookService.verifySignature(signature, rawBody)) {
            return ResponseEntity.status(401).body(ApiResponse.error("Invalid webhook signature"));
        }
        try {
            Map<String, Object> payload =
                    objectMapper.readValue(rawBody, new TypeReference<Map<String, Object>>() {});
            gitWebhookService.handle(eventType, payload);
        } catch (Exception e) {
            log.warn("Failed to process Git webhook", e);
            return ResponseEntity.badRequest().body(ApiResponse.error("Malformed webhook payload"));
        }
        return ResponseEntity.ok(ApiResponse.success(null, "Webhook received"));
    }
}
