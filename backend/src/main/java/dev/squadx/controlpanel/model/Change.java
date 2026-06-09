package dev.squadx.controlpanel.model;

import dev.squadx.controlpanel.model.enums.ChangePhase;
import dev.squadx.model.BaseEntity;
import dev.squadx.model.Project;
import dev.squadx.model.User;
import jakarta.persistence.*;
import lombok.*;

/**
 * Mudança (change folder OpenSpec): unidade de trabalho do Control Panel, escopada por projeto.
 * Agrupa requisitos, versões da spec e tarefas.
 */
@Entity
@Table(name = "changes")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Change extends BaseEntity {

    private String module;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private ChangePhase phase = ChangePhase.SPEC;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "project_id", nullable = false)
    private Project project;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by_id")
    private User createdBy;
}
