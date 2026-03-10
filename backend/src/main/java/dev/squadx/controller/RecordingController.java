package dev.squadx.controller;

import dev.squadx.dto.common.ApiResponse;
import dev.squadx.dto.recording.CompleteRecordingRequest;
import dev.squadx.dto.recording.RecordingResponse;
import dev.squadx.dto.recording.StartRecordingRequest;
import dev.squadx.model.User;
import dev.squadx.service.RecordingService;
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
@RequestMapping("/api/v1/recordings")
@RequiredArgsConstructor
@Tag(name = "Recordings", description = "Session recording management with S3 storage")
public class RecordingController {

    private final RecordingService recordingService;

    @PostMapping("/start")
    @Operation(summary = "Start recording a live session")
    public ResponseEntity<ApiResponse<RecordingResponse>> startRecording(
            @Valid @RequestBody StartRecordingRequest request,
            @AuthenticationPrincipal User user
    ) {
        RecordingResponse response = recordingService.startRecording(request.getSessionId());
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponse.success(response, "Recording started"));
    }

    @PostMapping("/{id}/complete")
    @Operation(summary = "Mark a recording as complete")
    public ResponseEntity<ApiResponse<RecordingResponse>> completeRecording(
            @PathVariable Long id,
            @Valid @RequestBody CompleteRecordingRequest request,
            @AuthenticationPrincipal User user
    ) {
        RecordingResponse response = recordingService.completeRecording(
                id, request.getFileSizeBytes(), request.getDurationSeconds());
        return ResponseEntity.ok(ApiResponse.success(response, "Recording completed"));
    }

    @GetMapping("/{id}/url")
    @Operation(summary = "Get playback URL for a recording")
    public ResponseEntity<ApiResponse<RecordingResponse>> getRecordingUrl(
            @PathVariable Long id,
            @AuthenticationPrincipal User user
    ) {
        RecordingResponse response = recordingService.getRecordingUrl(id);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @GetMapping("/session/{sessionId}")
    @Operation(summary = "List all recordings for a live session")
    public ResponseEntity<ApiResponse<List<RecordingResponse>>> listBySession(
            @PathVariable Long sessionId,
            @AuthenticationPrincipal User user
    ) {
        List<RecordingResponse> response = recordingService.listBySession(sessionId);
        return ResponseEntity.ok(ApiResponse.success(response));
    }
}
