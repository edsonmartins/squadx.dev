package dev.squadx.controller;

import dev.squadx.dto.ai.HighlightAnalysis;
import dev.squadx.dto.common.ApiResponse;
import dev.squadx.model.User;
import dev.squadx.service.AiAnalysisService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/ai")
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "squadx.ai", name = "enabled", havingValue = "true")
@Tag(name = "AI Analysis", description = "AI-powered code analysis and generation endpoints")
public class AiController {

    private final AiAnalysisService aiAnalysisService;

    @PostMapping("/analyze-logs")
    @Operation(summary = "Analyze execution logs using AI")
    public ResponseEntity<ApiResponse<HighlightAnalysis>> analyzeLogs(
            @RequestBody Map<String, List<String>> request,
            @AuthenticationPrincipal User user
    ) {
        List<String> logs = request.getOrDefault("logs", List.of());
        HighlightAnalysis analysis = aiAnalysisService.analyzeExecutionLogs(logs);
        return ResponseEntity.ok(ApiResponse.success(analysis));
    }

    @PostMapping("/generate-summary")
    @Operation(summary = "Generate execution summary using AI")
    public ResponseEntity<ApiResponse<Map<String, String>>> generateSummary(
            @RequestBody Map<String, String> request,
            @AuthenticationPrincipal User user
    ) {
        String logs = request.getOrDefault("logs", "");
        String summary = aiAnalysisService.generateExecutionSummary(logs);
        return ResponseEntity.ok(ApiResponse.success(Map.of("summary", summary)));
    }

    @PostMapping("/generate-pr-description")
    @Operation(summary = "Generate PR description from diff using AI")
    public ResponseEntity<ApiResponse<Map<String, String>>> generatePrDescription(
            @RequestBody Map<String, String> request,
            @AuthenticationPrincipal User user
    ) {
        String diff = request.getOrDefault("diff", "");
        String taskTitle = request.getOrDefault("taskTitle", "");
        String description = aiAnalysisService.generatePrDescription(diff, taskTitle);
        return ResponseEntity.ok(ApiResponse.success(Map.of("description", description)));
    }
}
