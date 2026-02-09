package dev.squadx.repository;

import dev.squadx.model.LiveSessionParticipant;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface LiveSessionParticipantRepository extends JpaRepository<LiveSessionParticipant, Long> {

    List<LiveSessionParticipant> findBySessionId(Long sessionId);

    List<LiveSessionParticipant> findBySessionIdAndLeftAtIsNull(Long sessionId);

    Optional<LiveSessionParticipant> findBySessionIdAndUserIdAndLeftAtIsNull(Long sessionId, Long userId);

    long countBySessionIdAndLeftAtIsNull(Long sessionId);

    boolean existsBySessionIdAndUserId(Long sessionId, Long userId);
}
