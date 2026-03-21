package dev.squadx.controller;

import dev.squadx.dto.agent.AgentMessageRequest;
import dev.squadx.dto.agent.AgentMessageResponse;
import dev.squadx.dto.common.ApiResponse;
import dev.squadx.exception.ForbiddenException;
import dev.squadx.exception.ResourceNotFoundException;
import dev.squadx.model.Agent;
import dev.squadx.model.Execution;
import dev.squadx.model.User;
import dev.squadx.repository.AgentRepository;
import dev.squadx.repository.ExecutionRepository;
import dev.squadx.repository.OrganizationMemberRepository;
import dev.squadx.service.AgentMessageService;
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
@RequestMapping("/api/v1/agent-messages")
@RequiredArgsConstructor
@Tag(name = "Agent Messages", description = "Inter-agent messaging endpoints")
public class AgentMessageController {

    private final AgentMessageService agentMessageService;
    private final AgentRepository agentRepository;
    private final ExecutionRepository executionRepository;
    private final OrganizationMemberRepository memberRepository;

    @PostMapping
    @Operation(summary = "Send a message between agents")
    public ResponseEntity<ApiResponse<AgentMessageResponse>> send(
            @Valid @RequestBody AgentMessageRequest request,
            @AuthenticationPrincipal User user
    ) {
        AgentMessageResponse response = agentMessageService.send(request, user);
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponse.success(response, "Message sent successfully"));
    }

    @PostMapping("/broadcast")
    @Operation(summary = "Broadcast a message to all agents in the execution's squad")
    public ResponseEntity<ApiResponse<List<AgentMessageResponse>>> broadcast(
            @Valid @RequestBody AgentMessageRequest request,
            @AuthenticationPrincipal User user
    ) {
        List<AgentMessageResponse> responses = agentMessageService.broadcast(
                request.getFromAgentId(),
                request.getExecutionId(),
                request.getContent()
        );
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponse.success(responses, "Broadcast sent successfully"));
    }

    @GetMapping("/unread")
    @Operation(summary = "Get unread messages for an agent")
    public ResponseEntity<ApiResponse<List<AgentMessageResponse>>> getUnread(
            @RequestParam Long agentId,
            @AuthenticationPrincipal User user
    ) {
        validateAgentAccess(agentId, user);
        List<AgentMessageResponse> responses = agentMessageService.getUnread(agentId);
        return ResponseEntity.ok(ApiResponse.success(responses));
    }

    @PutMapping("/{id}/read")
    @Operation(summary = "Mark a message as read")
    public ResponseEntity<ApiResponse<Void>> markRead(
            @PathVariable Long id
    ) {
        agentMessageService.markRead(id);
        return ResponseEntity.ok(ApiResponse.success(null, "Message marked as read"));
    }

    @PutMapping("/read-all")
    @Operation(summary = "Mark all messages as read for an agent")
    public ResponseEntity<ApiResponse<Void>> markAllRead(
            @RequestParam Long agentId,
            @AuthenticationPrincipal User user
    ) {
        validateAgentAccess(agentId, user);
        agentMessageService.markAllRead(agentId);
        return ResponseEntity.ok(ApiResponse.success(null, "All messages marked as read"));
    }

    @GetMapping("/execution/{executionId}")
    @Operation(summary = "Get message history for an execution")
    public ResponseEntity<ApiResponse<List<AgentMessageResponse>>> getByExecution(
            @PathVariable Long executionId,
            @AuthenticationPrincipal User user
    ) {
        validateExecutionAccess(executionId, user);
        List<AgentMessageResponse> responses = agentMessageService.getByExecution(executionId);
        return ResponseEntity.ok(ApiResponse.success(responses));
    }

    private void validateAgentAccess(Long agentId, User user) {
        Agent agent = agentRepository.findById(agentId)
                .orElseThrow(() -> new ResourceNotFoundException("Agent not found"));
        Long orgId = agent.getSquad().getOrganization().getId();
        if (!memberRepository.existsByOrganizationIdAndUserId(orgId, user.getId())) {
            throw new ForbiddenException("User does not have access to this agent's organization");
        }
    }

    private void validateExecutionAccess(Long executionId, User user) {
        Execution execution = executionRepository.findById(executionId)
                .orElseThrow(() -> new ResourceNotFoundException("Execution not found"));
        Long orgId = execution.getAgent().getSquad().getOrganization().getId();
        if (!memberRepository.existsByOrganizationIdAndUserId(orgId, user.getId())) {
            throw new ForbiddenException("User does not have access to this execution's organization");
        }
    }
}
