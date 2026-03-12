package dev.squadx.model;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "sso_configs",
        uniqueConstraints = @UniqueConstraint(columnNames = {"organization_id", "provider"}))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SsoConfig extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "organization_id", nullable = false)
    private Organization organization;

    @Column(nullable = false, length = 50)
    private String provider;

    @Column(name = "client_id", nullable = false, length = 500)
    private String clientId;

    @Column(name = "client_secret", nullable = false, length = 1000)
    private String clientSecret;

    @Column(name = "issuer_uri", length = 1000)
    private String issuerUri;

    @Column(nullable = false)
    @Builder.Default
    private boolean enabled = false;
}
