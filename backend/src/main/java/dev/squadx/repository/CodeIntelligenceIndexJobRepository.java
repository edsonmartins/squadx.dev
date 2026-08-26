package dev.squadx.repository;

import dev.squadx.model.CodeIntelligenceIndexJob;
import dev.squadx.model.enums.IntelligenceJobStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CodeIntelligenceIndexJobRepository extends JpaRepository<CodeIntelligenceIndexJob, Long> {
    List<CodeIntelligenceIndexJob> findBySnapshotIdAndStatusIn(
            Long snapshotId, List<IntelligenceJobStatus> statuses);

    List<CodeIntelligenceIndexJob> findTop20ByStatusOrderByCreatedAtAsc(IntelligenceJobStatus status);
}
