"""Resolve configured sandbox backend kind and feature matrix (ADR-0009)."""

from __future__ import annotations

from dataclasses import dataclass
from typing import Any

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
        notes="Default Team DOCKER / VPS; DockerSandboxBackend → AgentSandbox",
    ),
    SandboxBackendKind.PROCESS: BackendFeatures(
        kind=SandboxBackendKind.PROCESS,
        live_view=False,
        egress_sidecar=False,
        external_cli=False,
        implemented=True,
        notes="Dev LIGHT: bubblewrap (Linux) / Seatbelt (macOS); no Live View / External CLI",
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
    """Return a live ``SandboxBackend`` for the configured kind."""
    kind = get_sandbox_backend_kind()
    feats = features_for(kind)
    if not feats.implemented:
        raise SandboxNotSupportedError(
            f"sandbox backend {kind.value!r} is not implemented yet "
            f"({feats.notes}). Use SQUADX_SANDBOX_BACKEND=docker."
        )
    if kind is SandboxBackendKind.DOCKER:
        from squadx_client.sandbox.docker_backend import DockerSandboxBackend

        return DockerSandboxBackend()
    if kind is SandboxBackendKind.PROCESS:
        from squadx_client.sandbox.process_backend import ProcessSandboxBackend

        return ProcessSandboxBackend()
    raise SandboxNotSupportedError(
        f"sandbox backend {kind.value!r} marked implemented but has no factory branch"
    )


def create_agent_sandbox(
    *,
    task_id: int,
    agent_type: str,
    workspace_path: str,
    network_policy: str | None = None,
    enable_live_streaming: bool = True,
    ttl_seconds: int | None = None,
):
    """Create a Docker ``AgentSandbox`` only.

    Prefer ``create_sandbox_session`` when PROCESS may be selected.
    """
    kind = get_sandbox_backend_kind()
    if kind is not SandboxBackendKind.DOCKER:
        raise SandboxNotSupportedError(
            f"create_agent_sandbox requires docker backend; got {kind.value!r}. "
            f"External CLI and Live View need SQUADX_SANDBOX_BACKEND=docker. "
            f"For native agents use create_sandbox_session() with process."
        )
    from squadx_client.sandbox.docker_backend import DockerSandboxBackend

    backend = get_sandbox_backend()
    assert isinstance(backend, DockerSandboxBackend)
    return backend.create_session(
        task_id=task_id,
        agent_type=agent_type,
        workspace_path=workspace_path,
        network_policy=network_policy,
        enable_live_streaming=enable_live_streaming,
        ttl_seconds=ttl_seconds,
    )


def create_sandbox_session(
    *,
    task_id: int,
    agent_type: str,
    workspace_path: str,
    network_policy: str | None = None,
    enable_live_streaming: bool = True,
    ttl_seconds: int | None = None,
) -> Any:
    """Create a sandbox session for the configured backend.

    Returns ``AgentSandbox`` (docker) or ``ProcessSession`` (process). Both expose
    ``start`` / ``execute`` / ``write_file`` / ``read_file`` / ``cleanup`` used by
    LangGraph tools. Live View fields are always ``None`` on process.
    """
    kind = get_sandbox_backend_kind()
    if kind is SandboxBackendKind.DOCKER:
        return create_agent_sandbox(
            task_id=task_id,
            agent_type=agent_type,
            workspace_path=workspace_path,
            network_policy=network_policy,
            enable_live_streaming=enable_live_streaming,
            ttl_seconds=ttl_seconds,
        )
    if kind is SandboxBackendKind.PROCESS:
        from squadx_client.sandbox.process_backend import ProcessSandboxBackend

        backend = get_sandbox_backend()
        assert isinstance(backend, ProcessSandboxBackend)
        return backend.create_session(
            task_id=task_id,
            agent_type=agent_type,
            workspace_path=workspace_path,
            network_policy=network_policy,
            enable_live_streaming=enable_live_streaming,
            ttl_seconds=ttl_seconds,
        )
    raise SandboxNotSupportedError(
        f"create_sandbox_session: backend {kind.value!r} not available"
    )
