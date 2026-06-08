package dev.squadx.controller;

import dev.squadx.dto.autopilot.AutopilotRequest;
import dev.squadx.dto.autopilot.AutopilotResponse;
import dev.squadx.dto.autopilot.AutopilotRunResponse;
import dev.squadx.dto.common.ApiResponse;
import dev.squadx.dto.common.PageResponse;
import dev.squadx.model.User;
import dev.squadx.service.AutopilotService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/autopilots")
@RequiredArgsConstructor
@Tag(name = "Autopilots", description = "Scheduled/recurring task automation")
public class AutopilotController {

    private final AutopilotService autopilotService;

    @PostMapping
    @Operation(summary = "Create a new autopilot")
    public ResponseEntity<ApiResponse<AutopilotResponse>> create(
            @Valid @RequestBody AutopilotRequest request,
            @AuthenticationPrincipal User user
    ) {
        AutopilotResponse response = autopilotService.create(request, user);
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponse.success(response, "Autopilot created successfully"));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get autopilot by ID")
    public ResponseEntity<ApiResponse<AutopilotResponse>> getById(
            @PathVariable Long id,
            @AuthenticationPrincipal User user
    ) {
        return ResponseEntity.ok(ApiResponse.success(autopilotService.getById(id, user)));
    }

    @GetMapping("/organization/{organizationId}")
    @Operation(summary = "List autopilots for an organization")
    public ResponseEntity<ApiResponse<PageResponse<AutopilotResponse>>> getByOrganizationId(
            @PathVariable Long organizationId,
            @PageableDefault(size = 20) Pageable pageable,
            @AuthenticationPrincipal User user
    ) {
        PageResponse<AutopilotResponse> response =
                autopilotService.getByOrganizationId(organizationId, pageable, user);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update an autopilot")
    public ResponseEntity<ApiResponse<AutopilotResponse>> update(
            @PathVariable Long id,
            @Valid @RequestBody AutopilotRequest request,
            @AuthenticationPrincipal User user
    ) {
        return ResponseEntity.ok(
                ApiResponse.success(autopilotService.update(id, request, user), "Autopilot updated successfully"));
    }

    @PatchMapping("/{id}/toggle")
    @Operation(summary = "Enable/disable an autopilot")
    public ResponseEntity<ApiResponse<AutopilotResponse>> toggle(
            @PathVariable Long id,
            @AuthenticationPrincipal User user
    ) {
        return ResponseEntity.ok(
                ApiResponse.success(autopilotService.toggle(id, user), "Autopilot toggled"));
    }

    @PostMapping("/{id}/run")
    @Operation(summary = "Trigger an autopilot now")
    public ResponseEntity<ApiResponse<AutopilotRunResponse>> runNow(
            @PathVariable Long id,
            @AuthenticationPrincipal User user
    ) {
        return ResponseEntity.ok(
                ApiResponse.success(autopilotService.runNow(id, user), "Autopilot triggered"));
    }

    @GetMapping("/{id}/runs")
    @Operation(summary = "List run history for an autopilot")
    public ResponseEntity<ApiResponse<PageResponse<AutopilotRunResponse>>> getRuns(
            @PathVariable Long id,
            @PageableDefault(size = 20) Pageable pageable,
            @AuthenticationPrincipal User user
    ) {
        return ResponseEntity.ok(ApiResponse.success(autopilotService.getRuns(id, pageable, user)));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete an autopilot")
    public ResponseEntity<ApiResponse<Void>> delete(
            @PathVariable Long id,
            @AuthenticationPrincipal User user
    ) {
        autopilotService.delete(id, user);
        return ResponseEntity.ok(ApiResponse.success(null, "Autopilot deleted successfully"));
    }
}
