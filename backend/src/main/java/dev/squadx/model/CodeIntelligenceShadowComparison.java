package dev.squadx.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Entity
@Table(name = "code_intelligence_shadow_comparisons")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class CodeIntelligenceShadowComparison extends BaseEntity {
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "snapshot_id", nullable = false)
    private CodeIntelligenceSnapshot snapshot;
    @Column(nullable = false, length = 500) private String query;
    @Column(name = "primary_provider", nullable = false, length = 80) private String primaryProvider;
    @Column(name = "shadow_provider", nullable = false, length = 80) private String shadowProvider;
    @Column(name = "primary_hits", nullable = false) private Integer primaryHits;
    @Column(name = "shadow_hits", nullable = false) private Integer shadowHits;
    @Column(name = "overlap_hits", nullable = false) private Integer overlapHits;
    @Column(name = "divergence_score", nullable = false) private Double divergenceScore;
    @Column(name = "primary_latency_ms") private Long primaryLatencyMs;
    @Column(name = "shadow_latency_ms") private Long shadowLatencyMs;
    @Column(name = "error_message", columnDefinition = "TEXT") private String errorMessage;
    @Column(name = "compared_at", nullable = false) @Builder.Default private Instant comparedAt = Instant.now();
}
