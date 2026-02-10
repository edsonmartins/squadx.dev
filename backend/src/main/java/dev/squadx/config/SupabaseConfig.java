package dev.squadx.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;

/**
 * Configuration for Supabase integration.
 * Used for live streaming session management.
 */
@Configuration
@ConfigurationProperties(prefix = "supabase")
@Data
public class SupabaseConfig {

    /**
     * Supabase project URL (e.g., https://xxx.supabase.co)
     */
    private String url;

    /**
     * Supabase anonymous key for client-side operations
     */
    private String anonKey;

    /**
     * Supabase service role key for server-side operations
     */
    private String serviceKey;

    /**
     * Creates a WebClient configured for Supabase REST API calls.
     */
    @Bean
    public WebClient supabaseWebClient() {
        return WebClient.builder()
                .baseUrl(url + "/rest/v1")
                .defaultHeader("apikey", anonKey)
                .defaultHeader("Authorization", "Bearer " + anonKey)
                .defaultHeader("Content-Type", "application/json")
                .defaultHeader("Prefer", "return=representation")
                .build();
    }

    /**
     * Creates a WebClient for Supabase RPC calls.
     */
    @Bean
    public WebClient supabaseRpcClient() {
        return WebClient.builder()
                .baseUrl(url + "/rest/v1/rpc")
                .defaultHeader("apikey", anonKey)
                .defaultHeader("Authorization", "Bearer " + anonKey)
                .defaultHeader("Content-Type", "application/json")
                .build();
    }
}
