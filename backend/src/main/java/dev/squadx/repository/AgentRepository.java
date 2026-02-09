package dev.squadx.repository;

import dev.squadx.model.Agent;
import dev.squadx.model.enums.AgentType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AgentRepository extends JpaRepository<Agent, Long> {

    Page<Agent> findBySquadId(Long squadId, Pageable pageable);

    List<Agent> findBySquadIdAndIsActiveTrue(Long squadId);

    List<Agent> findBySquadIdAndAgentType(Long squadId, AgentType agentType);

    @Query("SELECT a FROM Agent a WHERE a.squad.organization.id = :organizationId")
    Page<Agent> findByOrganizationId(Long organizationId, Pageable pageable);

    @Query("SELECT COUNT(a) FROM Agent a WHERE a.squad.id = :squadId")
    long countBySquadId(Long squadId);
}
