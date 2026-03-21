package dev.squadx.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.SimpleLoggerAdvisor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Spring AI configuration for SquadX.
 * Only activated when squadx.ai.enabled=true.
 */
@Configuration
@ConditionalOnProperty(prefix = "squadx.ai", name = "enabled", havingValue = "true")
@Slf4j
public class AiConfig {

    @Bean
    public ChatClient chatClient(ChatClient.Builder builder) {
        log.info("Spring AI ChatClient initialized for SquadX analysis");
        return builder
                .defaultAdvisors(new SimpleLoggerAdvisor())
                .build();
    }
}
