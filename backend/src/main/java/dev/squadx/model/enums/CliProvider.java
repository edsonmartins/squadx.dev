package dev.squadx.model.enums;

/**
 * External coding-agent CLI used when {@link AgentRuntimeKind#EXTERNAL_CLI} is selected.
 * Mantido em sincronia com o runtime adapter do cliente (external_cli_agent.py,
 * SUPPORTED_PROVIDERS) — o daemon espera estes mesmos valores no payload.
 */
public enum CliProvider {
    CLAUDE_CODE,
    CODEX,
    GEMINI_CLI,
    AIDER,
    OPENCODE
}
