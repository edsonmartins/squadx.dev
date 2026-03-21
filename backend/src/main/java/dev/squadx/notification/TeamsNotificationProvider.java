package dev.squadx.notification;

import dev.squadx.model.NotificationChannel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;

@Component
@ConditionalOnProperty(prefix = "squadx.notifications.teams", name = "enabled", havingValue = "true", matchIfMissing = true)
@RequiredArgsConstructor
@Slf4j
public class TeamsNotificationProvider implements NotificationProvider {

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
        log.debug("Teams notification sent");
    }

    @Override
    public boolean supports(NotificationChannel channel) {
        return channel == NotificationChannel.TEAMS;
    }

    @Override
    public Map<String, Object> buildPayload(String message) {
        return Map.of(
                "type", "message",
                "attachments", List.of(Map.of(
                        "contentType", "application/vnd.microsoft.card.adaptive",
                        "content", Map.of(
                                "$schema", "http://adaptivecards.io/schemas/adaptive-card.json",
                                "type", "AdaptiveCard",
                                "version", "1.4",
                                "body", List.of(Map.of(
                                        "type", "TextBlock",
                                        "text", message,
                                        "wrap", true
                                ))
                        )
                ))
        );
    }
}
