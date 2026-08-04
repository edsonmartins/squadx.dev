"""Hot-path session contract used by orchestrator, tools, and External CLI.

Production boundary (ADR-0009): ``create_sandbox_session()`` → ``SandboxSession``.
``SandboxBackend`` only constructs sessions and advertises capabilities.
"""

from __future__ import annotations

from collections.abc import Callable
from typing import Any, Protocol, runtime_checkable

from squadx_client.sandbox.types import CommandResult


@runtime_checkable
class SandboxSession(Protocol):
    """Common contract implemented by Docker and PROCESS sessions."""

    @property
    def live_join_code(self) -> str | None:
        """WebRTC join code when Live View is active; always None for PROCESS."""
        ...

    async def start(
        self,
        *,
        environment: dict | None = ...,
        exec_env: dict | None = ...,
    ) -> bool:
        """Start the session with backend-independent environment options."""
        ...

    async def execute(
        self,
        command: list[str],
        workdir: str = ...,
        timeout: float = ...,
    ) -> CommandResult:
        """Run a command; result exposes success/exit_code/output/error."""
        ...

    async def execute_streaming(
        self,
        command: list[str],
        on_output: Callable[[str], Any] | None = ...,
        workdir: str = ...,
        timeout: float = ...,
    ) -> CommandResult: ...

    async def write_file(self, path: str, content: str) -> bool: ...

    async def read_file(self, path: str) -> str | None: ...

    async def cleanup(self) -> bool: ...

    async def stop(self, timeout: int = ...) -> bool: ...
