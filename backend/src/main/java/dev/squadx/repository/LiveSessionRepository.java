package dev.squadx.repository;

import dev.squadx.model.LiveSession;
import dev.squadx.model.enums.LiveSessionStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface LiveSessionRepository extends JpaRepository<LiveSession, Long> {

    Optional<LiveSession> findByCode(String code);

    Optional<LiveSession> findByCodeAndStatus(String code, LiveSessionStatus status);

    Optional<LiveSession> findByTaskIdAndStatus(Long taskId, LiveSessionStatus status);

    boolean existsByCode(String code);

    @Query("SELECT s FROM LiveSession s WHERE s.task.id = :taskId AND s.status = 'ACTIVE'")
    Optional<LiveSession> findActiveByTaskId(Long taskId);

    @Query("SELECT s FROM LiveSession s WHERE s.hostUser.id = :userId")
    Page<LiveSession> findByHostUserId(Long userId, Pageable pageable);

    @Query("SELECT s FROM LiveSession s WHERE s.task.project.organization.id = :organizationId")
    Page<LiveSession> findByOrganizationId(Long organizationId, Pageable pageable);

    @Query("SELECT s FROM LiveSession s WHERE s.task.project.organization.id = :organizationId AND s.status = 'ACTIVE'")
    List<LiveSession> findActiveByOrganizationId(Long organizationId);
}
