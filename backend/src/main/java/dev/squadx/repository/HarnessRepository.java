package dev.squadx.repository;

import dev.squadx.model.Harness;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface HarnessRepository extends JpaRepository<Harness, Long> {
    List<Harness> findByOrganizationIdOrderByNameAsc(Long organizationId);
    boolean existsByOrganizationIdAndKey(Long organizationId, String key);
}
