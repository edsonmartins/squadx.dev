package dev.squadx.controlpanel.model;

import dev.squadx.controlpanel.model.enums.RequirementType;
import dev.squadx.model.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

/**
 * Requisito como delta OpenSpec (ADDED/MODIFIED/REMOVED), com cenários de aceite (WHEN/THEN).
 * {@code requirementId} é a referência estável dentro da mudança (ex.: "R1").
 */
@Entity
@Table(name = "requirements")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Requirement extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "change_id", nullable = false)
    private Change change;

    @Column(name = "requirement_id", nullable = false)
    private String requirementId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private RequirementType type;

    @Column(nullable = false)
    private String title;

    @Column(columnDefinition = "TEXT")
    private String description;
}

