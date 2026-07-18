package dev.squadx.service;

import dev.squadx.dto.preferences.UserPreferencesRequest;
import dev.squadx.dto.preferences.UserPreferencesResponse;
import dev.squadx.model.User;
import dev.squadx.model.UserPreferences;
import dev.squadx.model.enums.LiveViewQuality;
import dev.squadx.repository.UserPreferencesRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserPreferencesServiceTest {

    @Mock private UserPreferencesRepository repository;

    @InjectMocks private UserPreferencesService service;

    private User currentUser;

    @BeforeEach
    void setUp() {
        currentUser = User.builder().email("user@example.com").fullName("User").build();
        currentUser.setId(7L);
    }

    @Test
    @DisplayName("get() returns defaults and persists a row on first access")
    void getMaterializesDefaults() {
        when(repository.findByUserId(7L)).thenReturn(Optional.empty());
        when(repository.save(any(UserPreferences.class))).thenAnswer(inv -> inv.getArgument(0));

        UserPreferencesResponse response = service.get(currentUser);

        // Defaults from the entity builder.
        assertThat(response.isEmailNotifications()).isTrue();
        assertThat(response.isPushNotifications()).isTrue();
        assertThat(response.isExecutionAlerts()).isTrue();
        assertThat(response.isLiveSessionAlerts()).isTrue();
        assertThat(response.isAutoStartLive()).isTrue();
        assertThat(response.getDefaultQuality()).isEqualTo(LiveViewQuality.HD);
        assertThat(response.getMaxViewers()).isEqualTo(5);

        ArgumentCaptor<UserPreferences> saved = ArgumentCaptor.forClass(UserPreferences.class);
        verify(repository).save(saved.capture());
        assertThat(saved.getValue().getUser()).isSameAs(currentUser);
    }

    @Test
    @DisplayName("get() returns the existing row without creating a new one")
    void getReturnsExisting() {
        UserPreferences existing = UserPreferences.builder()
                .user(currentUser)
                .emailNotifications(false)
                .defaultQuality(LiveViewQuality.SD)
                .maxViewers(10)
                .build();
        when(repository.findByUserId(7L)).thenReturn(Optional.of(existing));

        UserPreferencesResponse response = service.get(currentUser);

        assertThat(response.isEmailNotifications()).isFalse();
        assertThat(response.getDefaultQuality()).isEqualTo(LiveViewQuality.SD);
        assertThat(response.getMaxViewers()).isEqualTo(10);
        verify(repository, never()).save(any());
    }

    @Test
    @DisplayName("update() writes all fields onto the existing row")
    void updateMutatesExisting() {
        UserPreferences existing = UserPreferences.builder().user(currentUser).build();
        when(repository.findByUserId(7L)).thenReturn(Optional.of(existing));
        when(repository.save(any(UserPreferences.class))).thenAnswer(inv -> inv.getArgument(0));

        UserPreferencesRequest request = UserPreferencesRequest.builder()
                .emailNotifications(false)
                .pushNotifications(false)
                .executionAlerts(true)
                .liveSessionAlerts(false)
                .autoStartLive(false)
                .defaultQuality(LiveViewQuality.AUTO)
                .maxViewers(20)
                .build();

        UserPreferencesResponse response = service.update(request, currentUser);

        assertThat(response.isEmailNotifications()).isFalse();
        assertThat(response.isPushNotifications()).isFalse();
        assertThat(response.isExecutionAlerts()).isTrue();
        assertThat(response.isLiveSessionAlerts()).isFalse();
        assertThat(response.isAutoStartLive()).isFalse();
        assertThat(response.getDefaultQuality()).isEqualTo(LiveViewQuality.AUTO);
        assertThat(response.getMaxViewers()).isEqualTo(20);
        // The saved entity is the same row we loaded (no duplicate).
        assertThat(existing.getMaxViewers()).isEqualTo(20);
    }

    @Test
    @DisplayName("update() creates a row from defaults when none exists yet")
    void updateCreatesWhenMissing() {
        when(repository.findByUserId(7L)).thenReturn(Optional.empty());
        when(repository.save(any(UserPreferences.class))).thenAnswer(inv -> inv.getArgument(0));

        UserPreferencesRequest request = UserPreferencesRequest.builder()
                .emailNotifications(true)
                .pushNotifications(true)
                .executionAlerts(true)
                .liveSessionAlerts(true)
                .autoStartLive(true)
                .defaultQuality(LiveViewQuality.HD)
                .maxViewers(3)
                .build();

        UserPreferencesResponse response = service.update(request, currentUser);

        assertThat(response.getMaxViewers()).isEqualTo(3);
        ArgumentCaptor<UserPreferences> saved = ArgumentCaptor.forClass(UserPreferences.class);
        verify(repository).save(saved.capture());
        assertThat(saved.getValue().getUser()).isSameAs(currentUser);
    }
}
