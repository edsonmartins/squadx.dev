package dev.squadx.repository;

import dev.squadx.model.SessionHighlight;
import dev.squadx.model.enums.HighlightType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface SessionHighlightRepository extends JpaRepository<SessionHighlight, Long> {

    List<SessionHighlight> findByRecordingIdOrderByTimestampSecondsAsc(Long recordingId);

    List<SessionHighlight> findByRecordingIdAndHighlightType(Long recordingId, HighlightType highlightType);

    long countByRecordingId(Long recordingId);
}
