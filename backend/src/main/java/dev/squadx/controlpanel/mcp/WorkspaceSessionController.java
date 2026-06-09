package dev.squadx.controlpanel.mcp;

import dev.squadx.controlpanel.mcp.dto.SessionRequest;
import dev.squadx.controlpanel.mcp.dto.SessionResponse;
import dev.squadx.controlpanel.service.ChangeService;
import dev.squadx.controlpanel.service.HarnessService;
import dev.squadx.dto.common.ApiResponse;
import dev.squadx.model.User;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/**
 * Emite tokens de sessão MCP do workspace. Autenticado pelo JWT de usuário: o usuário abre uma
 * sessão escopada a uma mudança, e o agente passa a operar em nome dele (RFC-0001 §5).
 */
@RestController
@RequestMapping("/api/v1/workspace/sessions")
@RequiredArgsConstructor
@Tag(name = "Control Panel — Workspace Sessions", description = "Issue MCP workspace session tokens")
public class WorkspaceSessionController {

    private final ChangeService changeService;
    private final WorkspaceSessionProvider sessionProvider;
    private final HarnessService harnessService;

    @PostMapping
    @Operation(summary = "Open a scoped MCP workspace session for a change")
    public ResponseEntity<ApiResponse<SessionResponse>> open(
            @Valid @RequestBody SessionRequest request,
            @AuthenticationPrincipal User user
    ) {
        ChangeService.WorkspaceScope scope = changeService.resolveScope(request.getChangeId(), user);
        harnessService.touchConnection(scope.orgId(), request.getHarnessKey()); // handshake vivo
        WorkspaceSession session = new WorkspaceSession(
                user.getId(), scope.orgId(), scope.projectId(), scope.changeId(), request.getAssignee());

        SessionResponse response = SessionResponse.builder()
                .token(sessionProvider.issue(session))
                .expiresIn(sessionProvider.getTtlSeconds())
                .contractVersion(WorkspaceSessionProvider.CONTRACT_VERSION)
                .build();
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(response, "Workspace session opened"));
    }
}
