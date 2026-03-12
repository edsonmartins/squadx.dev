package dev.squadx.controller;

import dev.squadx.dto.common.ApiResponse;
import dev.squadx.dto.highlight.GenerateHighlightsRequest;
import dev.squadx.dto.highlight.HighlightResponse;
import dev.squadx.model.User;
import dev.squadx.service.HighlightService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/highlights")
@RequiredArgsConstructor
@Tag(name = "Highlights", description = "AI-powered session highlight detection and summaries")
public class HighlightController {

    private final HighlightService highlightService;

    @PostMapping("/generate")
    @Operation(summary = "Generate highlights for a recording by analyzing execution logs")
    public ResponseEntity<ApiResponse<List<HighlightResponse>>> generate(
            @Valid @RequestBody GenerateHighlightsRequest request,
            @AuthenticationPrincipal User user
    ) {
        List<HighlightResponse> highlights = highlightService.generateHighlights(
                request.getRecordingId(), request.getExecutionLogs());
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponse.success(highlights, "Highlights generated"));
    }

    @GetMapping("/recording/{recordingId}")
    @Operation(summary = "List all highlights for a recording")
    public ResponseEntity<ApiResponse<List<HighlightResponse>>> getByRecording(
            @PathVariable Long recordingId,
            @AuthenticationPrincipal User user
    ) {
        List<HighlightResponse> highlights = highlightService.getByRecording(recordingId);
        return ResponseEntity.ok(ApiResponse.success(highlights));
    }

    @GetMapping("/recording/{recordingId}/summary")
    @Operation(summary = "Get AI-generated text summary of a recording session")
    public ResponseEntity<ApiResponse<Map<String, String>>> getSummary(
            @PathVariable Long recordingId,
            @AuthenticationPrincipal User user
    ) {
        String summary = highlightService.getSummary(recordingId);
        return ResponseEntity.ok(ApiResponse.success(Map.of("summary", summary)));
    }
}
