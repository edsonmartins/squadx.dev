package dev.squadx.model;

import dev.squadx.model.enums.IntelligenceSnapshotStatus;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Entity
@Table(name = "code_intelligence_snapshots",
        uniqueConstraints = @UniqueConstraint(name = "ux_ci_snapshot_project_revision_provider",
                columnNames = {"project_id", "revision", "provider"}))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CodeIntelligenceSnapshot extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "project_id", nullable = false)
    private Project project;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "organization_id", nullable = false)
    private Organization organization;

    @Column(name = "repository_url", nullable = false, length = 1000)
    private String repositoryUrl;

    @Column(nullable = false, length = 128, updatable = false)
    private String revision;

    @Column(nullable = false, length = 80, updatable = false)
    private String provider;

    @Column(name = "provider_version", length = 80)
    private String providerVersion;

    @Column(name = "external_snapshot_id", length = 255)
    private String externalSnapshotId;

    @Column(name = "external_job_id", length = 255)
    private String externalJobId;

    @Column(name = "scip_artifact_sha256", length = 64)
    private String scipArtifactSha256;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    @Builder.Default
    private IntelligenceSnapshotStatus status = IntelligenceSnapshotStatus.PENDING;

    @Column(name = "indexed_at")
    private Instant indexedAt;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;
}
