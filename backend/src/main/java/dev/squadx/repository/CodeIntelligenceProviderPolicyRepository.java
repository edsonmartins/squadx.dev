package dev.squadx.repository;

import dev.squadx.model.CodeIntelligenceProviderPolicy;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface CodeIntelligenceProviderPolicyRepository
        extends JpaRepository<CodeIntelligenceProviderPolicy, Long> {
    Optional<CodeIntelligenceProviderPolicy> findByOrganizationId(Long organizationId);
}

