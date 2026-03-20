package dev.squadx.dto.cost;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.*;

import java.math.BigDecimal;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RecordCostRequest {

    private Long executionId;

    private Long agentId;

    @NotBlank(message = "Provider is required")
    private String provider;

    @NotBlank(message = "Model is required")
    private String model;

    @NotNull(message = "Input tokens is required")
    @Builder.Default
    private Integer inputTokens = 0;

    @NotNull(message = "Output tokens is required")
    @Builder.Default
    private Integer outputTokens = 0;

    @NotNull(message = "Cost cents is required")
    @Builder.Default
    private BigDecimal costCents = BigDecimal.ZERO;
}
