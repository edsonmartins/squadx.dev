"""Agent sandbox execution environment."""

import asyncio
import logging
from typing import Optional, Callable, Any
from dataclasses import dataclass
from enum import Enum

from .hardening import SandboxRuntime, get_runtime_config, resolve_runtime
from .manager import DockerManager, ContainerConfig, docker_manager
from .lifecycle import SandboxLifecycleManager, SandboxState, SandboxInfo
from .file_ops import SandboxFileOps
from .metrics import ContainerMetricsCollector, ContainerMetrics
from .network_policy import NetworkPolicy, get_predefined_policy

logger = logging.getLogger(__name__)


class SandboxStatus(str, Enum):
    """Sandbox status."""

    CREATED = "created"
    STARTING = "starting"
    RUNNING = "running"
    STOPPING = "stopping"
    STOPPED = "stopped"
    ERROR = "error"


@dataclass
class SandboxResult:
    """Result of a sandbox execution."""

    success: bool
    exit_code: int
    output: str
    error: Optional[str] = None
    duration_seconds: float = 0.0


class AgentSandbox:
    """Sandbox environment for running agent tasks."""

    def __init__(
        self,
        task_id: int,
        agent_type: str,
        workspace_path: str,
        manager: Optional[DockerManager] = None,
        enable_live_streaming: bool = True,
        runtime: Optional[SandboxRuntime] = None,
        network_policy: Optional[str] = None,
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

        # Network policy
        self._network_policy: Optional[NetworkPolicy] = None
        if network_policy:
            try:
                self._network_policy = get_predefined_policy(network_policy)
            except ValueError:
                logger.warning(f"Unknown network policy '{network_policy}', using none")

        self.container_id: Optional[str] = None
        self.status = SandboxStatus.CREATED
        self.vnc_port: Optional[int] = None
        self.live_join_code: Optional[str] = None

        # Enhanced file operations and metrics (initialized after container start)
        self.file_ops: Optional[SandboxFileOps] = None
        self.metrics_collector: Optional[ContainerMetricsCollector] = None

        self._output_callback: Optional[Callable[[str], Any]] = None
        self._status_callback: Optional[Callable[[SandboxStatus], Any]] = None

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
        environment: Optional[dict] = None,
    ) -> bool:
        """Start the sandbox container."""
        if self.status not in (SandboxStatus.CREATED, SandboxStatus.STOPPED, SandboxStatus.ERROR):
            logger.warning(f"Cannot start sandbox in status: {self.status}")
            return False

        self._set_status(SandboxStatus.STARTING)

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

            # Create and start container
            self.container_id = await self.manager.create_container(
                config=config,
                task_id=self.task_id,
                agent_type=self.agent_type,
            )

            if not self.container_id:
                self._set_status(SandboxStatus.ERROR)
                return False

            if not await self.manager.start_container(self.container_id):
                self._set_status(SandboxStatus.ERROR)
                return False

            # Initialize enhanced file operations and metrics collector
            if self.manager.client:
                self.file_ops = SandboxFileOps(self.manager.client, self.container_id)
                self.metrics_collector = ContainerMetricsCollector(self.manager.client)

            # Get VNC port if enabled
            if enable_vnc:
                await asyncio.sleep(2)  # Wait for VNC to be ready
                self.vnc_port = await self.manager.get_vnc_port(self.container_id)
                logger.info(f"VNC available on port: {self.vnc_port}")

                # Start live streaming if enabled
                if self.enable_live_streaming and self.vnc_port:
                    self.live_join_code = await self.manager.start_live_stream(
                        container_id=self.container_id,
                        task_id=self.task_id,
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
            self._set_status(SandboxStatus.STOPPED)
            logger.info(f"Sandbox stopped for task {self.task_id}")
            return True
        except Exception as e:
            logger.error(f"Failed to stop sandbox: {e}")
            self._set_status(SandboxStatus.ERROR)
            return False

    async def cleanup(self) -> bool:
        """Remove the sandbox container."""
        if not self.container_id:
            return True

        try:
            # Stop live streaming first
            if self.live_join_code:
                await self.manager.stop_live_stream(self.container_id)
                self.live_join_code = None

            await self.manager.remove_container(self.container_id, force=True)
            self.container_id = None
            self.vnc_port = None
            self._set_status(SandboxStatus.STOPPED)
            logger.info(f"Sandbox cleaned up for task {self.task_id}")
            return True
        except Exception as e:
            logger.error(f"Failed to cleanup sandbox: {e}")
            return False

    async def execute(
        self,
        command: list[str],
        workdir: str = "/workspace",
        timeout: float = 300,
    ) -> SandboxResult:
        """Execute a command in the sandbox."""
        if self.status != SandboxStatus.RUNNING:
            return SandboxResult(
                success=False,
                exit_code=-1,
                output="",
                error=f"Sandbox not running (status: {self.status})",
            )

        if not self.container_id:
            return SandboxResult(
                success=False,
                exit_code=-1,
                output="",
                error="No container ID",
            )

        import time
        start_time = time.time()

        try:
            exit_code, output = await asyncio.wait_for(
                self.manager.exec_command(
                    self.container_id,
                    command,
                    workdir=workdir,
                ),
                timeout=timeout,
            )

            duration = time.time() - start_time

            # Notify output callback
            if self._output_callback and output:
                try:
                    self._output_callback(output)
                except Exception as e:
                    logger.error(f"Output callback error: {e}")

            return SandboxResult(
                success=exit_code == 0,
                exit_code=exit_code,
                output=output,
                duration_seconds=duration,
            )

        except asyncio.TimeoutError:
            return SandboxResult(
                success=False,
                exit_code=-1,
                output="",
                error=f"Command timed out after {timeout}s",
                duration_seconds=timeout,
            )
        except Exception as e:
            return SandboxResult(
                success=False,
                exit_code=-1,
                output="",
                error=str(e),
                duration_seconds=time.time() - start_time,
            )

    async def execute_streaming(
        self,
        command: list[str],
        on_output=None,
        workdir: str = "/workspace",
        timeout: float = 1800,
    ) -> SandboxResult:
        """Execute a (potentially long-running) command, streaming its output.

        ``on_output`` is called on the event loop with each text chunk as it
        arrives, suitable for forwarding live progress. The full output is also
        buffered and returned in the result.
        """
        if self.status != SandboxStatus.RUNNING or not self.container_id:
            return SandboxResult(
                success=False,
                exit_code=-1,
                output="",
                error=f"Sandbox not running (status: {self.status})",
            )

        import time
        start_time = time.time()
        chunks: list[str] = []
        exit_code = 0
        error: Optional[str] = None

        async def _run() -> None:
            nonlocal exit_code, error
            async for kind, payload in self.manager.exec_command_stream(
                self.container_id, command, workdir=workdir
            ):
                if kind in ("stdout", "stderr"):
                    chunks.append(payload)
                    if on_output:
                        try:
                            on_output(payload)
                        except Exception as cb_err:  # noqa: BLE001
                            logger.error(f"Streaming output callback error: {cb_err}")
                elif kind == "exit":
                    exit_code = payload
                elif kind == "error":
                    error = payload
                    exit_code = -1

        try:
            await asyncio.wait_for(_run(), timeout=timeout)
        except asyncio.TimeoutError:
            return SandboxResult(
                success=False,
                exit_code=-1,
                output="".join(chunks),
                error=f"Command timed out after {timeout}s",
                duration_seconds=timeout,
            )

        return SandboxResult(
            success=exit_code == 0 and error is None,
            exit_code=exit_code,
            output="".join(chunks),
            error=error,
            duration_seconds=time.time() - start_time,
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

    async def read_file(self, path: str) -> Optional[str]:
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

    def get_metrics(self) -> Optional[ContainerMetrics]:
        """Get current container resource metrics."""
        if self.metrics_collector and self.container_id:
            return self.metrics_collector.collect(self.container_id)
        return None

    def get_network_policy(self) -> Optional[NetworkPolicy]:
        """Get the network policy applied to this sandbox."""
        return self._network_policy

    async def get_logs(self, tail: int = 100) -> Optional[str]:
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
