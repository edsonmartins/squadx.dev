package dev.squadx;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import dev.squadx.config.HarnessCatalogProperties;

@SpringBootApplication
@EnableAsync
@EnableScheduling
@EnableConfigurationProperties(HarnessCatalogProperties.class)
public class SquadxApplication {

    public static void main(String[] args) {
        SpringApplication.run(SquadxApplication.class, args);
    }
}
