package dev.squadx.controlpanel.model;

import dev.squadx.model.BaseEntity;
import dev.squadx.model.User;
import jakarta.persistence.*;
import lombok.*;

/**
 * Versão semântica da spec de uma mudança (RFC-0002 §1). Exatamente uma versão é {@code current}.
 * {@code commit}/{@code contentHash} são preenchidos na materialização.
 */
@Entity
@Table(name = "spec_versions")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SpecVersion extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "change_id", nullable = false)
    private Change change;

    @Column(nullable = false)
    private int version;

    @Column(nullable = false)
    @Builder.Default
    private boolean current = false;

    @Column(columnDefinition = "TEXT")
    private String summary;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "author_id")
    private User author;

    private String commit;

    @Column(name = "content_hash")
    private String contentHash;
}
