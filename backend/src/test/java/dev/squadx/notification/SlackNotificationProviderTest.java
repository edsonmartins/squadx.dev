package dev.squadx.notification;

import dev.squadx.model.NotificationChannel;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class SlackNotificationProviderTest {

    @Test
    @DisplayName("should support SLACK channel")
    void shouldSupportSlack() {
        var provider = new SlackNotificationProvider(null);
        assertThat(provider.supports(NotificationChannel.SLACK)).isTrue();
        assertThat(provider.supports(NotificationChannel.DISCORD)).isFalse();
        assertThat(provider.supports(NotificationChannel.TEAMS)).isFalse();
    }

    @Test
    @DisplayName("should build correct Slack payload")
    void shouldBuildSlackPayload() {
        var provider = new SlackNotificationProvider(null);
        Map<String, Object> payload = provider.buildPayload("Hello World");
        assertThat(payload).containsEntry("text", "Hello World");
        assertThat(payload).containsEntry("unfurl_links", false);
    }
}
