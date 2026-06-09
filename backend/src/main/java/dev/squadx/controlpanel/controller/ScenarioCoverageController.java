package dev.squadx.controlpanel.controller;

import dev.squadx.controlpanel.dto.pass5.CoverageRequest;
import dev.squadx.controlpanel.service.CoverageService;
import dev.squadx.dto.common.ApiResponse;
import dev.squadx.model.User;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/scenarios")
@RequiredArgsConstructor
@Tag(name = "Control Panel — Coverage", description = "Scenario↔test coverage marking")
public class ScenarioCoverageController {

    private final CoverageService coverageService;

    @PatchMapping("/{id}/coverage")
    @Operation(summary = "Mark a scenario as covered (or not) by a passing test")
    public ResponseEntity<ApiResponse<Void>> setCovered(
            @PathVariable Long id,
            @Valid @RequestBody CoverageRequest request,
            @AuthenticationPrincipal User user
    ) {
        coverageService.setCovered(id, request.getCovered(), user);
        return ResponseEntity.ok(ApiResponse.success(null, "Coverage updated"));
    }
}
