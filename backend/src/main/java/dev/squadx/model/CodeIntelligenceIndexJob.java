package dev.squadx.model;

import dev.squadx.model.enums.IntelligenceJobStatus;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Entity
@Table(name = "code_intelligence_index_jobs")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CodeIntelligenceIndexJob extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "snapshot_id", nullable = false)
    private CodeIntelligenceSnapshot snapshot;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    @Builder.Default
    private IntelligenceJobStatus status = IntelligenceJobStatus.PENDING;

    @Column(nullable = false)
    @Builder.Default
    private Integer attempt = 0;

    @Column(name = "started_at")
    private Instant startedAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;
}

