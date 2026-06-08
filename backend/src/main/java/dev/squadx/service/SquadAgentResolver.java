package dev.squadx.service;

import dev.squadx.model.Agent;
import dev.squadx.model.Squad;
import dev.squadx.repository.AgentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Resolves which agent should handle work assigned to a squad (leader-delegation).
 * Order: online leader → online member → leader (offline) → first active member.
 * Shared by autopilot dispatch and task execution.
 */
@Component
@RequiredArgsConstructor
public class SquadAgentResolver {

    /** An agent is considered online if it sent a heartbeat within this window. */
    private static final long ONLINE_THRESHOLD_SECONDS = 120;

    private final AgentRepository agentRepository;

    public Agent resolve(Squad squad) {
        if (squad == null) {
            return null;
        }
        Agent leader = squad.getLeaderAgent();
        if (isOnline(leader)) {
            return leader;
        }
        List<Agent> agents = agentRepository.findBySquadIdAndIsActiveTrue(squad.getId());
        Agent online = agents.stream().filter(this::isOnline).findFirst().orElse(null);
        if (online != null) {
            return online;
        }
        if (leader != null) {
            return leader;
        }
        return agents.isEmpty() ? null : agents.get(0);
    }

    public boolean isOnline(Agent agent) {
        if (agent == null || !agent.isActive() || agent.getLastHeartbeat() == null) {
            return false;
        }
        if ("DEAD".equals(agent.getLifecycleState())) {
            return false;
        }
        return agent.getLastHeartbeat().isAfter(LocalDateTime.now().minusSeconds(ONLINE_THRESHOLD_SECONDS));
    }
}
