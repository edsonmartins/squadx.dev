package dev.squadx.controller;

import dev.squadx.dto.common.ApiResponse;
import dev.squadx.dto.cost.CostSummaryResponse;
import dev.squadx.dto.cost.RecordCostRequest;
import dev.squadx.model.CostEvent;
import dev.squadx.service.CostTrackingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/costs")
@RequiredArgsConstructor
@Tag(name = "Cost Tracking", description = "Per-agent, per-model cost tracking endpoints")
public class CostController {

    private final CostTrackingService costTrackingService;

    @PostMapping
    @Operation(summary = "Record a cost event")
    public ResponseEntity<ApiResponse<CostEvent>> recordCost(
            @Valid @RequestBody RecordCostRequest request,
            @RequestParam Long orgId
    ) {
        CostEvent event = costTrackingService.recordCost(request, orgId);
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponse.success(event, "Cost event recorded"));
    }

    @GetMapping("/summary")
    @Operation(summary = "Get cost summary for an organization within a date range")
    public ResponseEntity<ApiResponse<CostSummaryResponse>> getSummary(
            @RequestParam Long orgId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to
    ) {
        CostSummaryResponse summary = costTrackingService.getCostSummary(orgId, from, to);
        return ResponseEntity.ok(ApiResponse.success(summary));
    }

    @GetMapping("/by-agent")
    @Operation(summary = "Get cost breakdown by agent")
    public ResponseEntity<ApiResponse<CostSummaryResponse>> getByAgent(
            @RequestParam Long orgId
    ) {
        CostSummaryResponse breakdown = costTrackingService.getCostByAgent(orgId);
        return ResponseEntity.ok(ApiResponse.success(breakdown));
    }

    @GetMapping("/by-model")
    @Operation(summary = "Get cost breakdown by model")
    public ResponseEntity<ApiResponse<CostSummaryResponse>> getByModel(
            @RequestParam Long orgId
    ) {
        CostSummaryResponse breakdown = costTrackingService.getCostByModel(orgId);
        return ResponseEntity.ok(ApiResponse.success(breakdown));
    }

    @GetMapping("/budget-status")
    @Operation(summary = "Get budget status for an organization")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getBudgetStatus(
            @RequestParam Long orgId
    ) {
        Map<String, Object> status = costTrackingService.getBudgetStatus(orgId);
        return ResponseEntity.ok(ApiResponse.success(status));
    }
}
