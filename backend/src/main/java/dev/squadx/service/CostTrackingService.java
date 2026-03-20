package dev.squadx.service;

import dev.squadx.dto.cost.CostBreakdownEntry;
import dev.squadx.dto.cost.CostSummaryResponse;
import dev.squadx.dto.cost.RecordCostRequest;
import dev.squadx.exception.ResourceNotFoundException;
import dev.squadx.model.Agent;
import dev.squadx.model.CostEvent;
import dev.squadx.model.Execution;
import dev.squadx.model.Organization;
import dev.squadx.repository.AgentRepository;
import dev.squadx.repository.CostEventRepository;
import dev.squadx.repository.ExecutionRepository;
import dev.squadx.repository.OrganizationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class CostTrackingService {

    private final CostEventRepository costEventRepository;
    private final OrganizationRepository organizationRepository;
    private final ExecutionRepository executionRepository;
    private final AgentRepository agentRepository;

    @Transactional
    public CostEvent recordCost(RecordCostRequest request, Long orgId) {
        Organization organization = organizationRepository.findById(orgId)
                .orElseThrow(() -> new ResourceNotFoundException("Organization not found"));

        Execution execution = null;
        if (request.getExecutionId() != null) {
            execution = executionRepository.findById(request.getExecutionId())
                    .orElseThrow(() -> new ResourceNotFoundException("Execution not found"));
        }

        Agent agent = null;
        if (request.getAgentId() != null) {
            agent = agentRepository.findById(request.getAgentId())
                    .orElseThrow(() -> new ResourceNotFoundException("Agent not found"));
        }

        CostEvent event = CostEvent.builder()
                .organization(organization)
                .execution(execution)
                .agent(agent)
                .provider(request.getProvider())
                .model(request.getModel())
                .inputTokens(request.getInputTokens())
                .outputTokens(request.getOutputTokens())
                .costCents(request.getCostCents())
                .build();

        return costEventRepository.save(event);
    }

    public CostSummaryResponse getCostSummary(Long orgId, LocalDateTime from, LocalDateTime to) {
        Instant fromInstant = from.toInstant(ZoneOffset.UTC);
        Instant toInstant = to.toInstant(ZoneOffset.UTC);

        BigDecimal totalCost = costEventRepository.sumCostByOrganizationAndDateRange(orgId, fromInstant, toInstant);
        Long totalInput = costEventRepository.sumInputTokensByOrganizationAndDateRange(orgId, fromInstant, toInstant);
        Long totalOutput = costEventRepository.sumOutputTokensByOrganizationAndDateRange(orgId, fromInstant, toInstant);

        return CostSummaryResponse.builder()
                .totalCostCents(totalCost != null ? totalCost : BigDecimal.ZERO)
                .totalInputTokens(totalInput != null ? totalInput : 0L)
                .totalOutputTokens(totalOutput != null ? totalOutput : 0L)
                .breakdown(List.of())
                .build();
    }

    public CostSummaryResponse getCostByAgent(Long orgId) {
        List<Object[]> rows = costEventRepository.sumCostGroupedByAgent(orgId);
        BigDecimal grandTotal = costEventRepository.sumTotalCostByOrganization(orgId);

        return buildBreakdownResponse(rows, grandTotal, true);
    }

    public CostSummaryResponse getCostByModel(Long orgId) {
        List<Object[]> rows = costEventRepository.sumCostGroupedByModel(orgId);
        BigDecimal grandTotal = costEventRepository.sumTotalCostByOrganization(orgId);

        return buildBreakdownResponse(rows, grandTotal, false);
    }

    public Map<String, Object> getBudgetStatus(Long orgId) {
        BigDecimal totalSpent = costEventRepository.sumTotalCostByOrganization(orgId);
        if (totalSpent == null) {
            totalSpent = BigDecimal.ZERO;
        }

        return Map.of(
                "organizationId", orgId,
                "totalSpentCents", totalSpent,
                "currency", "USD"
        );
    }

    private CostSummaryResponse buildBreakdownResponse(List<Object[]> rows, BigDecimal grandTotal, boolean hasAgentId) {
        if (grandTotal == null || grandTotal.compareTo(BigDecimal.ZERO) == 0) {
            grandTotal = BigDecimal.ZERO;
        }

        List<CostBreakdownEntry> entries = new ArrayList<>();
        BigDecimal totalCost = BigDecimal.ZERO;
        long totalInput = 0;
        long totalOutput = 0;

        for (Object[] row : rows) {
            String label;
            BigDecimal cost;
            Long input;
            Long output;

            if (hasAgentId) {
                label = row[1] != null ? row[1].toString() : "Unknown";
                cost = (BigDecimal) row[2];
                input = ((Number) row[3]).longValue();
                output = ((Number) row[4]).longValue();
            } else {
                label = row[0] != null ? row[0].toString() : "Unknown";
                cost = (BigDecimal) row[1];
                input = ((Number) row[2]).longValue();
                output = ((Number) row[3]).longValue();
            }

            double percentage = grandTotal.compareTo(BigDecimal.ZERO) > 0
                    ? cost.divide(grandTotal, 4, RoundingMode.HALF_UP).doubleValue() * 100.0
                    : 0.0;

            entries.add(CostBreakdownEntry.builder()
                    .label(label)
                    .costCents(cost)
                    .inputTokens(input)
                    .outputTokens(output)
                    .percentage(Math.round(percentage * 100.0) / 100.0)
                    .build());

            totalCost = totalCost.add(cost);
            totalInput += input;
            totalOutput += output;
        }

        return CostSummaryResponse.builder()
                .totalCostCents(totalCost)
                .totalInputTokens(totalInput)
                .totalOutputTokens(totalOutput)
                .breakdown(entries)
                .build();
    }
}
