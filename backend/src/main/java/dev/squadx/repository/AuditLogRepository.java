package dev.squadx.repository;

import dev.squadx.model.AuditLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.Instant;

@Repository
public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {

    Page<AuditLog> findByUserIdOrderByCreatedAtDesc(Long userId, Pageable pageable);

    Page<AuditLog> findByActionOrderByCreatedAtDesc(String action, Pageable pageable);

    Page<AuditLog> findByResourceTypeAndResourceIdOrderByCreatedAtDesc(
            String resourceType, Long resourceId, Pageable pageable);

    @Query("SELECT a FROM AuditLog a WHERE " +
            "(:userId IS NULL OR a.user.id = :userId) AND " +
            "(:action IS NULL OR a.action = :action) AND " +
            "(:resourceType IS NULL OR a.resourceType = :resourceType) AND " +
            "(:from IS NULL OR a.createdAt >= :from) AND " +
            "(:to IS NULL OR a.createdAt <= :to) " +
            "ORDER BY a.createdAt DESC")
    Page<AuditLog> findWithFilters(Long userId, String action, String resourceType,
                                   Instant from, Instant to, Pageable pageable);
}
