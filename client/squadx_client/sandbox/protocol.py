"""``SandboxBackend`` Protocol — pluggable agent isolation (ADR-0009).

Production agents use ``create_sandbox_session()`` → ``SandboxSession``.
Handle-based methods remain for multi-session control and tests.
"""

from __future__ import annotations

from collections.abc import Callable
from typing import Any, Protocol, runtime_checkable

from squadx_client.sandbox.session import SandboxSession
from squadx_client.sandbox.types import (
    ExecResult,
    SandboxBackendKind,
    SandboxHandle,
    SandboxLifecycleStatus,
)


@runtime_checkable
class SandboxBackend(Protocol):
    """Isolation backend: create sessions, optional handle-based control.

    Implementations must be safe for concurrent sandboxes (one handle per task).
    Network policy semantics differ by backend (Docker sidecar vs PROCESS proxy);
    see ADR-0009 feature matrix.
    """

    @property
    def kind(self) -> SandboxBackendKind:
        """Backend identifier (docker | process | firecracker | remote)."""
        ...

    def create_session(
        self,
        *,
        task_id: int,
        agent_type: str,
        workspace_path: str,
        network_policy: str | None = None,
        enable_live_streaming: bool = True,
        ttl_seconds: int | None = None,
    ) -> SandboxSession:
        """Build an unstarted session (orchestrator / factory hot path)."""
        ...

    def supports_live_view(self) -> bool:
        """Whether this backend can expose VNC/WebRTC live view."""
        ...

    def supports_egress_sidecar(self) -> bool:
        """Whether RFC-0006 Docker netns sidecar can apply."""
        ...

    async def start(
        self,
        *,
        task_id: int,
        agent_type: str,
        workspace_path: str,
        network_policy: str | None = None,
        enable_live: bool = False,
        memory_limit: str | None = None,
        cpu_limit: float | None = None,
        environment: dict[str, str] | None = None,
        exec_env: dict[str, str] | None = None,
        ttl_seconds: int | None = None,
    ) -> SandboxHandle:
        """Provision and start a sandbox for ``task_id``.

        ``exec_env`` holds secrets (provider keys) applied per-exec when possible;
        ``environment`` is non-secret create-time context.
        """
        ...

    async def exec(
        self,
        handle: SandboxHandle,
        command: list[str] | str,
        *,
        workdir: str | None = None,
        env: dict[str, str] | None = None,
        timeout: float | None = None,
    ) -> ExecResult:
        """Run a command to completion and return stdout/stderr + exit code."""
        ...

    async def exec_streaming(
        self,
        handle: SandboxHandle,
        command: list[str] | str,
        *,
        on_output: Callable[[str], Any] | None = None,
        workdir: str | None = None,
        env: dict[str, str] | None = None,
        timeout: float | None = None,
    ) -> ExecResult:
        """Run a command, streaming output chunks via ``on_output`` when provided."""
        ...

    async def write_file(self, handle: SandboxHandle, path: str, content: str) -> bool:
        """Write a file inside the sandbox workspace (path relative or absolute in sandbox)."""
        ...

    async def read_file(self, handle: SandboxHandle, path: str) -> str | None:
        """Read a file from the sandbox; ``None`` if missing."""
        ...

    async def list_dir(self, handle: SandboxHandle, path: str) -> list[str]:
        """List directory entries inside the sandbox."""
        ...

    async def stop(self, handle: SandboxHandle, *, timeout: int = 10) -> bool:
        """Stop the sandbox; resources may remain until ``cleanup``."""
        ...

    async def cleanup(self, handle: SandboxHandle) -> bool:
        """Release all resources for the handle (containers, processes, temp dirs)."""
        ...

    def get_metrics(self, handle: SandboxHandle) -> dict[str, Any] | None:
        """Optional resource metrics; ``None`` if unsupported."""
        ...

    async def status(self, handle: SandboxHandle) -> SandboxLifecycleStatus:
        """Current lifecycle status for the handle."""
        ...


__all__ = ["SandboxBackend"]
