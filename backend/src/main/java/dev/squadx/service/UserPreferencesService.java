package dev.squadx.service;

import dev.squadx.dto.preferences.UserPreferencesRequest;
import dev.squadx.dto.preferences.UserPreferencesResponse;
import dev.squadx.model.User;
import dev.squadx.model.UserPreferences;
import dev.squadx.repository.UserPreferencesRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
     * Return the current user's preferences, materializing defaults on first access so a
     * user who has never saved still gets a sensible payload (and a row to update later).
     */
    @Transactional
    public UserPreferencesResponse get(User currentUser) {
        UserPreferences prefs = repository.findByUserId(currentUser.getId())
                .orElseGet(() -> repository.save(defaults(currentUser)));
        return mapToResponse(prefs);
    }

    @Transactional
    public UserPreferencesResponse update(UserPreferencesRequest request, User currentUser) {
        UserPreferences prefs = repository.findByUserId(currentUser.getId())
                .orElseGet(() -> defaults(currentUser));

        prefs.setEmailNotifications(request.getEmailNotifications());
        prefs.setPushNotifications(request.getPushNotifications());
        prefs.setExecutionAlerts(request.getExecutionAlerts());
        prefs.setLiveSessionAlerts(request.getLiveSessionAlerts());
        prefs.setAutoStartLive(request.getAutoStartLive());
        prefs.setDefaultQuality(request.getDefaultQuality());
        prefs.setMaxViewers(request.getMaxViewers());

        UserPreferences saved = repository.save(prefs);
        log.info("Updated preferences for user {}", currentUser.getId());
        return mapToResponse(saved);
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
