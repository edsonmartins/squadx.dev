"""Agent sandbox execution environment (Docker).

Types: ``sandbox_types`` · egress: ``sandbox_egress`` · exec: ``sandbox_exec`` ·
start: ``sandbox_start``.
"""

from __future__ import annotations

import logging
from collections.abc import Callable
from typing import TYPE_CHECKING, Any

from squadx_client.config import settings

from .file_ops import SandboxFileOps
from .hardening import SandboxRuntime, resolve_runtime
from .manager import DockerManager, docker_manager
from .metrics import ContainerMetrics, ContainerMetricsCollector
from .network_policy import NetworkPolicy, get_predefined_policy
from .sandbox_egress import apply_sidecar_policy, teardown_sidecar
from .sandbox_exec import exec_command, exec_command_streaming
from .sandbox_start import SandboxStartConfig, start_agent_sandbox
from .sandbox_types import SandboxResult, SandboxStatus

# Re-export for existing ``from ...sandbox import SandboxResult, SandboxStatus``
__all__ = ["AgentSandbox", "SandboxResult", "SandboxStatus"]

if TYPE_CHECKING:
    from .pool import PooledContainer

logger = logging.getLogger(__name__)


class AgentSandbox:
    """Docker container lifecycle for agent tasks (egress, pool, live view).

    Implements the common ``SandboxSession`` contract directly. Production call
    sites should still use ``create_sandbox_session()`` rather than constructing it.
    """

    def __init__(
        self,
        task_id: int,
        agent_type: str,
        workspace_path: str,
        manager: DockerManager | None = None,
        enable_live_streaming: bool = True,
        runtime: SandboxRuntime | None = None,
        network_policy: str | None = None,
        ttl_seconds: int = 3600,
    ):
        self.task_id = task_id
        self.agent_type = agent_type
        self.workspace_path = workspace_path
        self.manager = manager or docker_manager
        self.enable_live_streaming = enable_live_streaming
        self.ttl_seconds = ttl_seconds

        # Resolve sandbox runtime (auto-detect if not specified)
        self.runtime = runtime or resolve_runtime()
        logger.info(
            f"Sandbox for task {task_id} using runtime: {self.runtime.value}"
        )

        # Network policy. Always resolves to a real policy: an unset argument falls back
        # to settings, never to None. Leaving it optional is what let both production
        # call sites silently run with no policy at all for the parameter's whole life —
        # a default that has to be passed to take effect is not a default.
        self._network_policy: NetworkPolicy = self._resolve_policy(network_policy)

        # Per-exec environment (provider API keys). Deliberately NOT container-create
        # env: create-time env is baked into the container for its whole life (visible
        # in `docker inspect` and /proc/1/environ) and is fixed at create time, which
        # is precisely why pre-created warm-pool containers cannot carry credentials.
        # Injecting at exec keeps secrets out of the container's metadata and lets a
        # pooled container serve any run.
        self._exec_env: dict[str, str] = {}

        self.container_id: str | None = None
        # RFC-0006: id of the egress sidecar whose netns the agent joins (when enabled).
        self.sidecar_id: str | None = None
        self.status = SandboxStatus.CREATED
        self.vnc_port: int | None = None
        self.live_join_code: str | None = None

        # Set to the pool handle when the container came from the warm pool;
        # cleanup() uses this to release back to the pool instead of removing.
        self._pooled_container: PooledContainer | None = None

        # Enhanced file operations and metrics (initialized after container start)
        self.file_ops: SandboxFileOps | None = None
        self.metrics_collector: ContainerMetricsCollector | None = None

        self._output_callback: Callable[[str], Any] | None = None
        self._status_callback: Callable[[SandboxStatus], Any] | None = None

    @staticmethod
    def _resolve_policy(name: str | None) -> NetworkPolicy:
        """Resolve a policy name to a policy, falling back to settings then to the
        secure default. Never returns None and never raises: an unknown name degrades
        to the standing default rather than to no enforcement at all.
        """
        configured = str(name or getattr(settings, "network_policy", "agent-default"))
        try:
            return get_predefined_policy(configured)
        except ValueError:
            logger.warning(
                f"unknown_network_policy name={configured!r} — falling back to "
                f"'agent-default' (default-deny + allowlist)"
            )
            return get_predefined_policy("agent-default")

    def on_output(self, callback: Callable[[str], Any]):
        """Register a callback for output."""
        self._output_callback = callback

    def on_status_change(self, callback: Callable[[SandboxStatus], Any]):
        """Register a callback for status changes."""
        self._status_callback = callback

    def _set_status(self, status: SandboxStatus):
        """Update status and notify callback."""
        self.status = status
        if self._status_callback:
            try:
                self._status_callback(status)
            except Exception as e:
                logger.error(f"Status callback error: {e}")

    async def start(
        self,
        *,
        image: str | None = None,
        memory_limit: str | None = None,
        cpu_limit: float | None = None,
        enable_vnc: bool | None = None,
        environment: dict | None = None,
        exec_env: dict | None = None,
    ) -> bool:
        """Start the container, resolving omitted Docker options from settings."""
        return await start_agent_sandbox(
            self,
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
            config=SandboxStartConfig(
                egress_sidecar_enabled=bool(
                    getattr(settings, "egress_sidecar_enabled", False)
                ),
                egress_fail_open=bool(getattr(settings, "egress_fail_open", False)),
            ),
        )
    async def _apply_pooled_policy(self, pooled) -> bool:
        """Pre-start hook for a pooled agent+sidecar pair (RFC-0006)."""
        self.sidecar_id = pooled.sidecar_id
        if not self.sidecar_id:
            logger.error(
                f"pooled_unit_without_sidecar_fail_closed task={self.task_id} "
                f"container_id={pooled.container_id[:12]}"
            )
            return False
        return await self._apply_sidecar_policy()

    async def _apply_sidecar_policy(self) -> bool:
        assert self.sidecar_id is not None
        # Pass settings through this module so tests that patch
        # ``squadx_client.docker.sandbox.settings`` still control fail-open / image.
        return await apply_sidecar_policy(
            manager=self.manager,
            sidecar_id=self.sidecar_id,
            policy=self._network_policy,
            task_id=self.task_id,
            egress_image=getattr(
                settings, "egress_sidecar_image", "squadx/egress-proxy:latest"
            ),
            fail_open=bool(getattr(settings, "egress_fail_open", False)),
        )

    async def _teardown_sidecar(self) -> None:
        await teardown_sidecar(
            manager=self.manager,
            sidecar_id=self.sidecar_id,
            task_id=self.task_id,
        )
        self.sidecar_id = None

    async def stop(self, timeout: int = 10) -> bool:
        """Stop the sandbox container."""
        if not self.container_id:
            return True

        if self.status not in (SandboxStatus.RUNNING, SandboxStatus.ERROR):
            return True

        self._set_status(SandboxStatus.STOPPING)

        try:
            # Stop live streaming first
            if self.live_join_code:
                await self.manager.stop_live_stream(self.container_id)
                self.live_join_code = None

            await self.manager.stop_container(self.container_id, timeout=timeout)
            if self.sidecar_id:
                await self.manager.stop_container(self.sidecar_id, timeout=timeout)
            self._set_status(SandboxStatus.STOPPED)
            logger.info(f"Sandbox stopped for task {self.task_id}")
            return True
        except Exception as e:
            logger.error(f"Failed to stop sandbox: {e}")
            self._set_status(SandboxStatus.ERROR)
            return False

    async def cleanup(self) -> bool:
        """Remove the sandbox container, or return it to the warm pool."""
        if not self.container_id:
            return True

        try:
            # Stop live streaming first
            if self.live_join_code:
                await self.manager.stop_live_stream(self.container_id)
                self.live_join_code = None

            # Pool fast path: hand the container back to the pool so the
            # next task gets a sub-second acquire instead of a 10-20s create.
            if self._pooled_container is not None:
                warm_pool = getattr(self.manager, "warm_pool", None)
                if warm_pool is not None and warm_pool.is_enabled:
                    await warm_pool.release(self._pooled_container)
                    self._pooled_container = None
                    self.container_id = None
                    self.vnc_port = None
                    self._set_status(SandboxStatus.STOPPED)
                    logger.info(
                        f"Sandbox returned to pool for task {self.task_id}"
                    )
                    return True
                # Pool was disabled between acquire and release; fall through
                # to remove. Don't leak the pooled handle.
                self._pooled_container = None

            await self.manager.remove_container(self.container_id, force=True)
            self.container_id = None
            self.vnc_port = None
            # RFC-0006: tear down the egress sidecar alongside the agent.
            await self._teardown_sidecar()
            self._set_status(SandboxStatus.STOPPED)
            logger.info(f"Sandbox cleaned up for task {self.task_id}")
            return True
        except Exception as e:
            logger.error(f"Failed to cleanup sandbox: {e}")
            self._set_status(SandboxStatus.ERROR)
            return False

    async def execute(
        self,
        command: list[str],
        workdir: str = "/workspace",
        timeout: float = 300,
    ) -> SandboxResult:
        """Execute a command in the sandbox."""
        return await exec_command(
            manager=self.manager,
            container_id=self.container_id,
            status=self.status,
            command=command,
            workdir=workdir,
            timeout=timeout,
            exec_env=self._exec_env or None,
            on_output=self._output_callback,
        )

    async def execute_streaming(
        self,
        command: list[str],
        on_output=None,
        workdir: str = "/workspace",
        timeout: float = 1800,
    ) -> SandboxResult:
        """Execute a command, streaming output via ``on_output``."""
        return await exec_command_streaming(
            manager=self.manager,
            container_id=self.container_id,
            status=self.status,
            command=command,
            workdir=workdir,
            timeout=timeout,
            exec_env=self._exec_env or None,
            on_output=on_output,
        )

    async def write_file(self, path: str, content: str) -> bool:
        """Write a file in the sandbox.

        Uses tar-based file_ops when available for binary-safe writes,
        falls back to heredoc/cat approach otherwise.
        """
        if self.file_ops:
            return self.file_ops.write_file(path, content)
        # Fallback: use echo with heredoc to write file
        command = ["sh", "-c", f"cat > {path} << 'SQUADX_EOF'\n{content}\nSQUADX_EOF"]
        result = await self.execute(command)
        return result.success

    async def read_file(self, path: str) -> str | None:
        """Read a file from the sandbox.

        Uses tar-based file_ops when available for binary-safe reads,
        falls back to cat approach otherwise.
        """
        if self.file_ops:
            return self.file_ops.read_file_text(path)
        # Fallback: use cat
        result = await self.execute(["cat", path])
        if result.success:
            return result.output
        return None

    def get_metrics(self) -> ContainerMetrics | None:
        """Get current container resource metrics."""
        if self.metrics_collector and self.container_id:
            return self.metrics_collector.collect(self.container_id)
        return None

    def get_network_policy(self) -> NetworkPolicy | None:
        """Get the network policy applied to this sandbox."""
        return self._network_policy

    async def get_logs(self, tail: int = 100) -> str | None:
        """Get sandbox container logs."""
        if not self.container_id:
            return None
        return await self.manager.get_container_logs(self.container_id, tail=tail)

    async def is_running(self) -> bool:
        """Check if sandbox is running."""
        if not self.container_id:
            return False

        status = await self.manager.get_container_status(self.container_id)
        return status == "running"

    async def __aenter__(self):
        """Async context manager entry."""
        await self.start()
        return self

    async def __aexit__(self, exc_type, exc_val, exc_tb):
        """Async context manager exit."""
        await self.cleanup()
