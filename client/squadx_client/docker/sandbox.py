"""Agent sandbox execution environment (Docker).

Types: ``sandbox_types`` · egress: ``sandbox_egress`` · exec: ``sandbox_exec``.
"""

from __future__ import annotations

import asyncio
import logging
from collections.abc import Callable
from typing import TYPE_CHECKING, Any

from squadx_client.config import settings

from .file_ops import SandboxFileOps
from .hardening import SandboxRuntime, get_runtime_config, resolve_runtime
from .manager import ContainerConfig, DockerManager, docker_manager
from .metrics import ContainerMetrics, ContainerMetricsCollector
from .network_policy import NetworkPolicy, get_predefined_policy
from .sandbox_egress import apply_sidecar_policy, teardown_sidecar
from .sandbox_exec import exec_command, exec_command_streaming
from .sandbox_types import SandboxResult, SandboxStatus

# Re-export for existing ``from ...sandbox import SandboxResult, SandboxStatus``
__all__ = ["AgentSandbox", "SandboxResult", "SandboxStatus"]

if TYPE_CHECKING:
    from .pool import PooledContainer

logger = logging.getLogger(__name__)


class AgentSandbox:
    """Docker container lifecycle for agent tasks (egress, pool, live view).

    Production call sites should use ``create_sandbox_session()`` →
    ``DockerSandboxSession``, which wraps this class. Prefer not constructing
    ``AgentSandbox`` directly outside tests and ``DockerSandboxBackend``.
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
        image: str = "squadx/agent:latest",
        memory_limit: str = "2g",
        cpu_limit: float = 2.0,
        enable_vnc: bool = True,
        environment: dict | None = None,
        exec_env: dict | None = None,
    ) -> bool:
        """Start the sandbox container.

        ``environment`` is baked into the container at create time — use it only for
        non-secret, per-container context. ``exec_env`` carries secrets (provider API
        keys) and is applied per ``execute``/``execute_streaming`` call instead, so it
        never lands in container metadata and works on pooled containers.
        """
        if self.status not in (SandboxStatus.CREATED, SandboxStatus.STOPPED, SandboxStatus.ERROR):
            logger.warning(f"Cannot start sandbox in status: {self.status}")
            return False

        self._set_status(SandboxStatus.STARTING)
        self._exec_env = dict(exec_env or {})

        try:
            # Ensure Docker connection
            if not self.manager.client:
                if not await self.manager.connect():
                    self._set_status(SandboxStatus.ERROR)
                    return False

            # Get runtime-specific configuration overrides
            runtime_kwargs = get_runtime_config(self.runtime)
            logger.info(
                f"Starting sandbox for task {self.task_id} with "
                f"runtime={self.runtime.value}"
            )

            # Create container config
            config = ContainerConfig(
                image=image,
                memory_limit=memory_limit,
                cpu_limit=cpu_limit,
                enable_vnc=enable_vnc,
                environment=environment or {},
                volumes={
                    self.workspace_path: {
                        "bind": "/workspace",
                        "mode": "rw",
                    }
                },
                **runtime_kwargs,
            )

            # Pool fast path: if the manager has a warm pool, get a started
            # container in sub-second instead of paying the 10-20s cold start.
            # Falls back to create+start on any failure so the daemon never
            # depends on the pool being healthy.
            sidecar_enabled = getattr(settings, "egress_sidecar_enabled", False)

            warm_pool = getattr(self.manager, "warm_pool", None)
            used_pool = False
            # The egress sidecar must own the netns at agent-create time. The pool
            # satisfies that by pre-creating the agent already joined to a sidecar, so
            # both compose: the policy is applied via the pre-start hook below, while
            # the agent is still `created` and cannot execute anything (RFC-0006).
            if warm_pool is not None and warm_pool.is_enabled:
                try:
                    pooled = await warm_pool.acquire(
                        task_id=self.task_id,
                        agent_type=self.agent_type,
                        before_start=self._apply_pooled_policy if sidecar_enabled else None,
                    )
                    self.container_id = pooled.container_id
                    self.sidecar_id = pooled.sidecar_id
                    self._pooled_container = pooled
                    used_pool = True
                    logger.info(
                        f"sandbox_acquired_from_pool task={self.task_id} "
                        f"container_id={pooled.container_id[:12]} "
                        f"use_count={pooled.use_count}"
                    )
                except Exception as e:  # noqa: BLE001 - cold fallback is below
                    if sidecar_enabled and not getattr(settings, "egress_fail_open", False):
                        # Falling back to a cold, unfiltered sandbox would defeat the
                        # whole point of the policy the pool just refused to apply.
                        logger.error(
                            f"warm_pool_acquire_failed_fail_closed task={self.task_id} "
                            f"error={e}"
                        )
                        self._set_status(SandboxStatus.ERROR)
                        return False
                    logger.warning(
                        f"warm_pool_acquire_failed_falling_back task={self.task_id} "
                        f"error={e}"
                    )

            if not used_pool:
                # RFC-0006: bring up the egress sidecar first so the agent can join its
                # netns. The sidecar publishes the VNC port the agent would expose.
                if sidecar_enabled:
                    published = {f"{config.vnc_port}/tcp": None} if enable_vnc else {}
                    self.sidecar_id = await self.manager.create_egress_sidecar(
                        task_id=self.task_id,
                        agent_type=self.agent_type,
                        published_ports=published,
                    )
                    if not self.sidecar_id:
                        logger.error(
                            f"egress_sidecar_failed_fail_closed task={self.task_id}"
                        )
                        self._set_status(SandboxStatus.ERROR)
                        return False

                    # Apply the egress policy on the sidecar (it holds NET_ADMIN) BEFORE
                    # the agent joins the netns, so the agent never runs with open egress.
                    # FAIL-CLOSED: abort the run if the policy cannot be applied.
                    if not await self._apply_sidecar_policy():
                        await self._teardown_sidecar()
                        self._set_status(SandboxStatus.ERROR)
                        return False

                # Create and start container
                self.container_id = await self.manager.create_container(
                    config=config,
                    task_id=self.task_id,
                    agent_type=self.agent_type,
                    netns_container=self.sidecar_id,
                )

                if not self.container_id:
                    self._set_status(SandboxStatus.ERROR)
                    return False

                if not await self.manager.start_container(self.container_id):
                    self._set_status(SandboxStatus.ERROR)
                    return False

            # Without the sidecar there is nowhere to enforce egress: the agent is
            # cap-drop ALL (no NET_ADMIN, and it cannot be regained via exec) and its
            # /tmp is noexec, so the in-agent iptables path cannot work by construction
            # — see egress_guard's module docstring. Say so loudly rather than run a
            # best-effort apply that always fails and reads like enforcement.
            if not sidecar_enabled:
                logger.error(
                    f"egress_unenforced task={self.task_id} "
                    f"policy={self._network_policy.default_action.value} — the egress "
                    f"sidecar is disabled, so the agent has UNRESTRICTED network access "
                    f"(only host-side cloud-metadata rules apply, where the host supports "
                    f"them). Set SQUADX_EGRESS_SIDECAR=true. See RFC-0006 / ADR-0008."
                )

            # Initialize enhanced file operations and metrics collector
            assert self.container_id is not None  # set by create_container above
            if self.manager.client:
                self.file_ops = SandboxFileOps(self.manager.client, self.container_id)
                self.metrics_collector = ContainerMetricsCollector(self.manager.client)

            # Get VNC port if enabled. With the sidecar the port is published on the
            # sidecar (the agent has no own network stack), so query it there.
            if enable_vnc:
                await asyncio.sleep(2)  # Wait for VNC to be ready
                vnc_host_container = self.sidecar_id or self.container_id
                self.vnc_port = await self.manager.get_vnc_port(vnc_host_container)
                logger.info(f"VNC available on port: {self.vnc_port}")

                # Start live streaming if enabled. The stream is keyed by the agent
                # container (that is what stop/cleanup tear down), but the port was
                # resolved above from whichever container actually publishes it —
                # the sidecar, when it owns the netns. Pass it explicitly rather than
                # letting the manager re-resolve it against the agent, which has no
                # published ports under RFC-0006 and would silently yield None.
                if self.enable_live_streaming and self.vnc_port:
                    self.live_join_code = await self.manager.start_live_stream(
                        container_id=self.container_id,
                        task_id=self.task_id,
                        vnc_port=self.vnc_port,
                    )
                    if self.live_join_code:
                        logger.info(f"Live streaming available: {self.live_join_code}")

            self._set_status(SandboxStatus.RUNNING)
            logger.info(f"Sandbox started for task {self.task_id}")
            return True

        except Exception as e:
            logger.error(f"Failed to start sandbox: {e}")
            self._set_status(SandboxStatus.ERROR)
            return False

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
