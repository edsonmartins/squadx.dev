package dev.squadx.repository;

import dev.squadx.model.SessionRecording;
import dev.squadx.model.enums.RecordingStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface SessionRecordingRepository extends JpaRepository<SessionRecording, Long> {

    List<SessionRecording> findBySessionIdOrderByStartedAtDesc(Long sessionId);

    List<SessionRecording> findBySessionIdAndStatus(Long sessionId, RecordingStatus status);
}
