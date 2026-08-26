package dev.squadx.controlpanel.mcp;

import dev.squadx.config.JwtConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;

class WorkspaceSessionProviderTest {

    private WorkspaceSessionProvider provider;

    @BeforeEach
    void setUp() {
        JwtConfig config = new JwtConfig();
        // HS256 requires a >=256-bit key; secret is Base64-decoded (like JwtService).
        config.setSecret(Base64.getEncoder()
                .encodeToString("a-very-long-workspace-secret-key-0123456789".getBytes(StandardCharsets.UTF_8)));
        provider = new WorkspaceSessionProvider(config, 3600);
    }

    @Test
    void issueAndParseRoundTrip() {
        WorkspaceSession session = new WorkspaceSession(1L, 100L, 7L, 5L, "Backend Agent");
        String token = provider.issue(session);

        WorkspaceSession parsed = provider.parse(token);
        assertThat(parsed).isNotNull();
        assertThat(parsed.userId()).isEqualTo(1L);
        assertThat(parsed.orgId()).isEqualTo(100L);
        assertThat(parsed.projectId()).isEqualTo(7L);
        assertThat(parsed.changeId()).isEqualTo(5L);
        assertThat(parsed.assignee()).isEqualTo("Backend Agent");
    }

    @Test
    void rejectsInvalidToken() {
        assertThat(provider.parse("not-a-jwt")).isNull();
    }

    @Test
    void rejectsTokenSignedWithAnotherKey() {
        JwtConfig other = new JwtConfig();
        other.setSecret(Base64.getEncoder()
                .encodeToString("a-completely-different-secret-key-9876543210".getBytes(StandardCharsets.UTF_8)));
        String foreign = new WorkspaceSessionProvider(other, 3600)
                .issue(new WorkspaceSession(2L, 1L, 1L, 1L, "x"));

        assertThat(provider.parse(foreign)).isNull();
    }
}

