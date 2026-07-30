"""Thin ``SandboxSession`` adapter over Docker ``AgentSandbox``.

Keeps container/egress/pool logic inside ``docker/sandbox.py`` while production
call sites only depend on the session contract + settings defaults for start.
"""

from __future__ import annotations

from collections.abc import Callable
from typing import Any

from squadx_client.config import settings
from squadx_client.docker.sandbox import AgentSandbox, SandboxResult


class DockerSandboxSession:
    """Facade: ``SandboxSession`` API + getattr pass-through to ``AgentSandbox``."""

    __slots__ = ("_inner",)

    def __init__(self, inner: AgentSandbox) -> None:
        self._inner = inner

    @property
    def inner(self) -> AgentSandbox:
        """Underlying Docker implementation (tests / rare docker-only callers)."""
        return self._inner

    @property
    def live_join_code(self) -> str | None:
        return self._inner.live_join_code

    @property
    def task_id(self) -> int:
        return self._inner.task_id

    @property
    def agent_type(self) -> str:
        return self._inner.agent_type

    @property
    def workspace_path(self) -> str:
        return self._inner.workspace_path

    async def start(
        self,
        image: str | None = None,
        memory_limit: str | None = None,
        cpu_limit: float | None = None,
        enable_vnc: bool | None = None,
        environment: dict | None = None,
        exec_env: dict | None = None,
    ) -> bool:
        """Start with settings defaults when kwargs are omitted."""
        return await self._inner.start(
            image=image if image is not None else str(settings.agent_image),
            memory_limit=(
                memory_limit
                if memory_limit is not None
                else str(settings.agent_memory_limit)
            ),
            cpu_limit=(
                float(cpu_limit)
                if cpu_limit is not None
                else float(settings.agent_cpu_limit)
            ),
            enable_vnc=(
                bool(enable_vnc)
                if enable_vnc is not None
                else bool(settings.enable_vnc)
            ),
            environment=environment,
            exec_env=exec_env,
        )

    async def execute(
        self,
        command: list[str],
        workdir: str = "/workspace",
        timeout: float = 300,
    ) -> SandboxResult:
        return await self._inner.execute(command, workdir=workdir, timeout=timeout)

    async def execute_streaming(
        self,
        command: list[str],
        on_output: Callable[[str], Any] | None = None,
        workdir: str = "/workspace",
        timeout: float = 1800,
    ) -> SandboxResult:
        return await self._inner.execute_streaming(
            command, on_output=on_output, workdir=workdir, timeout=timeout
        )

    async def write_file(self, path: str, content: str) -> bool:
        return await self._inner.write_file(path, content)

    async def read_file(self, path: str) -> str | None:
        return await self._inner.read_file(path)

    async def cleanup(self) -> bool:
        return await self._inner.cleanup()

    async def stop(self, timeout: int = 10) -> bool:
        return await self._inner.stop(timeout=timeout)

    def __getattr__(self, name: str) -> Any:
        # Docker-only attributes (container_id, sidecar_id, file_ops, …)
        return getattr(self._inner, name)
