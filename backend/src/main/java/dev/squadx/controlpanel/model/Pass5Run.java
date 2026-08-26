package dev.squadx.controlpanel.model;

import dev.squadx.controlpanel.model.enums.Pass5Result;
import dev.squadx.model.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

/**
 * Registro de uma execução do Pass 5 para uma tarefa, identificado por {@code (specTask, prSha)}
 * para idempotência (RFC-0004 §6).
 */
@Entity
@Table(name = "pass5_runs")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Pass5Run extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "spec_task_id", nullable = false)
    private SpecTask specTask;

    @Column(name = "pr_sha")
    private String prSha;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Pass5Result outcome;

    @Column(columnDefinition = "TEXT")
    private String critique;

    @Column(name = "coverage_total", nullable = false)
    @Builder.Default
    private int coverageTotal = 0;

    @Column(name = "coverage_covered", nullable = false)
    @Builder.Default
    private int coverageCovered = 0;
}

