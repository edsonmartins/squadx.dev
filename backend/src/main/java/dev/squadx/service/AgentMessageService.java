package dev.squadx.service;

import dev.squadx.dto.agent.AgentMessageRequest;
import dev.squadx.dto.agent.AgentMessageResponse;
import dev.squadx.exception.ResourceNotFoundException;
import dev.squadx.model.Agent;
import dev.squadx.model.AgentMessage;
import dev.squadx.model.Execution;
import dev.squadx.model.User;
import dev.squadx.model.enums.AgentMessageType;
import dev.squadx.repository.AgentMessageRepository;
import dev.squadx.repository.AgentRepository;
import dev.squadx.repository.ExecutionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class AgentMessageService {

    private final AgentMessageRepository messageRepository;
    private final AgentRepository agentRepository;
    private final ExecutionRepository executionRepository;
    private final WebSocketEventService webSocketEventService;

    @Transactional
    public AgentMessageResponse send(AgentMessageRequest request, User user) {
        Agent fromAgent = agentRepository.findById(request.getFromAgentId())
                .orElseThrow(() -> new ResourceNotFoundException("Source agent not found"));

        Agent toAgent = null;
        if (request.getToAgentId() != null) {
            toAgent = agentRepository.findById(request.getToAgentId())
                    .orElseThrow(() -> new ResourceNotFoundException("Target agent not found"));
        }

        Execution execution = null;
        if (request.getExecutionId() != null) {
            execution = executionRepository.findById(request.getExecutionId())
                    .orElseThrow(() -> new ResourceNotFoundException("Execution not found"));
        }

        AgentMessageType messageType = AgentMessageType.TASK_UPDATE;
        if (request.getMessageType() != null) {
            try {
                messageType = AgentMessageType.valueOf(request.getMessageType());
            } catch (IllegalArgumentException e) {
                log.warn("Unknown message type '{}', defaulting to TASK_UPDATE", request.getMessageType());
            }
        }

        AgentMessage message = AgentMessage.builder()
                .fromAgent(fromAgent)
                .toAgent(toAgent)
                .execution(execution)
                .messageType(messageType)
                .content(request.getContent())
                .isBroadcast(toAgent == null)
                .build();

        message = messageRepository.save(message);

        AgentMessageResponse response = mapToResponse(message);

        if (toAgent != null) {
            webSocketEventService.sendOrganizationEvent(
                    fromAgent.getSquad().getOrganization().getId(),
                    "agent_message",
                    response
            );
        }

        log.info("Agent message sent from {} to {}", fromAgent.getName(),
                toAgent != null ? toAgent.getName() : "broadcast");

        return response;
    }

    @Transactional
    public List<AgentMessageResponse> broadcast(Long fromAgentId, Long executionId, String content) {
        Agent fromAgent = agentRepository.findById(fromAgentId)
                .orElseThrow(() -> new ResourceNotFoundException("Source agent not found"));

        Execution execution = executionRepository.findById(executionId)
                .orElseThrow(() -> new ResourceNotFoundException("Execution not found"));

        Long squadId = execution.getAgent().getSquad().getId();
        List<Agent> squadAgents = agentRepository.findBySquadIdAndIsActiveTrue(squadId);

        List<AgentMessageResponse> responses = squadAgents.stream()
                .filter(agent -> !agent.getId().equals(fromAgentId))
                .map(agent -> {
                    AgentMessage message = AgentMessage.builder()
                            .fromAgent(fromAgent)
                            .toAgent(agent)
                            .execution(execution)
                            .messageType(AgentMessageType.BROADCAST)
                            .content(content)
                            .isBroadcast(true)
                            .build();
                    message = messageRepository.save(message);
                    return mapToResponse(message);
                })
                .collect(Collectors.toList());

        webSocketEventService.sendOrganizationEvent(
                fromAgent.getSquad().getOrganization().getId(),
                "agent_broadcast",
                responses
        );

        log.info("Agent {} broadcast message to {} agents in squad {}",
                fromAgent.getName(), responses.size(), squadId);

        return responses;
    }

    public List<AgentMessageResponse> getUnread(Long agentId) {
        return messageRepository.findByToAgentIdAndIsReadFalseOrderByCreatedAtAsc(agentId)
                .stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    @Transactional
    public void markRead(Long messageId) {
        AgentMessage message = messageRepository.findById(messageId)
                .orElseThrow(() -> new ResourceNotFoundException("Message not found"));
        message.setRead(true);
        messageRepository.save(message);
    }

    @Transactional
    public void markAllRead(Long agentId) {
        messageRepository.markAllReadByAgentId(agentId);
    }

    public List<AgentMessageResponse> getByExecution(Long executionId) {
        return messageRepository.findByExecutionIdOrderByCreatedAtAsc(executionId)
                .stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    private AgentMessageResponse mapToResponse(AgentMessage message) {
        return AgentMessageResponse.builder()
                .id(message.getId())
                .fromAgentId(message.getFromAgent().getId())
                .fromAgentName(message.getFromAgent().getName())
                .toAgentId(message.getToAgent() != null ? message.getToAgent().getId() : null)
                .toAgentName(message.getToAgent() != null ? message.getToAgent().getName() : null)
                .executionId(message.getExecution() != null ? message.getExecution().getId() : null)
                .messageType(message.getMessageType().name())
                .content(message.getContent())
                .isRead(message.isRead())
                .isBroadcast(message.isBroadcast())
                .createdAt(message.getCreatedAt())
                .build();
    }
}
