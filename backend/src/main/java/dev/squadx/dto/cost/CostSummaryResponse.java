package dev.squadx.dto.cost;

import lombok.*;

import java.math.BigDecimal;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CostSummaryResponse {
    private BigDecimal totalCostCents;
    private Long totalInputTokens;
    private Long totalOutputTokens;
    private List<CostBreakdownEntry> breakdown;
}
