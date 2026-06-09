package dev.squadx.controlpanel.controller;

import dev.squadx.controlpanel.dto.harness.HarnessRequest;
import dev.squadx.controlpanel.dto.harness.HarnessResponse;
import dev.squadx.controlpanel.dto.harness.SelectModelRequest;
import dev.squadx.controlpanel.service.HarnessService;
import dev.squadx.dto.common.ApiResponse;
import dev.squadx.model.User;
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
@RequestMapping("/api/v1/harnesses")
@RequiredArgsConstructor
@Tag(name = "Control Panel — Harnesses", description = "Harness registry + LLM model selection")
public class HarnessController {

    private final HarnessService harnessService;

    @PostMapping
    @Operation(summary = "Register a harness")
    public ResponseEntity<ApiResponse<HarnessResponse>> register(
            @Valid @RequestBody HarnessRequest request,
            @AuthenticationPrincipal User user
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(harnessService.register(request, user), "Harness registered"));
    }

    @GetMapping
    @Operation(summary = "List harnesses of an organization")
    public ResponseEntity<ApiResponse<List<HarnessResponse>>> list(
            @RequestParam Long organizationId,
            @AuthenticationPrincipal User user
    ) {
        return ResponseEntity.ok(ApiResponse.success(harnessService.list(organizationId, user)));
    }

    @PatchMapping("/{id}/model")
    @Operation(summary = "Select the LLM model for a harness")
    public ResponseEntity<ApiResponse<HarnessResponse>> selectModel(
            @PathVariable Long id,
            @Valid @RequestBody SelectModelRequest request,
            @AuthenticationPrincipal User user
    ) {
        return ResponseEntity.ok(
                ApiResponse.success(harnessService.selectModel(id, request.getModel(), user), "Model selected"));
    }
}
