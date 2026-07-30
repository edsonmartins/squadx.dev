"""Hot-path session contract used by orchestrator, tools, and External CLI.

This is the *real* production boundary (ADR-0009 hardening). Backend Protocol
methods that take ``SandboxHandle`` remain for multi-session control; call sites
that drive agents use ``SandboxSession`` via ``create_sandbox_session()``.
"""

from __future__ import annotations

from collections.abc import Callable
from typing import Any, Protocol, runtime_checkable

from squadx_client.sandbox.types import ExecResult


@runtime_checkable
class SandboxSession(Protocol):
    """Duck-typed session: Docker ``AgentSandbox`` or PROCESS ``ProcessSession``."""

    live_join_code: str | None

    async def start(
        self,
        image: str = ...,
        memory_limit: str = ...,
        cpu_limit: float = ...,
        enable_vnc: bool = ...,
        environment: dict | None = ...,
        exec_env: dict | None = ...,
    ) -> bool:
        """Start the session. PROCESS ignores image/memory/cpu/vnc."""
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
