package dev.squadx.controlpanel.mcp;

import org.springframework.security.authentication.AbstractAuthenticationToken;

import java.util.List;

/** Autenticação de uma sessão MCP do workspace; o principal é a {@link WorkspaceSession}. */
public class WorkspaceAuthentication extends AbstractAuthenticationToken {

    private final transient WorkspaceSession session;

    public WorkspaceAuthentication(WorkspaceSession session) {
        super(List.of());
        this.session = session;
        setAuthenticated(true);
    }

    @Override
    public Object getCredentials() {
        return null;
    }

    @Override
    public Object getPrincipal() {
        return session;
    }
}

