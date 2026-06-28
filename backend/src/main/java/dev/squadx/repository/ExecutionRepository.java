package dev.squadx.repository;

import dev.squadx.model.Execution;
import dev.squadx.model.enums.ExecutionStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface ExecutionRepository extends JpaRepository<Execution, Long> {

    Page<Execution> findByTaskId(Long taskId, Pageable pageable);

    List<Execution> findByTaskIdAndStatus(Long taskId, ExecutionStatus status);

    /** Active runs (PENDING or RUNNING) for a task — used by the admission seam (RFC-0005 §2.2). */
    List<Execution> findByTaskIdAndStatusIn(Long taskId, Collection<ExecutionStatus> statuses);

    /** Dedup lookup for idempotent admission (RFC-0005 §2.1). */
    Optional<Execution> findByTaskIdAndIdempotencyKey(Long taskId, String idempotencyKey);

    /** Pending executions in organizations the user belongs to (limit applied by Pageable). */
    @Query("""
            SELECT e FROM Execution e
            WHERE e.status = :status
              AND EXISTS (SELECT 1 FROM OrganizationMember m
                          WHERE m.organization.id = e.task.project.organization.id
                            AND m.user.id = :userId)
            ORDER BY e.createdAt ASC
            """)
    List<Execution> findPendingForUser(ExecutionStatus status, Long userId, Pageable pageable);

    /** Atomically claim a PENDING execution (PENDING -> RUNNING). Returns rows updated (0 or 1). */
    @Modifying
    @Query("UPDATE Execution e SET e.status = :running, e.startedAt = :now "
            + "WHERE e.id = :id AND e.status = :pending")
    int claimExecution(Long id, ExecutionStatus running, ExecutionStatus pending, Instant now);

    Optional<Execution> findTopByTaskIdOrderByCreatedAtDesc(Long taskId);

    Optional<Execution> findByContainerId(String containerId);

    @Query("SELECT e FROM Execution e WHERE e.task.project.id = :projectId")
    Page<Execution> findByProjectId(Long projectId, Pageable pageable);

    @Query("SELECT e FROM Execution e WHERE e.task.project.organization.id = :organizationId")
    Page<Execution> findByOrganizationId(Long organizationId, Pageable pageable);

    List<Execution> findTop20ByTask_Project_Organization_IdOrderByCreatedAtDesc(Long organizationId);

    List<Execution> findTop20ByTask_Project_IdOrderByCreatedAtDesc(Long projectId);

    @Query("SELECT SUM(e.inputTokens) FROM Execution e WHERE e.task.project.organization.id = :organizationId")
    Long sumInputTokensByOrganizationId(Long organizationId);

    @Query("SELECT SUM(e.outputTokens) FROM Execution e WHERE e.task.project.organization.id = :organizationId")
    Long sumOutputTokensByOrganizationId(Long organizationId);

    @Query("SELECT SUM(e.totalCost) FROM Execution e WHERE e.task.project.organization.id = :organizationId")
    Double sumTotalCostByOrganizationId(Long organizationId);
}
