package dev.squadx.notification;

import dev.squadx.model.NotificationChannel;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NotificationProviderRegistryTest {

    @Mock
    private NotificationProvider slackProvider;

    @Mock
    private NotificationProvider discordProvider;

    private NotificationProviderRegistry registry;

    @BeforeEach
    void setUp() {
        when(slackProvider.supports(NotificationChannel.SLACK)).thenReturn(true);
        when(discordProvider.supports(NotificationChannel.DISCORD)).thenReturn(true);
        registry = new NotificationProviderRegistry(List.of(slackProvider, discordProvider));
    }

    @Test
    @DisplayName("should return provider for supported channel")
    void shouldReturnProviderForSupportedChannel() {
        assertThat(registry.getProvider(NotificationChannel.SLACK)).isEqualTo(slackProvider);
        assertThat(registry.getProvider(NotificationChannel.DISCORD)).isEqualTo(discordProvider);
    }

    @Test
    @DisplayName("should throw for unsupported channel")
    void shouldThrowForUnsupportedChannel() {
        assertThatThrownBy(() -> registry.getProvider(NotificationChannel.TEAMS))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("No notification provider");
    }

    @Test
    @DisplayName("hasProvider should return true for registered channels")
    void hasProviderShouldReturnTrue() {
        assertThat(registry.hasProvider(NotificationChannel.SLACK)).isTrue();
        assertThat(registry.hasProvider(NotificationChannel.TEAMS)).isFalse();
    }

    @Test
    @DisplayName("findProvider should return Optional")
    void findProviderShouldReturnOptional() {
        assertThat(registry.findProvider(NotificationChannel.SLACK)).isPresent();
        assertThat(registry.findProvider(NotificationChannel.TEAMS)).isEmpty();
    }
}
