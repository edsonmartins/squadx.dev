package dev.squadx.model;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "execution_logs")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ExecutionLog extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "execution_id", nullable = false)
    private Execution execution;

    @Column(nullable = false)
    @Builder.Default
    private String level = "INFO";

    /** Attention Budget channel: human | audit | debug (RFC-0005 §1). Stored as String for drift safety. */
    @Column(nullable = false)
    @Builder.Default
    private String visibility = "human";

    /** Attention Budget rank within the channel: low | normal | high | blocking (RFC-0005 §1). */
    @Column(nullable = false)
    @Builder.Default
    private String importance = "normal";

    @Column(nullable = false, columnDefinition = "TEXT")
    private String message;

    @Column(columnDefinition = "TEXT")
    private String metadata;
}
