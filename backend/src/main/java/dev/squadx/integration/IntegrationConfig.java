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
    private GitConfig git = new GitConfig();
    private String serviceSecret;

    /** Git provider config for Control Panel spec materialization (RFC-0002). */
    @Getter
    @Setter
    public static class GitConfig {
        private boolean enabled = false;
        private String apiUrl = "https://api.github.com";
        private String token;
        private String branchPrefix = "spec/";
    }

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
