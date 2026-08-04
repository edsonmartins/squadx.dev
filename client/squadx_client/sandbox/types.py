"""Shared types for pluggable sandbox backends (ADR-0009)."""

from __future__ import annotations

from dataclasses import dataclass
from enum import Enum


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
    """Backend-agnostic lifecycle for every sandbox session."""

    CREATED = "created"
    STARTING = "starting"
    RUNNING = "running"
    STOPPING = "stopping"
    STOPPED = "stopped"
    ERROR = "error"


@dataclass
class CommandResult:
    """Backend-independent result of a sandbox command."""

    success: bool
    exit_code: int
    output: str
    error: str | None = None
    duration_seconds: float = 0.0
