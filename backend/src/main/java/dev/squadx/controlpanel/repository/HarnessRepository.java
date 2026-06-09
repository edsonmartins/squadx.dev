package dev.squadx.controlpanel.repository;

import dev.squadx.controlpanel.model.Harness;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface HarnessRepository extends JpaRepository<Harness, Long> {

    List<Harness> findByOrganizationId(Long organizationId);

    boolean existsByOrganizationIdAndKey(Long organizationId, String key);

    Optional<Harness> findByAgentId(Long agentId);
}
