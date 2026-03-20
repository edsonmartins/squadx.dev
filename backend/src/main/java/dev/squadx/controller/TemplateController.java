package dev.squadx.controller;

import dev.squadx.dto.common.ApiResponse;
import dev.squadx.dto.squad.SquadResponse;
import dev.squadx.dto.template.ApplyTemplateRequest;
import dev.squadx.dto.template.TemplateResponse;
import dev.squadx.model.Squad;
import dev.squadx.model.User;
import dev.squadx.service.TeamTemplateService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1/templates")
@RequiredArgsConstructor
@Tag(name = "Templates", description = "Pre-built team template endpoints")
public class TemplateController {

    private final TeamTemplateService teamTemplateService;

    @GetMapping
    @Operation(summary = "List all available team templates")
    public ResponseEntity<ApiResponse<List<TemplateResponse>>> listTemplates() {
        List<TemplateResponse> templates = teamTemplateService.listTemplates();
        return ResponseEntity.ok(ApiResponse.success(templates));
    }

    @GetMapping("/{name}")
    @Operation(summary = "Get a team template by name")
    public ResponseEntity<ApiResponse<TemplateResponse>> getTemplate(@PathVariable String name) {
        TemplateResponse template = teamTemplateService.getTemplate(name);
        return ResponseEntity.ok(ApiResponse.success(template));
    }

    @PostMapping("/{name}/apply")
    @Operation(summary = "Apply a team template to create a squad with agents")
    public ResponseEntity<ApiResponse<SquadResponse>> applyTemplate(
            @PathVariable String name,
            @Valid @RequestBody ApplyTemplateRequest request,
            @AuthenticationPrincipal User user
    ) {
        Squad squad = teamTemplateService.applyTemplate(name, request, user);
        SquadResponse response = mapToResponse(squad);
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponse.success(response, "Template applied successfully"));
    }

    private SquadResponse mapToResponse(Squad squad) {
        return SquadResponse.builder()
                .id(squad.getId())
                .name(squad.getName())
                .description(squad.getDescription())
                .isActive(squad.isActive())
                .organizationId(squad.getOrganization().getId())
                .organizationName(squad.getOrganization().getName())
                .agentsCount(squad.getAgents() != null ? squad.getAgents().size() : 0)
                .projectsCount(squad.getProjects() != null ? squad.getProjects().size() : 0)
                .agents(squad.getAgents() != null ? squad.getAgents().stream()
                        .map(agent -> SquadResponse.AgentSummary.builder()
                                .id(agent.getId())
                                .name(agent.getName())
                                .agentType(agent.getAgentType().name())
                                .isActive(agent.isActive())
                                .build())
                        .collect(Collectors.toList()) : null)
                .createdAt(squad.getCreatedAt())
                .updatedAt(squad.getUpdatedAt())
                .build();
    }
}
