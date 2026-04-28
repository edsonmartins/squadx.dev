package dev.squadx.repository;

import dev.squadx.model.Execution;
import dev.squadx.model.enums.ExecutionStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ExecutionRepository extends JpaRepository<Execution, Long> {

    Page<Execution> findByTaskId(Long taskId, Pageable pageable);

    List<Execution> findByTaskIdAndStatus(Long taskId, ExecutionStatus status);

    Optional<Execution> findTopByTaskIdOrderByCreatedAtDesc(Long taskId);

    Optional<Execution> findByContainerId(String containerId);

    @Query("SELECT e FROM Execution e WHERE e.task.project.id = :projectId")
    Page<Execution> findByProjectId(Long projectId, Pageable pageable);

    @Query("SELECT e FROM Execution e WHERE e.task.project.organization.id = :organizationId")
    Page<Execution> findByOrganizationId(Long organizationId, Pageable pageable);

    @Query("SELECT SUM(e.inputTokens) FROM Execution e WHERE e.task.project.organization.id = :organizationId")
    Long sumInputTokensByOrganizationId(Long organizationId);

    @Query("SELECT SUM(e.outputTokens) FROM Execution e WHERE e.task.project.organization.id = :organizationId")
    Long sumOutputTokensByOrganizationId(Long organizationId);

    @Query("SELECT SUM(e.totalCost) FROM Execution e WHERE e.task.project.organization.id = :organizationId")
    Double sumTotalCostByOrganizationId(Long organizationId);
}
