package dev.squadx.notification;

import dev.squadx.model.NotificationChannel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

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

    public List<Map<String, Object>> describeProviders() {
        return Stream.of(NotificationChannel.values())
                .map(channel -> {
                    Optional<NotificationProvider> provider = findProvider(channel);
                    Map<String, Object> description = new LinkedHashMap<>();
                    description.put("key", "notifications." + channel.name().toLowerCase());
                    description.put("type", "notification");
                    description.put("channel", channel.name());
                    description.put("enabled", provider.isPresent());
                    description.put("status", provider.isPresent() ? "AVAILABLE" : "DISABLED");
                    description.put("providerClass", provider.map(p -> p.getClass().getSimpleName()).orElse(null));
                    description.put("requiresConfiguration", "organization webhook configuration");
                    return description;
                })
                .toList();
    }
}
