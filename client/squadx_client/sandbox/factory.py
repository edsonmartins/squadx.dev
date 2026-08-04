"""Resolve sandbox backend + create sessions (ADR-0009).

Single entry for production: ``create_sandbox_session``.
"""

from __future__ import annotations

from collections.abc import Callable
from dataclasses import dataclass

from squadx_client.config import settings
from squadx_client.sandbox.errors import SandboxNotSupportedError
from squadx_client.sandbox.protocol import SandboxBackend
from squadx_client.sandbox.session import SandboxSession
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
    """Product feature matrix row for a backend (doctor / docs / call-site gates)."""

    kind: SandboxBackendKind
    live_view: bool
    egress_sidecar: bool
    external_cli: bool
    requires_docker: bool
    implemented: bool
    notes: str


_FEATURES: dict[SandboxBackendKind, BackendFeatures] = {
    SandboxBackendKind.DOCKER: BackendFeatures(
        kind=SandboxBackendKind.DOCKER,
        live_view=True,
        egress_sidecar=True,
        external_cli=True,
        requires_docker=True,
        implemented=True,
        notes="Default Team DOCKER / VPS; AgentSandbox implements SandboxSession",
    ),
    SandboxBackendKind.PROCESS: BackendFeatures(
        kind=SandboxBackendKind.PROCESS,
        live_view=False,
        egress_sidecar=False,
        external_cli=False,
        requires_docker=False,
        implemented=True,
        notes=(
            "Dev LIGHT best-effort: bwrap/Seatbelt on execute*; host-side write_file "
            "with path containment; no Live View / External CLI"
        ),
    ),
    SandboxBackendKind.FIRECRACKER: BackendFeatures(
        kind=SandboxBackendKind.FIRECRACKER,
        live_view=False,
        egress_sidecar=False,
        external_cli=True,
        requires_docker=True,
        implemented=False,
        notes="Enterprise path; SandboxRuntime.FIRECRACKER exists under Docker only",
    ),
    SandboxBackendKind.REMOTE: BackendFeatures(
        kind=SandboxBackendKind.REMOTE,
        live_view=False,
        egress_sidecar=False,
        external_cli=True,
        requires_docker=False,
        implemented=False,
        notes="Optional BYO cloud sandbox; not core open-source MVP",
    ),
}


def _make_docker() -> SandboxBackend:
    from squadx_client.sandbox.docker_backend import DockerSandboxBackend

    return DockerSandboxBackend()


def _make_process() -> SandboxBackend:
    from squadx_client.sandbox.process import ProcessSandboxBackend

    return ProcessSandboxBackend()


_BACKEND_FACTORIES: dict[SandboxBackendKind, Callable[[], SandboxBackend]] = {
    SandboxBackendKind.DOCKER: _make_docker,
    SandboxBackendKind.PROCESS: _make_process,
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
    """Return a live ``SandboxBackend`` for the configured kind."""
    kind = get_sandbox_backend_kind()
    feats = features_for(kind)
    factory = _BACKEND_FACTORIES.get(kind)
    if not feats.implemented or factory is None:
        raise SandboxNotSupportedError(
            f"sandbox backend {kind.value!r} is not implemented yet "
            f"({feats.notes}). Use SQUADX_SANDBOX_BACKEND=docker."
        )
    return factory()


def create_sandbox_session(
    *,
    task_id: int,
    agent_type: str,
    workspace_path: str,
    network_policy: str | None = None,
    enable_live_streaming: bool = True,
    ttl_seconds: int | None = None,
) -> SandboxSession:
    """Create a session for the configured backend (Docker or PROCESS).

    Canonical production entry for orchestrator and native agents.
    """
    backend = get_sandbox_backend()
    return backend.create_session(
        task_id=task_id,
        agent_type=agent_type,
        workspace_path=workspace_path,
        network_policy=network_policy,
        enable_live_streaming=enable_live_streaming,
        ttl_seconds=ttl_seconds,
    )
