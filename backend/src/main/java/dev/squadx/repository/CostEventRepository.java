package dev.squadx.repository;

import dev.squadx.model.CostEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;

@Repository
public interface CostEventRepository extends JpaRepository<CostEvent, Long> {

    List<CostEvent> findByOrganizationId(Long organizationId);

    @Query("SELECT SUM(c.costCents) FROM CostEvent c WHERE c.organization.id = :orgId AND c.createdAt >= :from AND c.createdAt <= :to")
    java.math.BigDecimal sumCostByOrganizationAndDateRange(
            @Param("orgId") Long orgId,
            @Param("from") Instant from,
            @Param("to") Instant to
    );

    @Query("SELECT COALESCE(SUM(c.inputTokens), 0) FROM CostEvent c WHERE c.organization.id = :orgId AND c.createdAt >= :from AND c.createdAt <= :to")
    Long sumInputTokensByOrganizationAndDateRange(
            @Param("orgId") Long orgId,
            @Param("from") Instant from,
            @Param("to") Instant to
    );

    @Query("SELECT COALESCE(SUM(c.outputTokens), 0) FROM CostEvent c WHERE c.organization.id = :orgId AND c.createdAt >= :from AND c.createdAt <= :to")
    Long sumOutputTokensByOrganizationAndDateRange(
            @Param("orgId") Long orgId,
            @Param("from") Instant from,
            @Param("to") Instant to
    );

    @Query("SELECT c.agent.id, c.agent.name, SUM(c.costCents), SUM(c.inputTokens), SUM(c.outputTokens) " +
           "FROM CostEvent c WHERE c.organization.id = :orgId AND c.agent IS NOT NULL " +
           "GROUP BY c.agent.id, c.agent.name ORDER BY SUM(c.costCents) DESC")
    List<Object[]> sumCostGroupedByAgent(@Param("orgId") Long orgId);

    @Query("SELECT c.model, SUM(c.costCents), SUM(c.inputTokens), SUM(c.outputTokens) " +
           "FROM CostEvent c WHERE c.organization.id = :orgId " +
           "GROUP BY c.model ORDER BY SUM(c.costCents) DESC")
    List<Object[]> sumCostGroupedByModel(@Param("orgId") Long orgId);

    @Query("SELECT COALESCE(SUM(c.costCents), 0) FROM CostEvent c WHERE c.organization.id = :orgId")
    java.math.BigDecimal sumTotalCostByOrganization(@Param("orgId") Long orgId);
}
