package dev.squadx.dto.cost;

import lombok.*;

import java.math.BigDecimal;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CostBreakdownEntry {
    private String label;
    private BigDecimal costCents;
    private Long inputTokens;
    private Long outputTokens;
    private Double percentage;
}
