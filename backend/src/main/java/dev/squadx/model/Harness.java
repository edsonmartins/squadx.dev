package dev.squadx.model;

import jakarta.persistence.*;
import lombok.*;

import java.util.ArrayList;
import java.util.List;

/** Organization-scoped agent execution connector (MCP remains harness-agnostic). */
@Entity
@Table(name = "harnesses", uniqueConstraints = @UniqueConstraint(name = "uq_harness_org_key",
        columnNames = {"organization_id", "harness_key"}))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Harness extends BaseEntity {
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "organization_id", nullable = false)
    private Organization organization;

    @Column(name = "harness_key", nullable = false)
    private String key;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private String vendor;

    @Column(nullable = false)
    @Builder.Default
    private String status = "AVAILABLE";

    private String model;

    @ElementCollection
    @CollectionTable(name = "harness_models", joinColumns = @JoinColumn(name = "harness_id"))
    @Column(name = "model", nullable = false)
    @Builder.Default
    private List<String> models = new ArrayList<>();
}
