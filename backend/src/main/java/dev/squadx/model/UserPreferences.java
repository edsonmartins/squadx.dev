package dev.squadx.model;

import dev.squadx.model.enums.LiveViewQuality;
import jakarta.persistence.*;
import lombok.*;

/**
 * Per-user UI preferences (notifications + live view). One row per user.
 *
 * <p>These are non-sensitive presentation/notification toggles. Provider API keys are
 * deliberately NOT stored here — they live on the client machine and are injected into
 * the sandbox by the daemon, never persisted server-side.
 */
@Entity
@Table(name = "user_preferences")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserPreferences extends BaseEntity {

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    private User user;

    // --- Notifications ---

    @Column(name = "email_notifications", nullable = false)
    @Builder.Default
    private boolean emailNotifications = true;

    @Column(name = "push_notifications", nullable = false)
    @Builder.Default
    private boolean pushNotifications = true;

    @Column(name = "execution_alerts", nullable = false)
    @Builder.Default
    private boolean executionAlerts = true;

    @Column(name = "live_session_alerts", nullable = false)
    @Builder.Default
    private boolean liveSessionAlerts = true;

    // --- Live View ---

    @Column(name = "auto_start_live", nullable = false)
    @Builder.Default
    private boolean autoStartLive = true;

    @Enumerated(EnumType.STRING)
    @Column(name = "default_quality", nullable = false, length = 10)
    @Builder.Default
    private LiveViewQuality defaultQuality = LiveViewQuality.HD;

    @Column(name = "max_viewers", nullable = false)
    @Builder.Default
    private int maxViewers = 5;
}
