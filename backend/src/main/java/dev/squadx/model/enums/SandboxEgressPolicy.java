package dev.squadx.model.enums;

/**
 * Which egress policy a squad's agents run under in the sandbox (RFC-0006 / ADR-0008).
 *
 * <p>Agents execute model output, so their network access is a security boundary rather
 * than a convenience setting. The names here are the contract with the client daemon,
 * which maps them onto its own presets in {@code docker/network_policy.py}; keep the two
 * in step. Enforcement lives entirely in the client — the backend only expresses intent.
 *
 * <ul>
 *   <li>{@code AGENT_DEFAULT} – default-deny plus an allowlist that lets agents work:
 *       LLM providers, package registries, git. The standing production choice.</li>
 *   <li>{@code DENY_ALL} – no egress at all. Appropriate for squads working on code that
 *       never needs the network; will break anything that fetches dependencies.</li>
 *   <li>{@code FULL} – everything except cloud metadata. Debugging only: an agent under
 *       this policy can reach any host it can name, so prompt injection reaches the
 *       whole internet.</li>
 * </ul>
 */
public enum SandboxEgressPolicy {
    AGENT_DEFAULT,
    DENY_ALL,
    FULL
}
