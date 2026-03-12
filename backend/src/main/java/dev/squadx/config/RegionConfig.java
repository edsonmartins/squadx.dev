package dev.squadx.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * Configuration for multi-region deployment.
 * Determines which region this instance is running in and which regions are available.
 */
@Configuration
@ConfigurationProperties(prefix = "squadx.region")
@Data
public class RegionConfig {

    /**
     * Current region identifier (e.g., us-east-1, eu-west-1)
     */
    private String current = "us-east-1";

    /**
     * List of all available regions in the deployment
     */
    private List<String> available = List.of("us-east-1", "eu-west-1", "ap-southeast-1");
}
