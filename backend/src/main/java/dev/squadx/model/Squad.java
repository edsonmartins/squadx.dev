package dev.squadx.model;

import dev.squadx.model.enums.SandboxEgressPolicy;
import jakarta.persistence.*;
import lombok.*;

import java.util.HashSet;
import java.util.Set;

@Entity
@Table(name = "squads")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Squad extends BaseEntity {

    @Column(nullable = false)
    private String name;

    private String description;

    @Column(name = "is_active")
    @Builder.Default
    private boolean isActive = true;

    /**
     * Egress policy this squad's agents run under in the sandbox (RFC-0006 / ADR-0008).
     * Every agent has a mandatory squad, so this always resolves for an executing agent.
     * Dispatched to the client daemon, which is where it is actually enforced.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "sandbox_egress_policy", nullable = false)
    @Builder.Default
    private SandboxEgressPolicy sandboxEgressPolicy = SandboxEgressPolicy.AGENT_DEFAULT;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "organization_id", nullable = false)
    private Organization organization;

    @OneToMany(mappedBy = "squad", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private Set<Agent> agents = new HashSet<>();

    /** Leader agent that work assigned to the squad routes to. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "leader_agent_id")
    private Agent leaderAgent;

    @OneToMany(mappedBy = "squad")
    @Builder.Default
    private Set<Project> projects = new HashSet<>();
}
