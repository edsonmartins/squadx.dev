package dev.squadx.controlpanel.model;

import dev.squadx.model.BaseEntity;
import dev.squadx.model.User;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "spec_versions", uniqueConstraints = @UniqueConstraint(name = "uq_spec_version_change_version",
        columnNames = {"change_id", "version"}))
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class SpecVersion extends BaseEntity {
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "change_id", nullable = false)
    private Change change;
    @Column(nullable = false)
    private String version;
    @Column(nullable = false)
    @Builder.Default private boolean current = false;
    private String summary;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "author_id")
    private User author;
    @Column(name = "commit_sha", length = 64)
    private String commitSha;
}
