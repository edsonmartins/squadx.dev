package dev.squadx.repository;

import dev.squadx.model.CodeIntelligenceSnapshot;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import dev.squadx.model.enums.IntelligenceSnapshotStatus;

public interface CodeIntelligenceSnapshotRepository extends JpaRepository<CodeIntelligenceSnapshot, Long> {
    Optional<CodeIntelligenceSnapshot> findByProjectIdAndRevisionAndProvider(
            Long projectId, String revision, String provider);

    Optional<CodeIntelligenceSnapshot> findFirstByProjectIdAndStatusOrderByIndexedAtDesc(
            Long projectId, IntelligenceSnapshotStatus status);
}
