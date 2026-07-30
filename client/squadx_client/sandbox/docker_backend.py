"""Docker implementation of ``SandboxBackend`` (ADR-0009 Phase 2).

Wraps the existing ``AgentSandbox`` (egress sidecar, warm pool, live view) behind
the Protocol so PROCESS/REMOTE can plug in later without forking call-site shape.

Production agents still receive an ``AgentSandbox`` instance via
``create_agent_sandbox()`` / ``create_session()`` — LangGraph tools and External
CLI use ``execute`` / ``write_file`` on that object. Protocol methods operate on
``SandboxHandle`` keys registered in this backend.
"""

from __future__ import annotations

import logging
import uuid
from collections.abc import Callable
from typing import TYPE_CHECKING, Any

from squadx_client.config import settings
from squadx_client.sandbox.errors import (
    SandboxExecError,
    SandboxStartError,
)
from squadx_client.sandbox.types import (
    ExecResult,
    SandboxBackendKind,
    SandboxHandle,
    SandboxLifecycleStatus,
)

if TYPE_CHECKING:
    from squadx_client.docker.manager import DockerManager
    from squadx_client.docker.sandbox import AgentSandbox

logger = logging.getLogger(__name__)

_STATUS_MAP = {
    "created": SandboxLifecycleStatus.CREATED,
    "starting": SandboxLifecycleStatus.STARTING,
    "running": SandboxLifecycleStatus.RUNNING,
    "stopping": SandboxLifecycleStatus.STOPPING,
    "stopped": SandboxLifecycleStatus.STOPPED,
    "error": SandboxLifecycleStatus.ERROR,
}


def _normalize_command(command: list[str] | str) -> list[str]:
    if isinstance(command, str):
        return ["bash", "-c", command]
    return list(command)


def _map_status(raw: str | Any) -> SandboxLifecycleStatus:
    key = raw.value if hasattr(raw, "value") else str(raw)
    return _STATUS_MAP.get(key, SandboxLifecycleStatus.ERROR)


class DockerSandboxBackend:
    """``SandboxBackend`` backed by Docker + optional egress sidecar (RFC-0006)."""

    def __init__(self, manager: DockerManager | None = None) -> None:
        self._manager = manager
        self._sessions: dict[str, AgentSandbox] = {}

    @property
    def kind(self) -> SandboxBackendKind:
        return SandboxBackendKind.DOCKER

    def supports_live_view(self) -> bool:
        return True

    def supports_egress_sidecar(self) -> bool:
        return True

    def create_session(
        self,
        *,
        task_id: int,
        agent_type: str,
        workspace_path: str,
        network_policy: str | None = None,
        enable_live_streaming: bool = True,
        ttl_seconds: int | None = None,
    ) -> AgentSandbox:
        """Build an ``AgentSandbox`` without starting it (daemon / orchestrator path).

        Prefer this over constructing ``AgentSandbox`` directly so backend selection
        stays centralized.
        """
        from squadx_client.docker.sandbox import AgentSandbox

        kwargs: dict[str, Any] = {
            "task_id": task_id,
            "agent_type": agent_type,
            "workspace_path": workspace_path,
            "network_policy": network_policy,
            "enable_live_streaming": enable_live_streaming,
        }
        if self._manager is not None:
            kwargs["manager"] = self._manager
        if ttl_seconds is not None:
            kwargs["ttl_seconds"] = ttl_seconds
        return AgentSandbox(**kwargs)

    def register_session(self, sandbox: AgentSandbox) -> SandboxHandle:
        """Register an already-started ``AgentSandbox`` and return a Protocol handle."""
        handle_id = sandbox.container_id or f"task-{sandbox.task_id}-{uuid.uuid4().hex[:8]}"
        self._sessions[handle_id] = sandbox
        return SandboxHandle(
            task_id=sandbox.task_id,
            backend=SandboxBackendKind.DOCKER,
            id=handle_id,
            workspace_path=sandbox.workspace_path,
            status=_map_status(sandbox.status),
            vnc_port=sandbox.vnc_port,
            live_join_code=sandbox.live_join_code,
            metadata={
                "container_id": sandbox.container_id,
                "sidecar_id": sandbox.sidecar_id,
                "agent_type": sandbox.agent_type,
            },
        )

    def get_session(self, handle: SandboxHandle) -> AgentSandbox:
        """Return the underlying ``AgentSandbox`` for a handle."""
        try:
            return self._sessions[handle.id]
        except KeyError as e:
            raise SandboxExecError(f"unknown sandbox handle id={handle.id!r}") from e

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
        sandbox = self.create_session(
            task_id=task_id,
            agent_type=agent_type,
            workspace_path=workspace_path,
            network_policy=network_policy,
            enable_live_streaming=enable_live,
            ttl_seconds=ttl_seconds,
        )
        mem = memory_limit if memory_limit is not None else str(
            getattr(settings, "agent_memory_limit", "2g")
        )
        cpu = (
            float(cpu_limit)
            if cpu_limit is not None
            else float(getattr(settings, "agent_cpu_limit", 2.0))
        )
        ok = await sandbox.start(
            image=str(getattr(settings, "agent_image", "squadx/agent:latest")),
            memory_limit=mem,
            cpu_limit=cpu,
            enable_vnc=bool(enable_live and getattr(settings, "enable_vnc", True)),
            environment=environment,
            exec_env=exec_env,
        )
        if not ok:
            raise SandboxStartError(
                f"Docker sandbox failed to start task_id={task_id} agent_type={agent_type}"
            )
        return self.register_session(sandbox)

    async def exec(
        self,
        handle: SandboxHandle,
        command: list[str] | str,
        *,
        workdir: str | None = None,
        env: dict[str, str] | None = None,
        timeout: float | None = None,
    ) -> ExecResult:
        sandbox = self.get_session(handle)
        result = await self._run_execute(
            sandbox,
            _normalize_command(command),
            workdir=workdir or "/workspace",
            env=env,
            timeout=timeout if timeout is not None else 300.0,
            streaming=False,
            on_output=None,
        )
        return result

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
        sandbox = self.get_session(handle)
        return await self._run_execute(
            sandbox,
            _normalize_command(command),
            workdir=workdir or "/workspace",
            env=env,
            timeout=timeout if timeout is not None else 1800.0,
            streaming=True,
            on_output=on_output,
        )

    async def _run_execute(
        self,
        sandbox: AgentSandbox,
        command: list[str],
        *,
        workdir: str,
        env: dict[str, str] | None,
        timeout: float,
        streaming: bool,
        on_output: Callable[[str], Any] | None,
    ) -> ExecResult:
        old_env = sandbox._exec_env
        if env:
            sandbox._exec_env = {**(old_env or {}), **env}
        try:
            if streaming:
                result = await sandbox.execute_streaming(
                    command,
                    on_output=on_output,
                    workdir=workdir,
                    timeout=timeout,
                )
            else:
                result = await sandbox.execute(command, workdir=workdir, timeout=timeout)
        finally:
            if env:
                sandbox._exec_env = old_env

        return ExecResult(
            success=result.success,
            exit_code=result.exit_code,
            output=result.output,
            error=result.error,
            duration_seconds=result.duration_seconds,
        )

    async def write_file(self, handle: SandboxHandle, path: str, content: str) -> bool:
        return await self.get_session(handle).write_file(path, content)

    async def read_file(self, handle: SandboxHandle, path: str) -> str | None:
        return await self.get_session(handle).read_file(path)

    async def list_dir(self, handle: SandboxHandle, path: str) -> list[str]:
        result = await self.exec(
            handle,
            ["ls", "-1A", path],
            workdir="/",
            timeout=30.0,
        )
        if not result.success:
            return []
        return [line for line in result.output.splitlines() if line.strip()]

    async def stop(self, handle: SandboxHandle, *, timeout: int = 10) -> bool:
        return await self.get_session(handle).stop(timeout=timeout)

    async def cleanup(self, handle: SandboxHandle) -> bool:
        sandbox = self.get_session(handle)
        ok = await sandbox.cleanup()
        self._sessions.pop(handle.id, None)
        return ok

    def get_metrics(self, handle: SandboxHandle) -> dict[str, Any] | None:
        metrics = self.get_session(handle).get_metrics()
        if metrics is None:
            return None
        if hasattr(metrics, "__dict__"):
            return dict(vars(metrics))
        if isinstance(metrics, dict):
            return metrics
        return {"raw": str(metrics)}

    async def status(self, handle: SandboxHandle) -> SandboxLifecycleStatus:
        sandbox = self.get_session(handle)
        return _map_status(sandbox.status)
