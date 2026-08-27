package dev.squadx.controlpanel.controller;

import dev.squadx.controlpanel.parser.TaskDecisionParser;
import dev.squadx.controlpanel.service.TaskMaterializationService;
import dev.squadx.dto.common.ApiResponse;
import dev.squadx.integration.ServiceJwtProvider;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * Disparo do laço mínimo decisão→tarefa (RFC-0007, T-0010-3): recebe o conteúdo de um
 * documento de decisão alterado e materializa as tarefas derivadas no projeto, idempotente
 * por âncora. Autenticado por service JWT (mesmo padrão dos demais webhooks de integração).
 */
@RestController
@RequestMapping("/api/v1/webhooks/decision")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Decision webhook", description = "Materializa tarefas a partir de decisões (RFC-0007)")
public class DecisionMaterializeController {

    /** Pedido esperado no corpo: { projectId, path, sourceKind, content }. */
    @PostMapping("/materialize")
    @Operation(summary = "Deriva/atualiza tarefas de uma decisão alterada")
    public ResponseEntity<ApiResponse<List<Long>>> materialize(
            @RequestHeader(value = "Authorization", required = false) String authHeader,
            @RequestBody Map<String, Object> payload
    ) {
        if (!validateServiceAuth(authHeader)) {
            return ResponseEntity.status(401).body(ApiResponse.error("Unauthorized"));
        }
        Long projectId = ((Number) payload.get("projectId")).longValue();
        String path = (String) payload.get("path");
        String sourceKind = (String) payload.getOrDefault("sourceKind", "RFC");
        String content = (String) payload.get("content");

        try {
            List<Long> ids = taskMaterializationService.materialize(content, path, sourceKind, projectId);
            return ResponseEntity.ok(ApiResponse.success(ids, "Decision materialized"));
        } catch (TaskDecisionParser.ParseException e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        }
    }

    private final ServiceJwtProvider serviceJwtProvider;
    private final TaskMaterializationService taskMaterializationService;

    private boolean validateServiceAuth(String authHeader) {
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            return false;
        }
        return serviceJwtProvider.validateToken(authHeader.substring(7), "squadx-decision");
    }
}
