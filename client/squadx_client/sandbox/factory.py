"""Resolve configured sandbox backend kind and feature matrix (ADR-0009).

Phase 0: selection + feature flags only. Callers still use ``AgentSandbox`` for
Docker. ``get_sandbox_backend()`` raises for non-docker kinds until Phase 2–4.
"""

from __future__ import annotations

from dataclasses import dataclass

from squadx_client.config import settings
from squadx_client.sandbox.errors import SandboxNotSupportedError
from squadx_client.sandbox.protocol import SandboxBackend
from squadx_client.sandbox.types import SandboxBackendKind

_ALIASES: dict[str, SandboxBackendKind] = {
    "docker": SandboxBackendKind.DOCKER,
    "container": SandboxBackendKind.DOCKER,
    "process": SandboxBackendKind.PROCESS,
    "os": SandboxBackendKind.PROCESS,
    "bwrap": SandboxBackendKind.PROCESS,
    "firecracker": SandboxBackendKind.FIRECRACKER,
    "fc": SandboxBackendKind.FIRECRACKER,
    "microvm": SandboxBackendKind.FIRECRACKER,
    "remote": SandboxBackendKind.REMOTE,
    "e2b": SandboxBackendKind.REMOTE,
}


@dataclass(frozen=True)
class BackendFeatures:
    """Product feature matrix row for a backend (doctor / docs)."""

    kind: SandboxBackendKind
    live_view: bool
    egress_sidecar: bool
    external_cli: bool
    implemented: bool
    notes: str


_FEATURES: dict[SandboxBackendKind, BackendFeatures] = {
    SandboxBackendKind.DOCKER: BackendFeatures(
        kind=SandboxBackendKind.DOCKER,
        live_view=True,
        egress_sidecar=True,
        external_cli=True,
        implemented=True,
        notes="Default Team DOCKER / VPS; AgentSandbox path today",
    ),
    SandboxBackendKind.PROCESS: BackendFeatures(
        kind=SandboxBackendKind.PROCESS,
        live_view=False,
        egress_sidecar=False,
        external_cli=False,
        implemented=False,
        notes="Planned Phase 4 (bubblewrap/Seatbelt); not selectable yet",
    ),
    SandboxBackendKind.FIRECRACKER: BackendFeatures(
        kind=SandboxBackendKind.FIRECRACKER,
        live_view=False,
        egress_sidecar=False,
        external_cli=True,
        implemented=False,
        notes="Enterprise path; SandboxRuntime.FIRECRACKER exists under Docker only",
    ),
    SandboxBackendKind.REMOTE: BackendFeatures(
        kind=SandboxBackendKind.REMOTE,
        live_view=False,
        egress_sidecar=False,
        external_cli=True,
        implemented=False,
        notes="Optional BYO cloud sandbox; not core open-source MVP",
    ),
}


def parse_backend_kind(value: str | None) -> SandboxBackendKind:
    """Parse env/config string into ``SandboxBackendKind`` (default docker)."""
    raw = (value or "docker").strip().lower()
    if not raw:
        return SandboxBackendKind.DOCKER
    kind = _ALIASES.get(raw)
    if kind is None:
        raise ValueError(
            f"unknown SQUADX_SANDBOX_BACKEND={value!r}; "
            f"expected one of: {', '.join(sorted(set(_ALIASES)))}"
        )
    return kind


def get_sandbox_backend_kind() -> SandboxBackendKind:
    """Backend selected by ``settings.sandbox_backend`` (env ``SQUADX_SANDBOX_BACKEND``)."""
    return parse_backend_kind(getattr(settings, "sandbox_backend", "docker"))


def features_for(kind: SandboxBackendKind | None = None) -> BackendFeatures:
    """Feature matrix for ``kind`` (or the configured default)."""
    k = kind or get_sandbox_backend_kind()
    return _FEATURES[k]


def get_sandbox_backend() -> SandboxBackend:
    """Return a live ``SandboxBackend`` instance for the configured kind.

    Phase 0–1: only documents selection. Docker work still goes through
    ``AgentSandbox``; calling this raises until Phase 2 wires
    ``DockerSandboxBackend``.
    """
    kind = get_sandbox_backend_kind()
    feats = features_for(kind)
    if not feats.implemented:
        raise SandboxNotSupportedError(
            f"sandbox backend {kind.value!r} is not implemented yet "
            f"({feats.notes}). Use SQUADX_SANDBOX_BACKEND=docker."
        )
    # Docker is "implemented" via AgentSandbox, but the Protocol adapter lands in Phase 2.
    raise SandboxNotSupportedError(
        "SandboxBackend Protocol adapter not wired yet (ADR-0009 Phase 2). "
        "Production continues to use squadx_client.docker.sandbox.AgentSandbox. "
        f"Configured kind={kind.value!r}."
    )
