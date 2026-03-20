package dev.squadx.service;

import dev.squadx.dto.agent.AgentRequest;
import dev.squadx.dto.agent.AgentResponse;
import dev.squadx.dto.common.PageResponse;
import dev.squadx.exception.ForbiddenException;
import dev.squadx.exception.ResourceNotFoundException;
import dev.squadx.model.Agent;
import dev.squadx.model.Squad;
import dev.squadx.model.User;
import dev.squadx.repository.AgentRepository;
import dev.squadx.repository.OrganizationMemberRepository;
import dev.squadx.repository.SquadRepository;
import dev.squadx.repository.TaskRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AgentService {

    private final AgentRepository agentRepository;
    private final SquadRepository squadRepository;
    private final OrganizationMemberRepository memberRepository;
    private final TaskRepository taskRepository;

    @Transactional
    public AgentResponse create(AgentRequest request, User currentUser) {
        Squad squad = squadRepository.findById(request.getSquadId())
                .orElseThrow(() -> new ResourceNotFoundException("Squad not found"));

        validateUserAccess(squad.getOrganization().getId(), currentUser.getId());

        Agent agent = Agent.builder()
                .name(request.getName())
                .agentType(request.getAgentType())
                .description(request.getDescription())
                .modelId(request.getModelId() != null ? request.getModelId() : "gpt-4o")
                .systemPrompt(request.getSystemPrompt())
                .maxTokens(request.getMaxTokens() != null ? request.getMaxTokens() : 4096)
                .temperature(request.getTemperature() != null ? request.getTemperature() : 0.7)
                .squad(squad)
                .capabilities(request.getCapabilities())
                .build();

        agent = agentRepository.save(agent);

        return mapToResponse(agent);
    }

    public AgentResponse getById(Long id, User currentUser) {
        Agent agent = agentRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Agent not found"));

        validateUserAccess(agent.getSquad().getOrganization().getId(), currentUser.getId());

        return mapToResponse(agent);
    }

    public PageResponse<AgentResponse> getBySquadId(Long squadId, Pageable pageable, User currentUser) {
        Squad squad = squadRepository.findById(squadId)
                .orElseThrow(() -> new ResourceNotFoundException("Squad not found"));

        validateUserAccess(squad.getOrganization().getId(), currentUser.getId());

        Page<AgentResponse> page = agentRepository.findBySquadId(squadId, pageable)
                .map(this::mapToResponse);

        return PageResponse.from(page);
    }

    public List<AgentResponse> getActiveBySquadId(Long squadId, User currentUser) {
        Squad squad = squadRepository.findById(squadId)
                .orElseThrow(() -> new ResourceNotFoundException("Squad not found"));

        validateUserAccess(squad.getOrganization().getId(), currentUser.getId());

        return agentRepository.findBySquadIdAndIsActiveTrue(squadId).stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    public PageResponse<AgentResponse> getByOrganizationId(Long organizationId, Pageable pageable, User currentUser) {
        validateUserAccess(organizationId, currentUser.getId());

        Page<AgentResponse> page = agentRepository.findByOrganizationId(organizationId, pageable)
                .map(this::mapToResponse);

        return PageResponse.from(page);
    }

    @Transactional
    public AgentResponse update(Long id, AgentRequest request, User currentUser) {
        Agent agent = agentRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Agent not found"));

        validateUserAccess(agent.getSquad().getOrganization().getId(), currentUser.getId());

        if (request.getName() != null) {
            agent.setName(request.getName());
        }
        if (request.getDescription() != null) {
            agent.setDescription(request.getDescription());
        }
        if (request.getModelId() != null) {
            agent.setModelId(request.getModelId());
        }
        if (request.getSystemPrompt() != null) {
            agent.setSystemPrompt(request.getSystemPrompt());
        }
        if (request.getMaxTokens() != null) {
            agent.setMaxTokens(request.getMaxTokens());
        }
        if (request.getTemperature() != null) {
            agent.setTemperature(request.getTemperature());
        }
        if (request.getCapabilities() != null) {
            agent.setCapabilities(request.getCapabilities());
        }

        agent = agentRepository.save(agent);

        return mapToResponse(agent);
    }

    @Transactional
    public void delete(Long id, User currentUser) {
        Agent agent = agentRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Agent not found"));

        validateUserAccess(agent.getSquad().getOrganization().getId(), currentUser.getId());

        agentRepository.delete(agent);
    }

    @Transactional
    public AgentResponse toggleActive(Long id, User currentUser) {
        Agent agent = agentRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Agent not found"));

        validateUserAccess(agent.getSquad().getOrganization().getId(), currentUser.getId());

        agent.setActive(!agent.isActive());
        agent = agentRepository.save(agent);

        return mapToResponse(agent);
    }

    // --- Agent Lifecycle ---

    @Transactional
    public void heartbeat(Long agentId) {
        Agent agent = agentRepository.findById(agentId)
                .orElseThrow(() -> new ResourceNotFoundException("Agent not found"));
        agent.setLastHeartbeat(LocalDateTime.now());
        agent.setLifecycleState("WORKING");
        agentRepository.save(agent);
    }

    @Transactional
    public void updateLifecycleState(Long agentId, String state) {
        Agent agent = agentRepository.findById(agentId)
                .orElseThrow(() -> new ResourceNotFoundException("Agent not found"));
        agent.setLifecycleState(state);
        if ("WORKING".equals(state) || "IDLE".equals(state)) {
            agent.setLastHeartbeat(LocalDateTime.now());
        }
        agentRepository.save(agent);
    }

    public List<AgentResponse> detectDeadAgents(int timeoutSeconds) {
        LocalDateTime threshold = LocalDateTime.now().minusSeconds(timeoutSeconds);
        return agentRepository.findAll().stream()
                .filter(a -> a.isActive()
                        && a.getLastHeartbeat() != null
                        && a.getLastHeartbeat().isBefore(threshold)
                        && !"DEAD".equals(a.getLifecycleState()))
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    @Transactional
    public void recoverTasksFromDeadAgent(Long agentId) {
        Agent agent = agentRepository.findById(agentId)
                .orElseThrow(() -> new ResourceNotFoundException("Agent not found"));
        agent.setLifecycleState("DEAD");
        agent.setActive(false);
        agentRepository.save(agent);

        // Reset in-progress tasks assigned to this agent back to pending
        taskRepository.findByAgentId(agentId).stream()
                .filter(t -> "IN_PROGRESS".equals(t.getStatus().name()))
                .forEach(t -> {
                    t.setStatus(dev.squadx.model.enums.TaskStatus.TODO);
                    t.setAgent(null);
                    taskRepository.save(t);
                });
    }

    @Scheduled(fixedRate = 60000) // Every 60 seconds
    @Transactional
    public void checkDeadAgents() {
        List<AgentResponse> dead = detectDeadAgents(120); // 2 minutes without heartbeat
        for (AgentResponse agent : dead) {
            recoverTasksFromDeadAgent(agent.getId());
        }
    }

    private void validateUserAccess(Long organizationId, Long userId) {
        if (!memberRepository.existsByOrganizationIdAndUserId(organizationId, userId)) {
            throw new ForbiddenException("User does not have access to this organization");
        }
    }

    private AgentResponse mapToResponse(Agent agent) {
        return AgentResponse.builder()
                .id(agent.getId())
                .name(agent.getName())
                .agentType(agent.getAgentType())
                .description(agent.getDescription())
                .modelId(agent.getModelId())
                .systemPrompt(agent.getSystemPrompt())
                .maxTokens(agent.getMaxTokens())
                .temperature(agent.getTemperature())
                .isActive(agent.isActive())
                .squadId(agent.getSquad().getId())
                .squadName(agent.getSquad().getName())
                .capabilities(agent.getCapabilities())
                .executionsCount(agent.getExecutions() != null ? agent.getExecutions().size() : 0)
                .createdAt(agent.getCreatedAt())
                .updatedAt(agent.getUpdatedAt())
                .build();
    }
}
