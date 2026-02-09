package dev.squadx.controller;

import dev.squadx.dto.agent.AgentRequest;
import dev.squadx.dto.agent.AgentResponse;
import dev.squadx.dto.common.ApiResponse;
import dev.squadx.dto.common.PageResponse;
import dev.squadx.model.User;
import dev.squadx.service.AgentService;
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

import java.util.List;

@RestController
@RequestMapping("/api/v1/agents")
@RequiredArgsConstructor
@Tag(name = "Agents", description = "Agent management endpoints")
public class AgentController {

    private final AgentService agentService;

    @PostMapping
    @Operation(summary = "Create a new agent")
    public ResponseEntity<ApiResponse<AgentResponse>> create(
            @Valid @RequestBody AgentRequest request,
            @AuthenticationPrincipal User user
    ) {
        AgentResponse response = agentService.create(request, user);
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponse.success(response, "Agent created successfully"));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get agent by ID")
    public ResponseEntity<ApiResponse<AgentResponse>> getById(
            @PathVariable Long id,
            @AuthenticationPrincipal User user
    ) {
        AgentResponse response = agentService.getById(id, user);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @GetMapping("/squad/{squadId}")
    @Operation(summary = "Get agents by squad ID")
    public ResponseEntity<ApiResponse<PageResponse<AgentResponse>>> getBySquadId(
            @PathVariable Long squadId,
            @PageableDefault(size = 20) Pageable pageable,
            @AuthenticationPrincipal User user
    ) {
        PageResponse<AgentResponse> response = agentService.getBySquadId(squadId, pageable, user);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @GetMapping("/squad/{squadId}/active")
    @Operation(summary = "Get active agents by squad ID")
    public ResponseEntity<ApiResponse<List<AgentResponse>>> getActiveBySquadId(
            @PathVariable Long squadId,
            @AuthenticationPrincipal User user
    ) {
        List<AgentResponse> response = agentService.getActiveBySquadId(squadId, user);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @GetMapping("/organization/{organizationId}")
    @Operation(summary = "Get agents by organization ID")
    public ResponseEntity<ApiResponse<PageResponse<AgentResponse>>> getByOrganizationId(
            @PathVariable Long organizationId,
            @PageableDefault(size = 20) Pageable pageable,
            @AuthenticationPrincipal User user
    ) {
        PageResponse<AgentResponse> response = agentService.getByOrganizationId(organizationId, pageable, user);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update an agent")
    public ResponseEntity<ApiResponse<AgentResponse>> update(
            @PathVariable Long id,
            @Valid @RequestBody AgentRequest request,
            @AuthenticationPrincipal User user
    ) {
        AgentResponse response = agentService.update(id, request, user);
        return ResponseEntity.ok(ApiResponse.success(response, "Agent updated successfully"));
    }

    @PatchMapping("/{id}/toggle-active")
    @Operation(summary = "Toggle agent active status")
    public ResponseEntity<ApiResponse<AgentResponse>> toggleActive(
            @PathVariable Long id,
            @AuthenticationPrincipal User user
    ) {
        AgentResponse response = agentService.toggleActive(id, user);
        return ResponseEntity.ok(ApiResponse.success(response, "Agent status updated"));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete an agent")
    public ResponseEntity<ApiResponse<Void>> delete(
            @PathVariable Long id,
            @AuthenticationPrincipal User user
    ) {
        agentService.delete(id, user);
        return ResponseEntity.ok(ApiResponse.success(null, "Agent deleted successfully"));
    }
}
