package dev.squadx.model;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "execution_artifacts", uniqueConstraints =
        @UniqueConstraint(name = "uk_execution_artifact_key", columnNames = {"execution_id", "artifact_key"}))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ExecutionArtifact extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "execution_id", nullable = false)
    private Execution execution;

    @Column(name = "artifact_key", nullable = false, length = 160)
    private String artifactKey;

    @Column(nullable = false, length = 64)
    private String type;

    @Column(nullable = false, length = 32)
    private String format;

    @Column(nullable = false)
    private String name;

    @Column(name = "git_revision", length = 128)
    private String gitRevision;

    @Column(name = "base_revision", length = 128)
    private String baseRevision;

    @Column(name = "artifact_group", length = 160)
    private String artifactGroup;

    @Column(name = "view_role", length = 32)
    private String viewRole;

    @Column(name = "checksum_sha256", nullable = false, length = 64)
    private String checksumSha256;

    @Column(name = "evidence_json", columnDefinition = "TEXT")
    private String evidenceJson;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;
}
