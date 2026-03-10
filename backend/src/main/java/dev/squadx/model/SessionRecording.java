package dev.squadx.model;

import dev.squadx.model.enums.RecordingStatus;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Entity
@Table(name = "session_recordings")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SessionRecording {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "session_id", nullable = false)
    private LiveSession session;

    @Column(name = "s3_key", nullable = false, length = 500)
    private String s3Key;

    @Column(name = "s3_bucket", nullable = false, length = 255)
    private String s3Bucket;

    @Column(name = "duration_seconds")
    private Integer durationSeconds;

    @Column(name = "file_size_bytes")
    private Long fileSizeBytes;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private RecordingStatus status = RecordingStatus.RECORDING;

    @Column(name = "started_at", nullable = false)
    @Builder.Default
    private Instant startedAt = Instant.now();

    @Column(name = "completed_at")
    private Instant completedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    @Builder.Default
    private Instant createdAt = Instant.now();
}
