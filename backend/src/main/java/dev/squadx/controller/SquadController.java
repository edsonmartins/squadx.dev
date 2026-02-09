package dev.squadx.controller;

import dev.squadx.dto.common.ApiResponse;
import dev.squadx.dto.common.PageResponse;
import dev.squadx.dto.squad.SquadRequest;
import dev.squadx.dto.squad.SquadResponse;
import dev.squadx.model.User;
import dev.squadx.service.SquadService;
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
@RequestMapping("/api/v1/squads")
@RequiredArgsConstructor
@Tag(name = "Squads", description = "Squad management endpoints")
public class SquadController {

    private final SquadService squadService;

    @PostMapping
    @Operation(summary = "Create a new squad")
    public ResponseEntity<ApiResponse<SquadResponse>> create(
            @Valid @RequestBody SquadRequest request,
            @AuthenticationPrincipal User user
    ) {
        SquadResponse response = squadService.create(request, user);
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponse.success(response, "Squad created successfully"));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get squad by ID")
    public ResponseEntity<ApiResponse<SquadResponse>> getById(
            @PathVariable Long id,
            @AuthenticationPrincipal User user
    ) {
        SquadResponse response = squadService.getById(id, user);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @GetMapping("/organization/{organizationId}")
    @Operation(summary = "Get squads by organization ID")
    public ResponseEntity<ApiResponse<PageResponse<SquadResponse>>> getByOrganizationId(
            @PathVariable Long organizationId,
            @PageableDefault(size = 20) Pageable pageable,
            @AuthenticationPrincipal User user
    ) {
        PageResponse<SquadResponse> response = squadService.getByOrganizationId(organizationId, pageable, user);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update a squad")
    public ResponseEntity<ApiResponse<SquadResponse>> update(
            @PathVariable Long id,
            @Valid @RequestBody SquadRequest request,
            @AuthenticationPrincipal User user
    ) {
        SquadResponse response = squadService.update(id, request, user);
        return ResponseEntity.ok(ApiResponse.success(response, "Squad updated successfully"));
    }

    @PatchMapping("/{id}/toggle-active")
    @Operation(summary = "Toggle squad active status")
    public ResponseEntity<ApiResponse<SquadResponse>> toggleActive(
            @PathVariable Long id,
            @AuthenticationPrincipal User user
    ) {
        SquadResponse response = squadService.toggleActive(id, user);
        return ResponseEntity.ok(ApiResponse.success(response, "Squad status updated"));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete a squad")
    public ResponseEntity<ApiResponse<Void>> delete(
            @PathVariable Long id,
            @AuthenticationPrincipal User user
    ) {
        squadService.delete(id, user);
        return ResponseEntity.ok(ApiResponse.success(null, "Squad deleted successfully"));
    }
}
