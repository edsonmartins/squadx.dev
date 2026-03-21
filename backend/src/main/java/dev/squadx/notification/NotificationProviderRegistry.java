package dev.squadx.notification;

import dev.squadx.model.NotificationChannel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

/**
 * Registry that auto-discovers all NotificationProvider beans
 * and provides lookup by channel type.
 */
@Component
@Slf4j
public class NotificationProviderRegistry {

    private final List<NotificationProvider> providers;

    public NotificationProviderRegistry(List<NotificationProvider> providers) {
        this.providers = providers;
        log.info("Registered {} notification providers: {}", providers.size(),
                providers.stream().map(p -> p.getClass().getSimpleName()).toList());
    }

    /**
     * Get the provider for a given channel.
     * @throws IllegalArgumentException if no provider supports the channel
     */
    public NotificationProvider getProvider(NotificationChannel channel) {
        return findProvider(channel)
                .orElseThrow(() -> new IllegalArgumentException(
                        "No notification provider registered for channel: " + channel));
    }

    public Optional<NotificationProvider> findProvider(NotificationChannel channel) {
        return providers.stream()
                .filter(p -> p.supports(channel))
                .findFirst();
    }

    public boolean hasProvider(NotificationChannel channel) {
        return providers.stream().anyMatch(p -> p.supports(channel));
    }
}
