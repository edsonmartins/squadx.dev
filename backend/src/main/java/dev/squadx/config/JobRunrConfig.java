package dev.squadx.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Configuration;

/**
 * JobRunr configuration for SquadX.
 * The jobrunr-spring-boot-3-starter auto-configures most beans.
 * This class exists for future customizations (custom retry policies, etc.).
 */
@Configuration
@Slf4j
public class JobRunrConfig {
    // JobRunr auto-configuration handles:
    // - StorageProvider (uses the existing DataSource)
    // - BackgroundJobServer
    // - Dashboard (port 8081)
    // - JobScheduler bean
}
