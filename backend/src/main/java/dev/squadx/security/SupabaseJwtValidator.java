package dev.squadx.security;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

/**
 * Validates Supabase JWT tokens for SSO integration.
 * Uses the Supabase JWT secret to validate tokens issued by Supabase Auth.
 */
@Component
@Slf4j
public class SupabaseJwtValidator {

    private final SecretKey supabaseKey;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public SupabaseJwtValidator(
            @Value("${supabase.jwt-secret:}") String jwtSecret
    ) {
        if (jwtSecret != null && !jwtSecret.isBlank()) {
            this.supabaseKey = Keys.hmacShaKeyFor(jwtSecret.getBytes(StandardCharsets.UTF_8));
            log.info("Supabase JWT validator initialized");
        } else {
            this.supabaseKey = null;
            log.info("Supabase JWT validator disabled (no JWT secret configured)");
        }
    }

    /**
     * Attempt to validate a JWT as a Supabase token.
     *
     * @param token the JWT to validate
     * @return the user email if valid, null otherwise
     */
    public String validateAndExtractEmail(String token) {
        if (supabaseKey == null) return null;

        try {
            Claims claims = Jwts.parser()
                    .verifyWith(supabaseKey)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();

            // Check expiration
            if (claims.getExpiration().before(new Date())) {
                return null;
            }

            // Extract email from Supabase claims
            String email = claims.get("email", String.class);
            if (email != null) return email;

            // Try nested user_metadata
            Object userMetadata = claims.get("user_metadata");
            if (userMetadata != null) {
                JsonNode node = objectMapper.valueToTree(userMetadata);
                if (node.has("email")) {
                    return node.get("email").asText();
                }
            }

            return claims.getSubject(); // fallback to sub (user UUID)
        } catch (Exception e) {
            log.trace("Supabase JWT validation failed: {}", e.getMessage());
            return null;
        }
    }

    public boolean isEnabled() {
        return supabaseKey != null;
    }
}
