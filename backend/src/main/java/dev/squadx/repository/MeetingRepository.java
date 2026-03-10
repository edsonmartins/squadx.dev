package dev.squadx.repository;

import dev.squadx.model.Meeting;
import dev.squadx.model.enums.MeetingStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;

@Repository
public interface MeetingRepository extends JpaRepository<Meeting, Long> {

    Page<Meeting> findByOrganizationId(Long organizationId, Pageable pageable);

    @Query("SELECT m FROM Meeting m WHERE m.organization.id = :orgId AND m.scheduledAt >= :from AND m.scheduledAt <= :to ORDER BY m.scheduledAt")
    List<Meeting> findByOrganizationAndDateRange(Long orgId, Instant from, Instant to);

    @Query("SELECT m FROM Meeting m JOIN m.attendees a WHERE a.user.id = :userId AND m.scheduledAt >= :from ORDER BY m.scheduledAt")
    List<Meeting> findUpcomingForUser(Long userId, Instant from);

    List<Meeting> findByOrganizationIdAndStatus(Long organizationId, MeetingStatus status);
}
