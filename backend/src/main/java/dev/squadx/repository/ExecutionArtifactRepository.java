package dev.squadx.repository;

import dev.squadx.model.ExecutionArtifact;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ExecutionArtifactRepository extends JpaRepository<ExecutionArtifact, Long> {
    Optional<ExecutionArtifact> findByExecutionIdAndArtifactKey(Long executionId, String artifactKey);
    List<ExecutionArtifact> findByExecutionIdOrderByCreatedAtDesc(Long executionId);
    Optional<ExecutionArtifact> findFirstByExecutionTaskProjectIdAndExecutionIdNotAndTypeAndFormatAndViewRoleInOrderByCreatedAtDesc(
            Long projectId, Long executionId, String type, String format, List<String> viewRoles);
}
