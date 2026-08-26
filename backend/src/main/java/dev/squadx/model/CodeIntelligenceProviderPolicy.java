package dev.squadx.model;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "code_intelligence_provider_policies")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CodeIntelligenceProviderPolicy extends BaseEntity {

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "organization_id", nullable = false, unique = true)
    private Organization organization;

    @Column(name = "primary_provider", nullable = false, length = 80)
    private String primaryProvider;

    @Column(name = "fallback_provider", length = 80)
    private String fallbackProvider;

    @Column(name = "shadow_provider", length = 80)
    private String shadowProvider;

    @Column(name = "shadow_enabled", nullable = false)
    @Builder.Default
    private Boolean shadowEnabled = false;
}

