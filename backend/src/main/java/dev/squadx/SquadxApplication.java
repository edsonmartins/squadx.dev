package dev.squadx;

import dev.squadx.config.SupabaseConfig;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication
@EnableAsync
@EnableConfigurationProperties(SupabaseConfig.class)
public class SquadxApplication {

    public static void main(String[] args) {
        SpringApplication.run(SquadxApplication.class, args);
    }
}
