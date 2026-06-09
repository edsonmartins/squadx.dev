package dev.squadx.controlpanel.mcp;

import dev.squadx.config.JwtConfig;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.time.Instant;
import java.util.Date;

/**
 * Emite e valida tokens de sessão do workspace MCP (RFC-0001 §2/§5). JWT de curta duração assinado
 * com o segredo JWT do repositório, carregando o escopo (org/projeto/change/assignee) e o usuário
 * em nome de quem o agente atua. Contrato versionado por {@link #CONTRACT_VERSION} (R7).
 */
@Component
@Slf4j
public class WorkspaceSessionProvider {

    public static final String CONTRACT_VERSION = "1.0.0";
    private static final String ISSUER = "squadx-workspace";

    private final SecretKey signingKey;
    private final long ttlSeconds;

    public WorkspaceSessionProvider(JwtConfig jwtConfig,
                                    @Value("${squadx.workspace.session-ttl-seconds:3600}") long ttlSeconds) {
        this.signingKey = Keys.hmacShaKeyFor(Decoders.BASE64.decode(jwtConfig.getSecret()));
        this.ttlSeconds = ttlSeconds;
    }

    public long getTtlSeconds() {
        return ttlSeconds;
    }

    public String issue(WorkspaceSession session) {
        Instant now = Instant.now();
        return Jwts.builder()
                .issuer(ISSUER)
                .subject(String.valueOf(session.userId()))
                .claim("org_id", String.valueOf(session.orgId()))
                .claim("project_id", String.valueOf(session.projectId()))
                .claim("change_id", String.valueOf(session.changeId()))
                .claim("assignee", session.assignee())
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusSeconds(ttlSeconds)))
                .signWith(signingKey)
                .compact();
    }

    /** Valida assinatura/expiração/emissor e devolve a sessão; {@code null} se inválido. */
    public WorkspaceSession parse(String token) {
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(signingKey)
                    .requireIssuer(ISSUER)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
            return new WorkspaceSession(
                    Long.valueOf(claims.getSubject()),
                    longClaim(claims, "org_id"),
                    longClaim(claims, "project_id"),
                    longClaim(claims, "change_id"),
                    claims.get("assignee", String.class));
        } catch (Exception e) {
            log.debug("Workspace session token validation failed: {}", e.getMessage());
            return null;
        }
    }

    private Long longClaim(Claims claims, String key) {
        Object value = claims.get(key);
        return value != null ? Long.valueOf(value.toString()) : null;
    }
}
