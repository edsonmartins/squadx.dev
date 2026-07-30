"""Hot-path session contract used by orchestrator, tools, and External CLI.

Production boundary (ADR-0009): ``create_sandbox_session()`` → ``SandboxSession``.
``SandboxBackend`` only constructs sessions and advertises capabilities.
"""

from __future__ import annotations

from collections.abc import Callable
from typing import Any, Protocol, runtime_checkable

from squadx_client.sandbox.types import ExecResult


@runtime_checkable
class SandboxSession(Protocol):
    """Duck-typed session: ``DockerSandboxSession`` or PROCESS ``ProcessSession``."""

    @property
    def live_join_code(self) -> str | None:
        """WebRTC join code when Live View is active; always None for PROCESS."""
        ...

    async def start(
        self,
        image: str | None = ...,
        memory_limit: str | None = ...,
        cpu_limit: float | None = ...,
        enable_vnc: bool | None = ...,
        environment: dict | None = ...,
        exec_env: dict | None = ...,
    ) -> bool:
        """Start the session.

        Docker: omitted image/memory/cpu/vnc come from settings.
        PROCESS: image/memory/cpu/vnc are ignored.
        """
        ...

    async def execute(
        self,
        command: list[str],
        workdir: str = ...,
        timeout: float = ...,
    ) -> ExecResult | Any:
        """Run a command; result exposes success/exit_code/output/error."""
        ...

    async def execute_streaming(
        self,
        command: list[str],
        on_output: Callable[[str], Any] | None = ...,
        workdir: str = ...,
        timeout: float = ...,
    ) -> ExecResult | Any: ...

    async def write_file(self, path: str, content: str) -> bool: ...

    async def read_file(self, path: str) -> str | None: ...

    async def cleanup(self) -> bool: ...

    async def stop(self, timeout: int = ...) -> bool: ...
