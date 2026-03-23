package dev.squadx.integration;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;

/**
 * Generates short-lived JWTs for service-to-service authentication.
 * All integrated services share SQUADX_SERVICE_SECRET.
 */
@Component
@Slf4j
public class ServiceJwtProvider {

    private static final long TTL_SECONDS = 300; // 5 minutes

    private final SecretKey signingKey;

    public ServiceJwtProvider(IntegrationConfig config) {
        String secret = config.getServiceSecret();
        if (secret == null || secret.length() < 32) {
            log.warn("SQUADX_SERVICE_SECRET not configured or too short. Service-to-service auth disabled.");
            this.signingKey = null;
        } else {
            this.signingKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        }
    }

    /**
     * Generate a service JWT for calling another SquadX service.
     *
     * @param audience target service identifier (e.g., "brainsentry", "squadx-live")
     * @return signed JWT string
     */
    public String generateToken(String audience) {
        if (signingKey == null) {
            throw new IllegalStateException("Service secret not configured. Set squadx.service-secret.");
        }

        Instant now = Instant.now();
        return Jwts.builder()
                .issuer("squadx-backend")
                .audience().add(audience).and()
                .subject("service")
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusSeconds(TTL_SECONDS)))
                .signWith(signingKey)
                .compact();
    }

    /**
     * Validate an inbound service JWT from another SquadX service.
     *
     * @param token the JWT to validate
     * @param expectedIssuer expected issuer claim
     * @return true if valid
     */
    public boolean validateToken(String token, String expectedIssuer) {
        if (signingKey == null) return false;

        try {
            var claims = Jwts.parser()
                    .verifyWith(signingKey)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();

            return expectedIssuer.equals(claims.getIssuer())
                    && claims.getExpiration().after(new Date());
        } catch (Exception e) {
            log.debug("Service JWT validation failed: {}", e.getMessage());
            return false;
        }
    }
}
