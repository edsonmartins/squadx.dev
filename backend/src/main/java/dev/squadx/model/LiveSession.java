package dev.squadx.model;

import dev.squadx.model.enums.LiveSessionStatus;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;

@Entity
@Table(name = "live_sessions")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LiveSession extends BaseEntity {

    @Column(unique = true, nullable = false, length = 8)
    private String code;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "task_id")
    private Task task;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "execution_id")
    private Execution execution;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "host_user_id")
    private User hostUser;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private LiveSessionStatus status = LiveSessionStatus.PENDING;

    @Column(name = "max_viewers")
    @Builder.Default
    private Integer maxViewers = 10;

    @Column(name = "container_id")
    private String containerId;

    @Column(name = "resolution")
    @Builder.Default
    private String resolution = "1280x720";

    @Column(name = "vnc_port")
    private Integer vncPort;

    @Column(name = "webrtc_offer", columnDefinition = "TEXT")
    private String webrtcOffer;

    @Column(name = "external_session_id")
    private String externalSessionId;

    @Column(name = "external_join_code")
    private String externalJoinCode;

    @Column(name = "external_join_url")
    private String externalJoinUrl;

    @Column(name = "ended_at")
    private LocalDateTime endedAt;

    @OneToMany(mappedBy = "session", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private Set<LiveSessionParticipant> participants = new HashSet<>();
}
