package dev.squadx.controlpanel.mcp;

import dev.squadx.controlpanel.dto.spectask.SpecTaskResponse;
import dev.squadx.controlpanel.mcp.dto.*;
import dev.squadx.dto.common.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * Superfície HTTP do contrato `workspace` (RFC-0001, ADR-0003). Autenticada pelo token de sessão
 * (via {@link WorkspaceSessionFilter}); o principal é a {@link WorkspaceSession}. Harness-agnóstica
 * (R7). Um bridge MCP stdio/SSE para os CLIs é um adaptador externo.
 */
@RestController
@RequestMapping("/api/v1/workspace/tools")
@RequiredArgsConstructor
@Tag(name = "Control Panel — Workspace MCP", description = "Harness-agnostic workspace tool contract")
public class WorkspaceMcpController {

    private final WorkspaceToolService toolService;

    @GetMapping
    @Operation(summary = "List available workspace tools and the contract version")
    public ResponseEntity<ApiResponse<Map<String, Object>>> tools() {
        return ResponseEntity.ok(ApiResponse.success(Map.of(
                "contract_version", WorkspaceSessionProvider.CONTRACT_VERSION,
                "tools", List.of(
                        "get_change", "get_tasks", "update_task_status",
                        "report_blocker", "materialize_change", "scaffold_tests", "search_code", "get_symbol_context"))));
    }

    @PostMapping("/get_change")
    @Operation(summary = "Briefing for the session's change")
    public ResponseEntity<ApiResponse<GetChangeResponse>> getChange(
            @AuthenticationPrincipal WorkspaceSession session) {
        return ResponseEntity.ok(ApiResponse.success(toolService.getChange(session)));
    }

    @PostMapping("/get_tasks")
    @Operation(summary = "Tasks of the session's change (optionally filtered by assignee)")
    public ResponseEntity<ApiResponse<List<SpecTaskResponse>>> getTasks(
            @AuthenticationPrincipal WorkspaceSession session,
            @RequestParam(required = false) String assignee) {
        return ResponseEntity.ok(ApiResponse.success(toolService.getTasks(session, assignee)));
    }

    @PostMapping("/update_task_status")
    @Operation(summary = "Report progress: em_curso | implementado")
    public ResponseEntity<ApiResponse<UpdateTaskStatusResponse>> updateTaskStatus(
            @AuthenticationPrincipal WorkspaceSession session,
            @Valid @RequestBody UpdateTaskStatusRequest request) {
        return ResponseEntity.ok(ApiResponse.success(toolService.updateTaskStatus(session, request)));
    }

    @PostMapping("/report_blocker")
    @Operation(summary = "Mark a task as blocked with a reason")
    public ResponseEntity<ApiResponse<UpdateTaskStatusResponse>> reportBlocker(
            @AuthenticationPrincipal WorkspaceSession session,
            @Valid @RequestBody ReportBlockerRequest request) {
        return ResponseEntity.ok(ApiResponse.success(toolService.reportBlocker(session, request)));
    }

    @PostMapping("/materialize_change")
    @Operation(summary = "Materialize the change folder to the repo (returns version + commit)")
    public ResponseEntity<ApiResponse<MaterializeResponse>> materializeChange(
            @AuthenticationPrincipal WorkspaceSession session) {
        return ResponseEntity.ok(ApiResponse.success(toolService.materializeChange(session)));
    }

    @PostMapping("/scaffold_tests")
    @Operation(summary = "Generate a test skeleton (one method per scenario) + coverage")
    public ResponseEntity<ApiResponse<ScaffoldTestsResponse>> scaffoldTests(
            @AuthenticationPrincipal WorkspaceSession session,
            @RequestBody(required = false) ScaffoldTestsRequest request) {
        ScaffoldTestsRequest req = request != null ? request : new ScaffoldTestsRequest();
        return ResponseEntity.ok(ApiResponse.success(toolService.scaffoldTests(session, req)));
    }

    @PostMapping("/search_code")
    @Operation(summary = "Search code in the session's active native snapshot")
    public ResponseEntity<ApiResponse<dev.squadx.intelligence.CodeIntelligenceModels.SearchResult>> searchCode(
            @AuthenticationPrincipal WorkspaceSession session,
            @Valid @RequestBody SearchCodeRequest request) {
        return ResponseEntity.ok(ApiResponse.success(toolService.searchCode(session, request)));
    }

    @PostMapping("/get_symbol_context")
    @Operation(summary = "Resolve a symbol in the session's active native snapshot")
    public ResponseEntity<ApiResponse<dev.squadx.intelligence.CodeIntelligenceModels.SymbolContext>> getSymbolContext(
            @AuthenticationPrincipal WorkspaceSession session,
            @Valid @RequestBody SymbolContextRequest request) {
        return ResponseEntity.ok(ApiResponse.success(toolService.getSymbolContext(session, request)));
    }
}
