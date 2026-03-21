package dev.squadx.notification;

import dev.squadx.model.NotificationChannel;

import java.util.Map;

/**
 * Plugin interface for notification channel providers.
 * Each implementation handles a specific channel (Slack, Discord, Teams).
 */
public interface NotificationProvider {

    /**
     * Send a notification message to the given webhook URL.
     */
    void send(String message, String webhookUrl);

    /**
     * Check if this provider supports the given channel.
     */
    boolean supports(NotificationChannel channel);

    /**
     * Build the channel-specific payload.
     */
    Map<String, Object> buildPayload(String message);
}
