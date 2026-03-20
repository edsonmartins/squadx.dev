package dev.squadx.repository;

import dev.squadx.model.AgentMessage;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AgentMessageRepository extends JpaRepository<AgentMessage, Long> {

    List<AgentMessage> findByToAgentIdAndIsReadFalseOrderByCreatedAtAsc(Long agentId);

    List<AgentMessage> findByExecutionIdOrderByCreatedAtAsc(Long executionId);

    Page<AgentMessage> findByToAgentIdOrderByCreatedAtDesc(Long agentId, Pageable pageable);

    long countByToAgentIdAndIsReadFalse(Long agentId);

    @Modifying
    @Query("UPDATE AgentMessage m SET m.isRead = true WHERE m.toAgent.id = :agentId AND m.isRead = false")
    int markAllReadByAgentId(Long agentId);
}
