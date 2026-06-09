package dev.squadx.controlpanel.repository;

import dev.squadx.controlpanel.model.SpecVersion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface SpecVersionRepository extends JpaRepository<SpecVersion, Long> {

    List<SpecVersion> findByChangeIdOrderByVersionDesc(Long changeId);

    Optional<SpecVersion> findByChangeIdAndCurrentTrue(Long changeId);

    Optional<SpecVersion> findTopByChangeIdOrderByVersionDesc(Long changeId);
}
