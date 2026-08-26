package dev.squadx.controller;

import dev.squadx.dto.common.ApiResponse;
import dev.squadx.dto.intelligence.EnsureSnapshotRequest;
import dev.squadx.dto.intelligence.SnapshotResponse;
import dev.squadx.dto.intelligence.SearchCodeRequest;
import dev.squadx.dto.intelligence.SymbolContextRequest;
import dev.squadx.dto.intelligence.ChangeImpactRequest;
import dev.squadx.dto.intelligence.DependencyGraphRequest;
import dev.squadx.dto.intelligence.ShadowSearchRequest;
import dev.squadx.dto.intelligence.ShadowComparisonResponse;
import dev.squadx.dto.intelligence.DecisionCandidateRequest;
import dev.squadx.dto.intelligence.DecisionReviewRequest;
import dev.squadx.dto.intelligence.DecisionCandidateResponse;
import dev.squadx.intelligence.CodeIntelligenceModels.SearchResult;
import dev.squadx.intelligence.CodeIntelligenceModels.SymbolContext;
import dev.squadx.intelligence.CodeIntelligenceModels.DependencyGraph;
import dev.squadx.intelligence.CodeIntelligenceModels.ChangeImpact;
import dev.squadx.model.User;
import dev.squadx.service.CodeIntelligenceSnapshotService;
import dev.squadx.service.CodeIntelligenceQueryService;
import dev.squadx.service.CodeIntelligenceShadowService;
import dev.squadx.service.CodeIntelligenceDecisionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/intelligence")
@RequiredArgsConstructor
public class CodeIntelligenceController {

    private final CodeIntelligenceSnapshotService snapshotService;
    private final CodeIntelligenceQueryService queryService;
    private final CodeIntelligenceShadowService shadowService;
    private final CodeIntelligenceDecisionService decisionService;

    @PostMapping("/snapshots/ensure")
    public ResponseEntity<ApiResponse<SnapshotResponse>> ensureSnapshot(
            @Valid @RequestBody EnsureSnapshotRequest request,
            @AuthenticationPrincipal User user) {
        SnapshotResponse response = snapshotService.ensure(request, user);
        return ResponseEntity.status(response.created() ? HttpStatus.CREATED : HttpStatus.OK)
                .body(ApiResponse.success(response));
    }

    @PostMapping("/tools/search-code")
    public ResponseEntity<ApiResponse<SearchResult>> searchCode(
            @Valid @RequestBody SearchCodeRequest request,
            @AuthenticationPrincipal User user) {
        return ResponseEntity.ok(ApiResponse.success(queryService.search(request, user)));
    }

    @PostMapping("/tools/symbol-context")
    public ResponseEntity<ApiResponse<SymbolContext>> symbolContext(
            @Valid @RequestBody SymbolContextRequest request,
            @AuthenticationPrincipal User user) {
        return ResponseEntity.ok(ApiResponse.success(queryService.symbolContext(request, user)));
    }

    @PostMapping("/tools/dependencies")
    public ResponseEntity<ApiResponse<DependencyGraph>> dependencies(
            @Valid @RequestBody DependencyGraphRequest request,
            @AuthenticationPrincipal User user) {
        return ResponseEntity.ok(ApiResponse.success(queryService.dependencies(request, user)));
    }

    @PostMapping("/tools/change-impact")
    public ResponseEntity<ApiResponse<ChangeImpact>> changeImpact(
            @Valid @RequestBody ChangeImpactRequest request,
            @AuthenticationPrincipal User user) {
        return ResponseEntity.ok(ApiResponse.success(queryService.changeImpact(request, user)));
    }

    @PostMapping("/shadow/search")
    public ResponseEntity<ApiResponse<ShadowComparisonResponse>> shadowSearch(
            @Valid @RequestBody ShadowSearchRequest request,
            @AuthenticationPrincipal User user) {
        return ResponseEntity.ok(ApiResponse.success(shadowService.compareSearch(request, user)));
    }

    @PostMapping("/decisions")
    public ResponseEntity<ApiResponse<DecisionCandidateResponse>> proposeDecision(
            @Valid @RequestBody DecisionCandidateRequest request, @AuthenticationPrincipal User user) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(decisionService.propose(request, user)));
    }

    @PostMapping("/decisions/{id}/review")
    public ResponseEntity<ApiResponse<DecisionCandidateResponse>> reviewDecision(
            @PathVariable Long id, @Valid @RequestBody DecisionReviewRequest request,
            @AuthenticationPrincipal User user) {
        return ResponseEntity.ok(ApiResponse.success(decisionService.review(id, request, user)));
    }
}
