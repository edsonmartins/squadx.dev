package dev.squadx.controlpanel.controller;

import dev.squadx.controlpanel.dto.pass5.Pass5StatusResponse;
import dev.squadx.controlpanel.service.Pass5Service;
import dev.squadx.dto.common.ApiResponse;
import dev.squadx.model.User;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/spec-tasks/{id}/pass5")
@RequiredArgsConstructor
@Tag(name = "Control Panel — Pass 5", description = "Conformance gate: coverage + outcome")
public class Pass5Controller {

    private final Pass5Service pass5Service;

    @GetMapping
    @Operation(summary = "Pass 5 status + scenario coverage map for a task")
    public ResponseEntity<ApiResponse<Pass5StatusResponse>> status(
            @PathVariable Long id,
            @AuthenticationPrincipal User user
    ) {
        return ResponseEntity.ok(ApiResponse.success(pass5Service.getStatus(id, user)));
    }

    @PostMapping("/run")
    @Operation(summary = "Re-run Pass 5 for a task on demand")
    public ResponseEntity<ApiResponse<Pass5StatusResponse>> run(
            @PathVariable Long id,
            @AuthenticationPrincipal User user
    ) {
        return ResponseEntity.ok(ApiResponse.success(pass5Service.runOnDemand(id, user), "Pass 5 executed"));
    }
}
