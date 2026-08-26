package dev.squadx.model;

import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;

@Entity @Table(name = "code_intelligence_decisions")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class CodeIntelligenceDecision extends BaseEntity {
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "snapshot_id", nullable = false)
    private CodeIntelligenceSnapshot snapshot;
    @Column(nullable = false, length = 200) private String title;
    @Column(nullable = false, columnDefinition = "TEXT") private String rationale;
    @Column(name = "evidence_json", nullable = false, columnDefinition = "TEXT") private String evidenceJson;
    @Column(nullable = false, length = 20) @Builder.Default private String status = "PENDING";
    @Column(name = "brainsentry_memory_id", length = 160) private String brainsentryMemoryId;
    @Column(name = "reviewed_by") private Long reviewedBy;
    @Column(name = "reviewed_at") private Instant reviewedAt;
}
