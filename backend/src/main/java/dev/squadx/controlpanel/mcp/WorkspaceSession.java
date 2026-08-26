package dev.squadx.controlpanel.mcp;

/**
 * Contexto de uma sessão MCP do workspace, escopada a uma mudança e a quem a abriu. O agente age
 * em nome de {@code userId} (RBAC/multi-tenancy intactos) dentro de {@code changeId} (escopo).
 */
public record WorkspaceSession(
        Long userId,
        Long orgId,
        Long projectId,
        Long changeId,
        String assignee
) {}

