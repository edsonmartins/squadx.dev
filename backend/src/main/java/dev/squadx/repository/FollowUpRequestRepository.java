package dev.squadx.repository;

import dev.squadx.model.FollowUpRequest;
import dev.squadx.model.enums.FollowUpStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface FollowUpRequestRepository extends JpaRepository<FollowUpRequest, Long> {

    Page<FollowUpRequest> findByTaskIdAndStatus(Long taskId, FollowUpStatus status, Pageable pageable);

    Optional<FollowUpRequest> findFirstByTaskIdAndStatusOrderByCreatedAtAsc(Long taskId, FollowUpStatus status);
}
