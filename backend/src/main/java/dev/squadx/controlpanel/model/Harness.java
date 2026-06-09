package dev.squadx.controlpanel.model;

import dev.squadx.controlpanel.model.enums.HarnessStatus;
import dev.squadx.model.Agent;
import dev.squadx.model.BaseEntity;
import dev.squadx.model.Organization;
import jakarta.persistence.*;
import lombok.*;

import java.util.ArrayList;
import java.util.List;

/**
 * Harness de agente cadastrado (ADR-0003). Fala o contrato MCP `workspace`; o modelo LLM é escolhido
 * pelo usuário dentre os disponíveis. Pode ser mapeado a um {@link Agent} (assignee).
 */
@Entity
@Table(name = "harnesses")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Harness extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "organization_id", nullable = false)
    private Organization organization;

    @Column(nullable = false)
    private String key;

    @Column(nullable = false)
    private String name;

    private String vendor;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private HarnessStatus status = HarnessStatus.AVAILABLE;

    /** Modelo LLM escolhido para este harness. */
    private String model;

    @ElementCollection
    @CollectionTable(name = "harness_models", joinColumns = @JoinColumn(name = "harness_id"))
    @Column(name = "model")
    @Builder.Default
    private List<String> models = new ArrayList<>();

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "agent_id")
    private Agent agent;

    /** Última abertura de sessão MCP com este harness (status CONNECTED é derivado disto). */
    @Column(name = "last_connected_at")
    private java.time.Instant lastConnectedAt;
}
