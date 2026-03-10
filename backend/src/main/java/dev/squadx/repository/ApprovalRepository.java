package dev.squadx.repository;

import dev.squadx.model.Approval;
import dev.squadx.model.enums.ApprovalStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ApprovalRepository extends JpaRepository<Approval, Long> {

    Page<Approval> findByTaskId(Long taskId, Pageable pageable);

    Page<Approval> findByRequestedById(Long userId, Pageable pageable);

    Page<Approval> findByReviewerId(Long reviewerId, Pageable pageable);

    Page<Approval> findByStatus(ApprovalStatus status, Pageable pageable);

    List<Approval> findByTaskIdAndStatus(Long taskId, ApprovalStatus status);

    @Query("SELECT a FROM Approval a WHERE a.status = 'PENDING' AND " +
           "(a.reviewer.id = :userId OR a.task.project.organization.id IN " +
           "(SELECT om.organization.id FROM OrganizationMember om WHERE om.user.id = :userId))")
    Page<Approval> findPendingForUser(Long userId, Pageable pageable);

    long countByStatus(ApprovalStatus status);
}
