package dev.squadx.model;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "cost_events")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CostEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "execution_id")
    private Execution execution;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "agent_id")
    private Agent agent;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "organization_id", nullable = false)
    private Organization organization;

    @Column(nullable = false, length = 50)
    private String provider;

    @Column(nullable = false, length = 100)
    private String model;

    @Column(name = "input_tokens", nullable = false)
    @Builder.Default
    private Integer inputTokens = 0;

    @Column(name = "output_tokens", nullable = false)
    @Builder.Default
    private Integer outputTokens = 0;

    @Column(name = "cost_cents", nullable = false, precision = 10, scale = 4)
    @Builder.Default
    private BigDecimal costCents = BigDecimal.ZERO;

    @Column(name = "created_at", updatable = false)
    @Builder.Default
    private Instant createdAt = Instant.now();
}
