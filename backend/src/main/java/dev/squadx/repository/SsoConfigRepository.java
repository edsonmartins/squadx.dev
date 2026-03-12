package dev.squadx.repository;

import dev.squadx.model.SsoConfig;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface SsoConfigRepository extends JpaRepository<SsoConfig, Long> {

    List<SsoConfig> findByOrganizationId(Long organizationId);

    Optional<SsoConfig> findByOrganizationIdAndProvider(Long organizationId, String provider);

    List<SsoConfig> findByOrganizationIdAndEnabledTrue(Long organizationId);

    boolean existsByOrganizationIdAndProvider(Long organizationId, String provider);
}
