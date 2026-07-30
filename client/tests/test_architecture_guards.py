"""Structural guards: security knobs must have a consumer, and dead ones must be named.

Modelled on agentOS's crates/native-sidecar/tests/architecture_guards.rs, which fails
the build when banned APIs appear outside sanctioned modules. The point is that a
convention written in a doc is not enforcement — a test is.

The specific failure this exists to prevent, which this codebase kept hitting: a knob
is declared, documented, covered by a unit test, and never wired to anything. It then
reads as a control that is in force when it is not. Real examples found while writing
this file:

  - `AgentSandbox(network_policy=...)` was never passed by either production call site,
    so no egress policy was applied anywhere for the parameter's whole life.
  - `HardeningManager.apparmor_profile` never returned its own default, so AppArmor
    was never applied — and the default named a profile that does not exist in the repo.
  - `EgressSidecarConfig` had no caller at all, so the DNS-proxy layer it configured
    was never built.

agentOS has a name for it: a "dead-cap" — a value set in config and silently never
read. The rule here is not that dead knobs are forbidden; it is that they cannot be
*silent*. Anything in QUARANTINE is admitted in writing. The list may shrink, never grow.
"""

from __future__ import annotations

import ast
import re
from pathlib import Path

import pytest

_CLIENT_ROOT = Path(__file__).resolve().parents[1]
_PACKAGE = _CLIENT_ROOT / "squadx_client"
_CONFIG = _PACKAGE / "config.py"

# Settings whose whole purpose is to constrain what agent code can do. If one of these
# has no consumer, an operator who sets it believes they are protected and are not.
_SECURITY_SETTINGS = {
    "enable_network",
    "seccomp_profile",
    "apparmor_profile",
    "cli_security_mode",
    "network_policy",
    "egress_sidecar_enabled",
    "egress_sidecar_image",
    "egress_fail_open",
    "block_cloud_metadata",
    "cost_budget_usd",
    "sandbox_backend",
    "process_network",
    "sandbox_runtime",
    "enable_sandbox",
}

# Knobs known to have no consumer, admitted explicitly rather than silently.
# Each entry must say what is not enforced and what would fix it.
_QUARANTINE = {
    "sandbox_ttl_seconds": (
        "No reaper exists. SandboxLifecycleManager (docker/lifecycle.py) implements TTL "
        "and is unit-tested, but nothing constructs it: sandbox.py imports it and stores "
        "ttl_seconds without ever using either. An orphaned sandbox therefore lives until "
        "the daemon removes it. Fix: run the lifecycle manager from the daemon, or delete "
        "the module and the setting together."
    ),
    "sandbox_max_ttl_seconds": ("Same as sandbox_ttl_seconds — no reaper consumes it."),
    "auto_upgrade_runtime": (
        "resolve_runtime() picks a runtime by probing for the runsc/firecracker binary "
        "and never consults this flag or the thresholds below. Runtime never auto-upgrades."
    ),
    "gvisor_threshold": ("Unused — see auto_upgrade_runtime."),
    "firecracker_threshold": ("Unused — see auto_upgrade_runtime."),
}


def _declared_settings() -> dict[str, str]:
    """Map setting attribute name -> env alias, parsed from the Settings class."""
    tree = ast.parse(_CONFIG.read_text(encoding="utf-8"))
    out: dict[str, str] = {}
    for node in ast.walk(tree):
        if not isinstance(node, ast.AnnAssign) or not isinstance(node.target, ast.Name):
            continue
        alias = ""
        if isinstance(node.value, ast.Call):
            for kw in node.value.keywords:
                if kw.arg == "alias" and isinstance(kw.value, ast.Constant):
                    alias = str(kw.value.value)
        out[node.target.id] = alias
    return out


def _package_sources() -> str:
    """All package source except config.py, where the declarations themselves live."""
    parts = []
    for path in _PACKAGE.rglob("*.py"):
        if path == _CONFIG or "__pycache__" in path.parts:
            continue
        parts.append(path.read_text(encoding="utf-8"))
    return "\n".join(parts)


def _has_consumer(name: str, sources: str) -> bool:
    """True if anything reads this setting.

    Both access styles count: `settings.foo` and the defensive
    `getattr(settings, "foo", default)` the sandbox code uses throughout.
    """
    return bool(
        re.search(rf"settings\.{re.escape(name)}\b", sources)
        or re.search(rf"getattr\(\s*settings\s*,\s*[\"']{re.escape(name)}[\"']", sources)
    )


class TestNoSilentDeadCaps:
    def test_every_security_setting_has_a_consumer(self):
        sources = _package_sources()
        orphaned = sorted(
            name
            for name in _SECURITY_SETTINGS
            if not _has_consumer(name, sources) and name not in _QUARANTINE
        )
        assert not orphaned, (
            f"Security settings with no consumer: {orphaned}. An operator setting one of "
            f"these believes it takes effect. Wire it, delete it, or add it to "
            f"_QUARANTINE with a written reason."
        )

    def test_quarantined_settings_are_still_actually_dead(self):
        """When a quarantined knob gets wired up, this fails so the list shrinks."""
        sources = _package_sources()
        revived = sorted(n for n in _QUARANTINE if _has_consumer(n, sources))
        assert not revived, (
            f"These are quarantined as dead but now have consumers: {revived}. "
            f"Remove them from _QUARANTINE."
        )

    def test_quarantine_entries_are_declared_settings(self):
        """Guards against the list rotting into a set of names that no longer exist."""
        declared = _declared_settings()
        unknown = sorted(n for n in _QUARANTINE if n not in declared)
        assert not unknown, f"_QUARANTINE names settings that no longer exist: {unknown}"

    def test_quarantine_does_not_grow(self):
        """A ratchet. Raising this number is a decision, not an accident."""
        assert len(_QUARANTINE) <= 5, (
            "The dead-knob list grew. Every entry is a control an operator can set and "
            "get nothing from — wire the new one up instead of admitting it."
        )

    def test_every_security_setting_is_env_addressable(self):
        declared = _declared_settings()
        missing = sorted(
            name for name in _SECURITY_SETTINGS if not declared.get(name)
        )
        assert not missing, f"Security settings with no env alias: {missing}"


class TestEnforcementIsNotAspirational:
    def test_no_default_apparmor_profile_that_does_not_ship(self):
        """A default naming a profile absent from the repo cannot ever be applied: on a
        host without it loaded, Docker refuses to create the container. Ship the profile
        or do not name one.
        """
        hardening = (_PACKAGE / "docker" / "hardening.py").read_text(encoding="utf-8")
        # Look for the assignment, not the name: prose explaining why there is no
        # default must not read as one being declared.
        if not re.search(r"^\s*DEFAULT_APPARMOR_PROFILE\s*=", hardening, re.MULTILINE):
            pytest.skip("no default apparmor profile is claimed")
        profiles = list(_CLIENT_ROOT.glob("docker/apparmor/*"))
        assert profiles, (
            "hardening.py names a default AppArmor profile but no profile ships under "
            "client/docker/apparmor/. Either ship it or drop the claim."
        )

    def test_seccomp_profile_ships(self):
        """The seccomp default *is* real — this pins that it stays that way."""
        assert (_CLIENT_ROOT / "docker" / "seccomp" / "agent.json").is_file()

    def test_sandbox_always_resolves_a_network_policy(self):
        """The regression: an Optional policy meant both call sites silently had none."""
        from squadx_client.docker.sandbox import AgentSandbox

        sandbox = AgentSandbox.__new__(AgentSandbox)
        assert sandbox._resolve_policy(None) is not None
        assert sandbox._resolve_policy("nonexistent-policy") is not None


class TestDockerSdkIsolation:
    """ADR-0009 Phase 2: Docker SDK stays under docker/ (+ sanctioned backends)."""

    # Files allowed to import the Docker Python SDK (or docker.errors / docker.models).
    _ALLOWED_DOCKER_SDK_IMPORT = {
        _PACKAGE / "docker" / "manager.py",
        # Future: keep empty unless a module must call docker.from_env() directly.
    }

    def test_docker_sdk_not_imported_outside_sanctioned_modules(self):
        offenders: list[str] = []
        for path in _PACKAGE.rglob("*.py"):
            if "__pycache__" in path.parts:
                continue
            # Entire docker/ package is the Docker integration surface.
            if "docker" in path.parts:
                continue
            text = path.read_text(encoding="utf-8")
            if re.search(
                r"^\s*(import docker\b|from docker\b)",
                text,
                re.MULTILINE,
            ):
                if path not in self._ALLOWED_DOCKER_SDK_IMPORT:
                    offenders.append(str(path.relative_to(_PACKAGE)))
        assert not offenders, (
            "Docker SDK import outside docker/ package (ADR-0009): "
            f"{offenders}. Route through DockerManager / DockerSandboxBackend."
        )

    def test_production_call_sites_use_sandbox_factory(self):
        """Daemon and orchestrator must use the session factory, not AgentSandbox()."""
        for rel in ("orchestrator/nodes.py", "daemon.py"):
            text = (_PACKAGE / rel).read_text(encoding="utf-8")
            assert "create_sandbox_session" in text, f"{rel} should call create_sandbox_session"
            assert not re.search(r"\bAgentSandbox\s*\(", text), (
                f"{rel} must not construct AgentSandbox(...) directly"
            )

    def test_agent_sandbox_construction_is_contained(self):
        """Production packages outside docker/sandbox + docker_backend must not build AgentSandbox."""
        # Entire docker/ package may reference AgentSandbox; factory surface is docker_backend.
        allowed_prefixes = (
            "docker/",
            "sandbox/docker_backend.py",
            "sandbox/docker_session.py",
        )
        offenders: list[str] = []
        for path in _PACKAGE.rglob("*.py"):
            if "__pycache__" in path.parts:
                continue
            rel = str(path.relative_to(_PACKAGE)).replace("\\", "/")
            if any(rel == p or rel.startswith(p) for p in allowed_prefixes):
                continue
            text = path.read_text(encoding="utf-8")
            if re.search(r"\bAgentSandbox\s*\(", text):
                offenders.append(rel)
        assert not offenders, (
            "AgentSandbox(...) outside sanctioned modules: "
            f"{offenders}. Use create_sandbox_session() / DockerSandboxBackend."
        )
