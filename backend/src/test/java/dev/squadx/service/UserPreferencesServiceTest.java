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
import org.springframework.dao.DataIntegrityViolationException;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
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
    @DisplayName("get() returns defaults WITHOUT persisting on first access (read-only)")
    void getReturnsDefaultsWithoutWriting() {
        when(repository.findByUserId(7L)).thenReturn(Optional.empty());

        UserPreferencesResponse response = service.get(currentUser);

        // Defaults from the entity builder.
        assertThat(response.isEmailNotifications()).isTrue();
        assertThat(response.isPushNotifications()).isTrue();
        assertThat(response.isExecutionAlerts()).isTrue();
        assertThat(response.isLiveSessionAlerts()).isTrue();
        assertThat(response.isAutoStartLive()).isTrue();
        assertThat(response.getDefaultQuality()).isEqualTo(LiveViewQuality.HD);
        assertThat(response.getMaxViewers()).isEqualTo(5);

        // A GET must not write — no row is created until the first update().
        verify(repository, never()).save(any());
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

    @Test
    @DisplayName("update() recovers from a concurrent insert by re-reading and re-applying")
    void updateRetriesOnInsertRace() {
        UserPreferences racedRow = UserPreferences.builder().user(currentUser).build();
        // First lookup: no row yet. Our save loses the race and the unique constraint fires.
        // Second lookup: the row the racing request inserted.
        when(repository.findByUserId(7L))
                .thenReturn(Optional.empty())
                .thenReturn(Optional.of(racedRow));
        when(repository.save(any(UserPreferences.class)))
                .thenThrow(new DataIntegrityViolationException("duplicate user_id"))
                .thenAnswer(inv -> inv.getArgument(0));

        UserPreferencesRequest request = UserPreferencesRequest.builder()
                .emailNotifications(false)
                .pushNotifications(true)
                .executionAlerts(true)
                .liveSessionAlerts(true)
                .autoStartLive(true)
                .defaultQuality(LiveViewQuality.SD)
                .maxViewers(10)
                .build();

        UserPreferencesResponse response = service.update(request, currentUser);

        assertThat(response.getMaxViewers()).isEqualTo(10);
        assertThat(response.getDefaultQuality()).isEqualTo(LiveViewQuality.SD);
        assertThat(response.isEmailNotifications()).isFalse();
        // The request's values were re-applied onto the row that won the race.
        assertThat(racedRow.getMaxViewers()).isEqualTo(10);
        verify(repository, times(2)).save(any(UserPreferences.class));
    }
}
