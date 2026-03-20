package dev.squadx.service;

import dev.squadx.dto.cost.CostSummaryResponse;
import dev.squadx.dto.cost.RecordCostRequest;
import dev.squadx.exception.ResourceNotFoundException;
import dev.squadx.model.*;
import dev.squadx.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CostTrackingServiceTest {

    @Mock
    private CostEventRepository costEventRepository;

    @Mock
    private OrganizationRepository organizationRepository;

    @Mock
    private ExecutionRepository executionRepository;

    @Mock
    private AgentRepository agentRepository;

    @InjectMocks
    private CostTrackingService costTrackingService;

    private Organization testOrg;

    @BeforeEach
    void setUp() {
        testOrg = Organization.builder()
                .name("Test Org")
                .slug("test-org")
                .build();
        testOrg.setId(1L);
    }

    @Nested
    @DisplayName("recordCost()")
    class RecordCost {

        @Test
        @DisplayName("should create a cost event successfully")
        void shouldCreateCostEvent() {
            RecordCostRequest request = RecordCostRequest.builder()
                    .provider("openai")
                    .model("gpt-4o")
                    .inputTokens(1000)
                    .outputTokens(500)
                    .costCents(new BigDecimal("3.5000"))
                    .build();

            when(organizationRepository.findById(1L)).thenReturn(Optional.of(testOrg));
            when(costEventRepository.save(any(CostEvent.class))).thenAnswer(invocation -> {
                CostEvent saved = invocation.getArgument(0);
                saved.setId(1L);
                return saved;
            });

            CostEvent result = costTrackingService.recordCost(request, 1L);

            assertThat(result).isNotNull();
            assertThat(result.getProvider()).isEqualTo("openai");
            assertThat(result.getModel()).isEqualTo("gpt-4o");
            assertThat(result.getInputTokens()).isEqualTo(1000);
            assertThat(result.getOutputTokens()).isEqualTo(500);
            assertThat(result.getCostCents()).isEqualByComparingTo(new BigDecimal("3.5000"));

            verify(costEventRepository).save(any(CostEvent.class));
        }

        @Test
        @DisplayName("should throw when organization not found")
        void shouldThrowWhenOrganizationNotFound() {
            RecordCostRequest request = RecordCostRequest.builder()
                    .provider("openai")
                    .model("gpt-4o")
                    .build();

            when(organizationRepository.findById(99L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> costTrackingService.recordCost(request, 99L))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessage("Organization not found");
        }

        @Test
        @DisplayName("should throw when execution not found")
        void shouldThrowWhenExecutionNotFound() {
            RecordCostRequest request = RecordCostRequest.builder()
                    .provider("openai")
                    .model("gpt-4o")
                    .executionId(99L)
                    .build();

            when(organizationRepository.findById(1L)).thenReturn(Optional.of(testOrg));
            when(executionRepository.findById(99L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> costTrackingService.recordCost(request, 1L))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessage("Execution not found");
        }
    }

    @Nested
    @DisplayName("getCostSummary()")
    class GetCostSummary {

        @Test
        @DisplayName("should return aggregated summary for date range")
        void shouldReturnAggregatedSummary() {
            LocalDateTime from = LocalDateTime.of(2026, 1, 1, 0, 0);
            LocalDateTime to = LocalDateTime.of(2026, 3, 31, 23, 59);

            when(costEventRepository.sumCostByOrganizationAndDateRange(eq(1L), any(Instant.class), any(Instant.class)))
                    .thenReturn(new BigDecimal("150.0000"));
            when(costEventRepository.sumInputTokensByOrganizationAndDateRange(eq(1L), any(Instant.class), any(Instant.class)))
                    .thenReturn(50000L);
            when(costEventRepository.sumOutputTokensByOrganizationAndDateRange(eq(1L), any(Instant.class), any(Instant.class)))
                    .thenReturn(25000L);

            CostSummaryResponse result = costTrackingService.getCostSummary(1L, from, to);

            assertThat(result.getTotalCostCents()).isEqualByComparingTo(new BigDecimal("150.0000"));
            assertThat(result.getTotalInputTokens()).isEqualTo(50000L);
            assertThat(result.getTotalOutputTokens()).isEqualTo(25000L);
        }

        @Test
        @DisplayName("should return zeros when no data exists")
        void shouldReturnZerosWhenNoData() {
            LocalDateTime from = LocalDateTime.of(2026, 1, 1, 0, 0);
            LocalDateTime to = LocalDateTime.of(2026, 3, 31, 23, 59);

            when(costEventRepository.sumCostByOrganizationAndDateRange(eq(1L), any(Instant.class), any(Instant.class)))
                    .thenReturn(null);
            when(costEventRepository.sumInputTokensByOrganizationAndDateRange(eq(1L), any(Instant.class), any(Instant.class)))
                    .thenReturn(null);
            when(costEventRepository.sumOutputTokensByOrganizationAndDateRange(eq(1L), any(Instant.class), any(Instant.class)))
                    .thenReturn(null);

            CostSummaryResponse result = costTrackingService.getCostSummary(1L, from, to);

            assertThat(result.getTotalCostCents()).isEqualByComparingTo(BigDecimal.ZERO);
            assertThat(result.getTotalInputTokens()).isEqualTo(0L);
            assertThat(result.getTotalOutputTokens()).isEqualTo(0L);
        }
    }

    @Nested
    @DisplayName("getCostByAgent()")
    class GetCostByAgent {

        @Test
        @DisplayName("should group costs by agent with percentages")
        void shouldGroupCostsByAgent() {
            Object[] row1 = new Object[]{1L, "Backend Agent", new BigDecimal("100.0000"), 30000L, 15000L};
            Object[] row2 = new Object[]{2L, "Frontend Agent", new BigDecimal("50.0000"), 20000L, 10000L};

            when(costEventRepository.sumCostGroupedByAgent(1L)).thenReturn(List.of(row1, row2));
            when(costEventRepository.sumTotalCostByOrganization(1L)).thenReturn(new BigDecimal("150.0000"));

            CostSummaryResponse result = costTrackingService.getCostByAgent(1L);

            assertThat(result.getBreakdown()).hasSize(2);
            assertThat(result.getBreakdown().get(0).getLabel()).isEqualTo("Backend Agent");
            assertThat(result.getBreakdown().get(0).getCostCents()).isEqualByComparingTo(new BigDecimal("100.0000"));
            assertThat(result.getBreakdown().get(0).getPercentage()).isCloseTo(66.67, org.assertj.core.api.Assertions.within(0.1));
            assertThat(result.getBreakdown().get(1).getLabel()).isEqualTo("Frontend Agent");
            assertThat(result.getTotalCostCents()).isEqualByComparingTo(new BigDecimal("150.0000"));
        }

        @Test
        @DisplayName("should return empty breakdown when no data")
        void shouldReturnEmptyBreakdown() {
            when(costEventRepository.sumCostGroupedByAgent(1L)).thenReturn(Collections.emptyList());
            when(costEventRepository.sumTotalCostByOrganization(1L)).thenReturn(BigDecimal.ZERO);

            CostSummaryResponse result = costTrackingService.getCostByAgent(1L);

            assertThat(result.getBreakdown()).isEmpty();
            assertThat(result.getTotalCostCents()).isEqualByComparingTo(BigDecimal.ZERO);
        }
    }

    @Nested
    @DisplayName("getCostByModel()")
    class GetCostByModel {

        @Test
        @DisplayName("should group costs by model with percentages")
        void shouldGroupCostsByModel() {
            Object[] row1 = new Object[]{"gpt-4o", new BigDecimal("200.0000"), 40000L, 20000L};
            Object[] row2 = new Object[]{"claude-3-opus", new BigDecimal("100.0000"), 20000L, 10000L};

            when(costEventRepository.sumCostGroupedByModel(1L)).thenReturn(List.of(row1, row2));
            when(costEventRepository.sumTotalCostByOrganization(1L)).thenReturn(new BigDecimal("300.0000"));

            CostSummaryResponse result = costTrackingService.getCostByModel(1L);

            assertThat(result.getBreakdown()).hasSize(2);
            assertThat(result.getBreakdown().get(0).getLabel()).isEqualTo("gpt-4o");
            assertThat(result.getBreakdown().get(0).getCostCents()).isEqualByComparingTo(new BigDecimal("200.0000"));
            assertThat(result.getBreakdown().get(0).getPercentage()).isCloseTo(66.67, org.assertj.core.api.Assertions.within(0.1));
            assertThat(result.getBreakdown().get(1).getLabel()).isEqualTo("claude-3-opus");
            assertThat(result.getTotalCostCents()).isEqualByComparingTo(new BigDecimal("300.0000"));
            assertThat(result.getTotalInputTokens()).isEqualTo(60000L);
            assertThat(result.getTotalOutputTokens()).isEqualTo(30000L);
        }

        @Test
        @DisplayName("should return empty breakdown when no model data")
        void shouldReturnEmptyBreakdownWhenNoModelData() {
            when(costEventRepository.sumCostGroupedByModel(1L)).thenReturn(Collections.emptyList());
            when(costEventRepository.sumTotalCostByOrganization(1L)).thenReturn(BigDecimal.ZERO);

            CostSummaryResponse result = costTrackingService.getCostByModel(1L);

            assertThat(result.getBreakdown()).isEmpty();
            assertThat(result.getTotalCostCents()).isEqualByComparingTo(BigDecimal.ZERO);
        }
    }
}
