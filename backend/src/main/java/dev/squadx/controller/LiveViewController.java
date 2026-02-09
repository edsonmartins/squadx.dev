package dev.squadx.controller;

import dev.squadx.dto.common.ApiResponse;
import dev.squadx.dto.liveview.JoinSessionRequest;
import dev.squadx.dto.liveview.LiveSessionRequest;
import dev.squadx.dto.liveview.LiveSessionResponse;
import dev.squadx.model.User;
import dev.squadx.service.LiveViewService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/live-view")
@RequiredArgsConstructor
@Tag(name = "Live View", description = "Live session management for real-time task viewing")
public class LiveViewController {

    private final LiveViewService liveViewService;

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
}
