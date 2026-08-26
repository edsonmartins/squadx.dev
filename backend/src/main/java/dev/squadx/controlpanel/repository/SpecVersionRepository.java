package dev.squadx.controlpanel.repository;

import dev.squadx.controlpanel.model.SpecVersion;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface SpecVersionRepository extends JpaRepository<SpecVersion, Long> {
    Optional<SpecVersion> findFirstByChangeIdAndCurrentTrue(Long changeId);
    List<SpecVersion> findByChangeIdOrderByCreatedAtDesc(Long changeId);
    Optional<SpecVersion> findFirstByChangeIdOrderByCreatedAtDesc(Long changeId);
}
