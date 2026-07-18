package dev.squadx.service;

import dev.squadx.dto.preferences.UserPreferencesRequest;
import dev.squadx.dto.preferences.UserPreferencesResponse;
import dev.squadx.model.User;
import dev.squadx.model.UserPreferences;
import dev.squadx.repository.UserPreferencesRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Per-user UI preferences. Scope is the user themselves — there is no organization
 * membership check here (unlike the org-scoped services); a user can only ever read or
 * write their own row, keyed on {@code currentUser.getId()}.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class UserPreferencesService {

    private final UserPreferencesRepository repository;

    /**
     * Return the current user's preferences. Read-only: if the user has never saved, we
     * return in-memory defaults WITHOUT persisting them. A GET must not write — doing so
     * made the endpoint non-idempotent and let two concurrent first-access requests both
     * INSERT, colliding on the unique {@code user_id} constraint. The row is created
     * lazily by the first {@link #update}.
     */
    @Transactional(readOnly = true)
    public UserPreferencesResponse get(User currentUser) {
        UserPreferences prefs = repository.findByUserId(currentUser.getId())
                .orElseGet(() -> defaults(currentUser));
        return mapToResponse(prefs);
    }

    @Transactional
    public UserPreferencesResponse update(UserPreferencesRequest request, User currentUser) {
        UserPreferences prefs = repository.findByUserId(currentUser.getId())
                .orElseGet(() -> defaults(currentUser));

        apply(request, prefs);

        UserPreferences saved;
        try {
            saved = repository.save(prefs);
        } catch (DataIntegrityViolationException race) {
            // A concurrent request inserted the row between our read and save. Re-read the
            // now-existing row and re-apply this request's values onto it.
            log.debug("Preferences insert raced for user {}, retrying as update", currentUser.getId());
            UserPreferences existing = repository.findByUserId(currentUser.getId())
                    .orElseThrow(() -> race);
            apply(request, existing);
            saved = repository.save(existing);
        }
        log.info("Updated preferences for user {}", currentUser.getId());
        return mapToResponse(saved);
    }

    private void apply(UserPreferencesRequest request, UserPreferences prefs) {
        prefs.setEmailNotifications(request.getEmailNotifications());
        prefs.setPushNotifications(request.getPushNotifications());
        prefs.setExecutionAlerts(request.getExecutionAlerts());
        prefs.setLiveSessionAlerts(request.getLiveSessionAlerts());
        prefs.setAutoStartLive(request.getAutoStartLive());
        prefs.setDefaultQuality(request.getDefaultQuality());
        prefs.setMaxViewers(request.getMaxViewers());
    }

    private UserPreferences defaults(User user) {
        return UserPreferences.builder().user(user).build();
    }

    private UserPreferencesResponse mapToResponse(UserPreferences prefs) {
        return UserPreferencesResponse.builder()
                .emailNotifications(prefs.isEmailNotifications())
                .pushNotifications(prefs.isPushNotifications())
                .executionAlerts(prefs.isExecutionAlerts())
                .liveSessionAlerts(prefs.isLiveSessionAlerts())
                .autoStartLive(prefs.isAutoStartLive())
                .defaultQuality(prefs.getDefaultQuality())
                .maxViewers(prefs.getMaxViewers())
                .updatedAt(prefs.getUpdatedAt())
                .build();
    }
}
