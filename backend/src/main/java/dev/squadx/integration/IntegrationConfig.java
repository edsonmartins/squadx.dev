package dev.squadx.integration;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Configuration for external service integrations.
 */
@Configuration
@ConfigurationProperties(prefix = "squadx")
@Getter
@Setter
public class IntegrationConfig {

    private BrainSentryConfig brainsentry = new BrainSentryConfig();
    private LiveConfig live = new LiveConfig();
    private LinktorConfig linktor = new LinktorConfig();
    private String serviceSecret;

    @Getter
    @Setter
    public static class BrainSentryConfig {
        private String url = "http://localhost:8090";
        private String tenantId = "default";
        private boolean perOrganizationTenant = true;
        private String tenantPrefix = "org-";
        private boolean enabled = false;
        private String memoryScope = "adaptive";
        private boolean proceduralMemoryEnabled = true;
        private int proceduralLimit = 5;
    }

    @Getter
    @Setter
    public static class LiveConfig {
        private String url = "http://localhost:3100";
        private boolean enabled = false;
    }

    @Getter
    @Setter
    public static class LinktorConfig {
        private String url = "http://localhost:8081";
        private boolean enabled = false;
        private String email;
        private String password;
        private String defaultConversationId;
        private String channelId;
        private String contactId;
        private boolean autoCreateConversation = false;
    }
}
