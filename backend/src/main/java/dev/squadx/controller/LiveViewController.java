package dev.squadx.controller;

import dev.squadx.dto.common.ApiResponse;
import dev.squadx.dto.liveview.JoinSessionRequest;
import dev.squadx.dto.liveview.LiveChatMessageRequest;
import dev.squadx.dto.liveview.LiveChatMessageResponse;
import dev.squadx.dto.liveview.LiveSessionRequest;
import dev.squadx.dto.liveview.LiveSessionResponse;
import dev.squadx.dto.supabase.SupabaseLiveSession;
import dev.squadx.model.User;
import dev.squadx.service.LiveViewService;
import dev.squadx.service.SupabaseLiveSessionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Optional;

@RestController
@RequestMapping("/api/v1/live-view")
@RequiredArgsConstructor
@Tag(name = "Live View", description = "Live session management for real-time task viewing")
public class LiveViewController {

    private final LiveViewService liveViewService;
    private final SupabaseLiveSessionService supabaseLiveSessionService;

    @PostMapping("/sessions")
    @Operation(summary = "Create a new live session for a task")
    public ResponseEntity<ApiResponse<LiveSessionResponse>> createSession(
            @Valid @RequestBody LiveSessionRequest request,
            @AuthenticationPrincipal User user
    ) {
        LiveSessionResponse response = liveViewService.createSession(request, user);
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponse.success(response, "Live session created"));
    }

    @PostMapping("/agents/{agentId}/direct-session")
    @Operation(summary = "Ensure an always-available direct live session for an agent")
    public ResponseEntity<ApiResponse<LiveSessionResponse>> ensureDirectAgentSession(
            @PathVariable Long agentId,
            @AuthenticationPrincipal User user
    ) {
        LiveSessionResponse response = liveViewService.ensureDirectAgentSession(agentId, user);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(response, "Direct agent session ready"));
    }

    @PostMapping("/sessions/join")
    @Operation(summary = "Join an existing live session")
    public ResponseEntity<ApiResponse<LiveSessionResponse>> joinSession(
            @Valid @RequestBody JoinSessionRequest request,
            @AuthenticationPrincipal User user
    ) {
        LiveSessionResponse response = liveViewService.joinSession(request, user);
        return ResponseEntity.ok(ApiResponse.success(response, "Joined live session"));
    }

    @PostMapping("/sessions/{sessionId}/start")
    @Operation(summary = "Start a pending live session (host only)")
    public ResponseEntity<ApiResponse<LiveSessionResponse>> startSession(
            @PathVariable Long sessionId,
            @AuthenticationPrincipal User user
    ) {
        LiveSessionResponse response = liveViewService.startSession(sessionId, user);
        return ResponseEntity.ok(ApiResponse.success(response, "Live session started"));
    }

    @PostMapping("/sessions/{sessionId}/end")
    @Operation(summary = "End an active live session (host only)")
    public ResponseEntity<ApiResponse<LiveSessionResponse>> endSession(
            @PathVariable Long sessionId,
            @AuthenticationPrincipal User user
    ) {
        LiveSessionResponse response = liveViewService.endSession(sessionId, user);
        return ResponseEntity.ok(ApiResponse.success(response, "Live session ended"));
    }

    @PostMapping("/sessions/{code}/leave")
    @Operation(summary = "Leave a live session")
    public ResponseEntity<ApiResponse<Void>> leaveSession(
            @PathVariable String code,
            @AuthenticationPrincipal User user
    ) {
        liveViewService.leaveSession(code, user);
        return ResponseEntity.ok(ApiResponse.success(null, "Left live session"));
    }

    @GetMapping("/sessions/code/{code}")
    @Operation(summary = "Get live session by code")
    public ResponseEntity<ApiResponse<LiveSessionResponse>> getByCode(
            @PathVariable String code,
            @AuthenticationPrincipal User user
    ) {
        LiveSessionResponse response = liveViewService.getByCode(code, user);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @GetMapping("/sessions/task/{taskId}")
    @Operation(summary = "Get active live session for a task")
    public ResponseEntity<ApiResponse<LiveSessionResponse>> getByTaskId(
            @PathVariable Long taskId,
            @AuthenticationPrincipal User user
    ) {
        LiveSessionResponse response = liveViewService.getByTaskId(taskId, user);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @GetMapping("/sessions/organization/{organizationId}")
    @Operation(summary = "Get all active live sessions for an organization")
    public ResponseEntity<ApiResponse<List<LiveSessionResponse>>> getActiveByOrganization(
            @PathVariable Long organizationId,
            @AuthenticationPrincipal User user
    ) {
        List<LiveSessionResponse> response = liveViewService.getActiveSessionsByOrganization(organizationId, user);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @PostMapping("/sessions/{sessionId}/participants/{userId}/grant-control")
    @Operation(summary = "Grant control to a participant (host only)")
    public ResponseEntity<ApiResponse<LiveSessionResponse>> grantControl(
            @PathVariable Long sessionId,
            @PathVariable Long userId,
            @AuthenticationPrincipal User user
    ) {
        LiveSessionResponse response = liveViewService.grantControl(sessionId, userId, user);
        return ResponseEntity.ok(ApiResponse.success(response, "Control granted"));
    }

    @PostMapping("/sessions/{sessionId}/participants/{userId}/revoke-control")
    @Operation(summary = "Revoke control from a participant (host only)")
    public ResponseEntity<ApiResponse<LiveSessionResponse>> revokeControl(
            @PathVariable Long sessionId,
            @PathVariable Long userId,
            @AuthenticationPrincipal User user
    ) {
        LiveSessionResponse response = liveViewService.revokeControl(sessionId, userId, user);
        return ResponseEntity.ok(ApiResponse.success(response, "Control revoked"));
    }

    // ==================== Supabase Endpoints ====================
    // These endpoints query Supabase for sessions created by the Python client

    @GetMapping("/supabase/sessions/code/{code}")
    @Operation(summary = "Get live session by join code from Supabase (for Python client sessions)")
    public ResponseEntity<ApiResponse<LiveSessionResponse>> getSupabaseSessionByCode(
            @PathVariable String code
    ) {
        Optional<SupabaseLiveSession> session = supabaseLiveSessionService.getSessionByCode(code);
        if (session.isPresent()) {
            LiveSessionResponse response = supabaseLiveSessionService.toResponse(session.get());
            return ResponseEntity.ok(ApiResponse.success(response));
        }
        return ResponseEntity.notFound().build();
    }

    @GetMapping("/supabase/sessions/task/{taskId}")
    @Operation(summary = "Get active live session for a task from Supabase")
    public ResponseEntity<ApiResponse<LiveSessionResponse>> getSupabaseSessionByTaskId(
            @PathVariable Long taskId
    ) {
        Optional<SupabaseLiveSession> session = supabaseLiveSessionService.getActiveSessionByTaskId(taskId);
        if (session.isPresent()) {
            LiveSessionResponse response = supabaseLiveSessionService.toResponse(session.get());
            return ResponseEntity.ok(ApiResponse.success(response));
        }
        return ResponseEntity.notFound().build();
    }

    @GetMapping("/supabase/sessions/active")
    @Operation(summary = "Get all active live sessions from Supabase")
    public ResponseEntity<ApiResponse<List<LiveSessionResponse>>> getSupabaseActiveSessions() {
        List<SupabaseLiveSession> sessions = supabaseLiveSessionService.getActiveSessions();
        List<LiveSessionResponse> responses = sessions.stream()
                .map(supabaseLiveSessionService::toResponse)
                .toList();
        return ResponseEntity.ok(ApiResponse.success(responses));
    }

    @PostMapping("/supabase/sessions")
    @Operation(summary = "Create a new live session in Supabase")
    public ResponseEntity<ApiResponse<LiveSessionResponse>> createSupabaseSession(
            @RequestParam Long taskId,
            @RequestParam(required = false) String hostUserId,
            @RequestParam(defaultValue = "p2p") String mode,
            @RequestParam(defaultValue = "25") Integer maxViewers
    ) {
        Optional<SupabaseLiveSession> session = supabaseLiveSessionService.createSession(
                taskId, hostUserId, mode, maxViewers);
        if (session.isPresent()) {
            LiveSessionResponse response = supabaseLiveSessionService.toResponse(session.get());
            return ResponseEntity
                    .status(HttpStatus.CREATED)
                    .body(ApiResponse.success(response, "Live session created in Supabase"));
        }
        return ResponseEntity.badRequest().build();
    }

    @PostMapping("/supabase/sessions/{sessionId}/end")
    @Operation(summary = "End a live session in Supabase")
    public ResponseEntity<ApiResponse<Void>> endSupabaseSession(
            @PathVariable String sessionId
    ) {
        boolean success = supabaseLiveSessionService.endSession(sessionId);
        if (success) {
            return ResponseEntity.ok(ApiResponse.success(null, "Live session ended"));
        }
        return ResponseEntity.badRequest().build();
    }

    @GetMapping("/sessions/{sessionId}/chat")
    @Operation(summary = "Get SquadX Live chat history for a linked live session")
    public ResponseEntity<ApiResponse<List<LiveChatMessageResponse>>> getChatHistory(
            @PathVariable Long sessionId,
            @RequestParam(defaultValue = "100") int limit,
            @RequestParam(required = false) String before,
            @RequestParam(required = false) String recipientId,
            @AuthenticationPrincipal User user
    ) {
        List<LiveChatMessageResponse> response = liveViewService.getChatHistory(sessionId, limit, before, recipientId, user);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @PostMapping("/sessions/{sessionId}/chat/agent-message")
    @Operation(summary = "Send an agent message to the linked SquadX Live session")
    public ResponseEntity<ApiResponse<LiveChatMessageResponse>> sendAgentMessage(
            @PathVariable Long sessionId,
            @Valid @RequestBody LiveChatMessageRequest request,
            @AuthenticationPrincipal User user
    ) {
        LiveChatMessageResponse response = liveViewService.sendAgentMessage(sessionId, request, user);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(response, "Agent live message sent"));
    }
}
