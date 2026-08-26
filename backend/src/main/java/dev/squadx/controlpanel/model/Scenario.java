package dev.squadx.controlpanel.model;

import dev.squadx.model.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

/**
 * Cenário de aceite (WHEN/THEN) de um requisito. {@code covered} indica se há ≥1 teste cobrindo
 * (alimentado pelo Pass 5). Colunas {@code when_condition}/{@code then_result} evitam palavras
 * reservadas do SQL.
 */
@Entity
@Table(name = "scenarios")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Scenario extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "requirement_id", nullable = false)
    private Requirement requirement;

    @Column(nullable = false)
    private String name;

    @Column(name = "when_condition", nullable = false, columnDefinition = "TEXT")
    private String whenCondition;

    @Column(name = "then_result", nullable = false, columnDefinition = "TEXT")
    private String thenResult;

    @Column(nullable = false)
    @Builder.Default
    private boolean covered = false;
}

