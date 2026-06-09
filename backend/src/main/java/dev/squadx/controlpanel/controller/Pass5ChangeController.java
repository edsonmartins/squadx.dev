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

import java.util.List;

/** Pass 5 de uma mudança inteira (batch), para a tela de validação evitar N requisições. */
@RestController
@RequestMapping("/api/v1/changes/{changeId}/pass5")
@RequiredArgsConstructor
@Tag(name = "Control Panel — Pass 5", description = "Pass 5 status for all tasks of a change")
public class Pass5ChangeController {

    private final Pass5Service pass5Service;

    @GetMapping
    @Operation(summary = "Pass 5 status of every task in a change")
    public ResponseEntity<ApiResponse<List<Pass5StatusResponse>>> statuses(
            @PathVariable Long changeId,
            @AuthenticationPrincipal User user
    ) {
        return ResponseEntity.ok(ApiResponse.success(pass5Service.getStatusesForChange(changeId, user)));
    }
}
