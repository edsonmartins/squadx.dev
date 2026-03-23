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
    private String serviceSecret;

    @Getter
    @Setter
    public static class BrainSentryConfig {
        private String url = "http://localhost:8090";
        private String tenantId = "default";
        private boolean enabled = false;
    }

    @Getter
    @Setter
    public static class LiveConfig {
        private String url = "http://localhost:3100";
        private boolean enabled = false;
    }
}
