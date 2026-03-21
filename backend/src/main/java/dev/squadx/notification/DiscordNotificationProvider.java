package dev.squadx.notification;

import dev.squadx.model.NotificationChannel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.Map;

@Component
@ConditionalOnProperty(prefix = "squadx.notifications.discord", name = "enabled", havingValue = "true", matchIfMissing = true)
@RequiredArgsConstructor
@Slf4j
public class DiscordNotificationProvider implements NotificationProvider {

    private final RestClient.Builder restClientBuilder;

    @Override
    public void send(String message, String webhookUrl) {
        RestClient client = restClientBuilder.build();
        client.post()
                .uri(webhookUrl)
                .header("Content-Type", "application/json")
                .body(buildPayload(message))
                .retrieve()
                .toBodilessEntity();
        log.debug("Discord notification sent");
    }

    @Override
    public boolean supports(NotificationChannel channel) {
        return channel == NotificationChannel.DISCORD;
    }

    @Override
    public Map<String, Object> buildPayload(String message) {
        return Map.of("content", message);
    }
}
