package dev.squadx.controlpanel.model;

import dev.squadx.controlpanel.model.enums.AssigneeType;
import dev.squadx.controlpanel.model.enums.Pass5Result;
import dev.squadx.controlpanel.model.enums.SpecTaskStatus;
import dev.squadx.model.Agent;
import dev.squadx.model.BaseEntity;
import dev.squadx.model.User;
import jakarta.persistence.*;
import lombok.*;

/**
 * Tarefa do Control Panel (spec-driven). Aponta para o requisito de origem ({@code requirement}),
 * tem responsável humano ou agente, e estado de board governado pela máquina de estados
 * (ADR-0004). {@code CONCLUIDA}/{@code AJUSTES} só vêm do Pass 5. Distinta da Task de execução.
 */
@Entity
@Table(name = "spec_tasks")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SpecTask extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "change_id", nullable = false)
    private Change change;

    /** Requisito de origem (rastreabilidade). */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "requirement_id")
    private Requirement requirement;

    @Column(nullable = false)
    private String title;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private SpecTaskStatus status = SpecTaskStatus.A_FAZER;

    @Enumerated(EnumType.STRING)
    @Column(name = "assignee_type")
    private AssigneeType assigneeType;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "assigned_user_id")
    private User assignedUser;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "assigned_agent_id")
    private Agent assignedAgent;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private Pass5Result pass5 = Pass5Result.PENDING;

    @Column(name = "blocker_reason", columnDefinition = "TEXT")
    private String blockerReason;

    @Column(name = "revise_reason", columnDefinition = "TEXT")
    private String reviseReason;
}

