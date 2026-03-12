package dev.squadx.repository;

import dev.squadx.model.BrandConfig;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface BrandConfigRepository extends JpaRepository<BrandConfig, Long> {

    Optional<BrandConfig> findByOrganizationId(Long organizationId);

    Optional<BrandConfig> findByCustomDomain(String customDomain);

    boolean existsByOrganizationId(Long organizationId);

    void deleteByOrganizationId(Long organizationId);
}
