"""Shared types for pluggable sandbox backends (ADR-0009)."""

from __future__ import annotations

from dataclasses import dataclass, field
from enum import Enum
from typing import Any


class SandboxBackendKind(str, Enum):
    """Which isolation backend the daemon uses for agent workspaces.

    Distinct from ``SandboxRuntime`` (docker/gvisor/firecracker under the *Docker*
    backend). See ADR-0009.
    """

    DOCKER = "docker"
    PROCESS = "process"
    FIRECRACKER = "firecracker"
    REMOTE = "remote"


class SandboxLifecycleStatus(str, Enum):
    """Backend-agnostic lifecycle for a sandbox handle."""

    CREATED = "created"
    STARTING = "starting"
    RUNNING = "running"
    STOPPING = "stopping"
    STOPPED = "stopped"
    ERROR = "error"


@dataclass(frozen=True)
class SandboxHandle:
    """Opaque handle returned by ``SandboxBackend.start``.

    Callers must not inspect backend-specific ids beyond logging.
    """

    task_id: int
    backend: SandboxBackendKind
    id: str
    workspace_path: str
    status: SandboxLifecycleStatus = SandboxLifecycleStatus.RUNNING
    # Optional live-view join metadata (only when backend supports it)
    vnc_port: int | None = None
    live_join_code: str | None = None
    metadata: dict[str, Any] = field(default_factory=dict)


@dataclass
class ExecResult:
    """Result of a one-shot ``exec`` in a sandbox."""

    success: bool
    exit_code: int
    output: str
    error: str | None = None
    duration_seconds: float = 0.0
