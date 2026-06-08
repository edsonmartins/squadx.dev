package dev.squadx.service;

import dev.squadx.dto.common.PageResponse;
import dev.squadx.dto.squad.SquadRequest;
import dev.squadx.dto.squad.SquadResponse;
import dev.squadx.exception.BadRequestException;
import dev.squadx.exception.ForbiddenException;
import dev.squadx.exception.ResourceNotFoundException;
import dev.squadx.model.Agent;
import dev.squadx.model.Organization;
import dev.squadx.model.Squad;
import dev.squadx.model.User;
import dev.squadx.repository.AgentRepository;
import dev.squadx.repository.OrganizationMemberRepository;
import dev.squadx.repository.OrganizationRepository;
import dev.squadx.repository.SquadRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class SquadService {

    private final SquadRepository squadRepository;
    private final OrganizationRepository organizationRepository;
    private final OrganizationMemberRepository memberRepository;
    private final AgentRepository agentRepository;

    @Transactional
    public SquadResponse create(SquadRequest request, User currentUser) {
        Organization org = organizationRepository.findById(request.getOrganizationId())
                .orElseThrow(() -> new ResourceNotFoundException("Organization not found"));

        validateUserAccess(org.getId(), currentUser.getId());

        Squad squad = Squad.builder()
                .name(request.getName())
                .description(request.getDescription())
                .organization(org)
                .build();

        squad = squadRepository.save(squad);

        if (request.getLeaderAgentId() != null) {
            applyLeader(squad, request.getLeaderAgentId());
            squad = squadRepository.save(squad);
        }

        return mapToResponse(squad);
    }

    @Transactional(readOnly = true)
    public SquadResponse getById(Long id, User currentUser) {
        Squad squad = squadRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Squad not found"));

        validateUserAccess(squad.getOrganization().getId(), currentUser.getId());

        return mapToResponse(squad);
    }

    @Transactional(readOnly = true)
    public PageResponse<SquadResponse> getByOrganizationId(Long organizationId, Pageable pageable, User currentUser) {
        validateUserAccess(organizationId, currentUser.getId());

        Page<SquadResponse> page = squadRepository.findActiveByOrganizationId(organizationId, pageable)
                .map(this::mapToResponse);

        return PageResponse.from(page);
    }

    @Transactional
    public SquadResponse update(Long id, SquadRequest request, User currentUser) {
        Squad squad = squadRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Squad not found"));

        validateUserAccess(squad.getOrganization().getId(), currentUser.getId());

        if (request.getName() != null) {
            squad.setName(request.getName());
        }
        if (request.getDescription() != null) {
            squad.setDescription(request.getDescription());
        }
        if (request.getLeaderAgentId() != null) {
            applyLeader(squad, request.getLeaderAgentId());
        }

        squad = squadRepository.save(squad);

        return mapToResponse(squad);
    }

    @Transactional
    public void delete(Long id, User currentUser) {
        Squad squad = squadRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Squad not found"));

        validateUserAccess(squad.getOrganization().getId(), currentUser.getId());

        squadRepository.delete(squad);
    }

    @Transactional
    public SquadResponse toggleActive(Long id, User currentUser) {
        Squad squad = squadRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Squad not found"));

        validateUserAccess(squad.getOrganization().getId(), currentUser.getId());

        squad.setActive(!squad.isActive());
        squad = squadRepository.save(squad);

        return mapToResponse(squad);
    }

    private void applyLeader(Squad squad, Long leaderAgentId) {
        Agent leader = agentRepository.findById(leaderAgentId)
                .orElseThrow(() -> new ResourceNotFoundException("Leader agent not found"));
        if (leader.getSquad() == null || !leader.getSquad().getId().equals(squad.getId())) {
            throw new BadRequestException("Leader agent must belong to this squad");
        }
        squad.setLeaderAgent(leader);
    }

    private void validateUserAccess(Long organizationId, Long userId) {
        if (!memberRepository.existsByOrganizationIdAndUserId(organizationId, userId)) {
            throw new ForbiddenException("User does not have access to this organization");
        }
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
                .leaderAgentId(squad.getLeaderAgent() != null ? squad.getLeaderAgent().getId() : null)
                .leaderAgentName(squad.getLeaderAgent() != null ? squad.getLeaderAgent().getName() : null)
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
