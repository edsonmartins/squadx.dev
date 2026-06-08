"""Docker container manager for SquadX agents."""

import asyncio
import logging
from typing import Optional, Any, TYPE_CHECKING
from dataclasses import dataclass, field

import docker
from docker.models.containers import Container
from docker.errors import DockerException, NotFound, APIError

from squadx_client.config import settings

if TYPE_CHECKING:
    from squadx_client.streaming.webrtc_bridge import WebRTCBridge

logger = logging.getLogger(__name__)


@dataclass
class ContainerConfig:
    """Configuration for an agent container."""

    image: str = "squadx/agent:latest"
    name: Optional[str] = None
    memory_limit: str = "2g"
    cpu_limit: float = 2.0
    workspace_path: str = "/workspace"
    environment: dict = field(default_factory=dict)
    ports: dict = field(default_factory=dict)
    volumes: dict = field(default_factory=dict)
    network: Optional[str] = None
    enable_vnc: bool = True
    vnc_port: int = 5900
    resolution: str = "1280x720x24"
    # Security hardening
    enable_hardening: bool = True
    security_level: str = "standard"  # development, standard, maximum


@dataclass
class LiveStreamInfo:
    """Information about an active live stream for a container."""

    container_id: str
    task_id: int
    session_id: str
    join_code: str
    vnc_port: int
    bridge: Optional["WebRTCBridge"] = None


class DockerManager:
    """Manages Docker containers for SquadX agents."""

    def __init__(self):
        self.client: Optional[docker.DockerClient] = None
        self.containers: dict[str, Container] = {}
        self._lock = asyncio.Lock()
        # Live streaming tracking
        self._live_streams: dict[str, LiveStreamInfo] = {}  # container_id -> LiveStreamInfo
        self._live_enabled = bool(settings.supabase_url and settings.supabase_anon_key)

    async def connect(self) -> bool:
        """Connect to Docker daemon."""
        try:
            self.client = docker.from_env()
            # Test connection
            self.client.ping()
            logger.info("Connected to Docker daemon")
            return True
        except DockerException as e:
            logger.error(f"Failed to connect to Docker: {e}")
            return False

    async def disconnect(self):
        """Disconnect from Docker daemon."""
        if self.client:
            self.client.close()
            self.client = None
            logger.info("Disconnected from Docker daemon")

    async def pull_image(self, image: str) -> bool:
        """Pull a Docker image."""
        if not self.client:
            logger.error("Docker client not connected")
            return False

        try:
            logger.info(f"Pulling image: {image}")
            self.client.images.pull(image)
            logger.info(f"Successfully pulled image: {image}")
            return True
        except APIError as e:
            logger.error(f"Failed to pull image {image}: {e}")
            return False

    async def create_container(
        self,
        config: ContainerConfig,
        task_id: int,
        agent_type: str,
    ) -> Optional[str]:
        """Create a new container for an agent."""
        if not self.client:
            logger.error("Docker client not connected")
            return None

        async with self._lock:
            container_name = config.name or f"squadx-agent-{task_id}-{agent_type}"

            try:
                # Check if container already exists
                try:
                    existing = self.client.containers.get(container_name)
                    logger.warning(f"Container {container_name} already exists, removing...")
                    existing.remove(force=True)
                except NotFound:
                    pass

                # Prepare environment variables
                env = {
                    "SQUADX_TASK_ID": str(task_id),
                    "SQUADX_AGENT_TYPE": agent_type,
                    "SQUADX_API_URL": settings.api_url,
                    "DISPLAY": ":99",
                    "RESOLUTION": config.resolution,
                    **config.environment,
                }

                # Prepare volumes (use config.volumes, don't add workspace again)
                volumes = {
                    **config.volumes,
                }

                # Prepare ports
                ports = {**config.ports}
                if config.enable_vnc:
                    ports[f"{config.vnc_port}/tcp"] = None  # Auto-assign host port

                # Build container kwargs
                container_kwargs: dict[str, Any] = {
                    "image": config.image,
                    "name": container_name,
                    "environment": env,
                    "volumes": volumes,
                    "ports": ports,
                    "detach": True,
                    "tty": True,
                    "stdin_open": True,
                }

                # Apply security hardening if enabled
                if config.enable_hardening:
                    from squadx_client.docker.hardening import (
                        hardening_manager,
                        SecurityLevel,
                    )

                    level = SecurityLevel(config.security_level)
                    security_config = hardening_manager.create_security_config(
                        level=level,
                        memory_limit=config.memory_limit,
                        cpu_limit=config.cpu_limit,
                    )

                    # Merge security kwargs
                    container_kwargs.update(security_config.to_docker_kwargs())

                    # Override network if VNC is enabled (needs port binding)
                    if config.enable_vnc and security_config.network_disabled:
                        container_kwargs["network_mode"] = "bridge"

                    logger.info(
                        f"Security hardening enabled: level={level.value}, "
                        f"read_only={security_config.read_only}, "
                        f"network={container_kwargs.get('network_mode', 'default')}"
                    )
                else:
                    # Basic resource limits without hardening
                    container_kwargs.update({
                        "mem_limit": config.memory_limit,
                        "cpu_period": 100000,
                        "cpu_quota": int(config.cpu_limit * 100000),
                        "network": config.network,
                    })

                # Create container
                container = self.client.containers.create(**container_kwargs)

                self.containers[container.id] = container
                logger.info(f"Created container: {container_name} ({container.short_id})")

                return container.id

            except APIError as e:
                logger.error(f"Failed to create container: {e}")
                return None

    async def start_container(self, container_id: str) -> bool:
        """Start a container."""
        if not self.client:
            return False

        try:
            container = self.client.containers.get(container_id)
            container.start()
            logger.info(f"Started container: {container.short_id}")
            return True
        except (NotFound, APIError) as e:
            logger.error(f"Failed to start container {container_id}: {e}")
            return False

    async def stop_container(self, container_id: str, timeout: int = 10) -> bool:
        """Stop a container."""
        if not self.client:
            return False

        try:
            container = self.client.containers.get(container_id)
            container.stop(timeout=timeout)
            logger.info(f"Stopped container: {container.short_id}")
            return True
        except (NotFound, APIError) as e:
            logger.error(f"Failed to stop container {container_id}: {e}")
            return False

    async def remove_container(self, container_id: str, force: bool = False) -> bool:
        """Remove a container."""
        if not self.client:
            return False

        try:
            container = self.client.containers.get(container_id)
            container.remove(force=force)
            self.containers.pop(container_id, None)
            logger.info(f"Removed container: {container.short_id}")
            return True
        except (NotFound, APIError) as e:
            logger.error(f"Failed to remove container {container_id}: {e}")
            return False

    async def get_container_status(self, container_id: str) -> Optional[str]:
        """Get container status."""
        if not self.client:
            return None

        try:
            container = self.client.containers.get(container_id)
            container.reload()
            return container.status
        except NotFound:
            return None
        except APIError as e:
            logger.error(f"Failed to get container status: {e}")
            return None

    async def get_container_logs(
        self,
        container_id: str,
        tail: int = 100,
        follow: bool = False,
    ) -> Optional[str]:
        """Get container logs."""
        if not self.client:
            return None

        try:
            container = self.client.containers.get(container_id)
            logs = container.logs(tail=tail, follow=follow)
            if isinstance(logs, bytes):
                return logs.decode("utf-8")
            return str(logs)
        except (NotFound, APIError) as e:
            logger.error(f"Failed to get container logs: {e}")
            return None

    async def exec_command(
        self,
        container_id: str,
        command: list[str],
        workdir: Optional[str] = None,
    ) -> tuple[int, str]:
        """Execute a command in a container."""
        if not self.client:
            return -1, "Docker client not connected"

        try:
            container = self.client.containers.get(container_id)
            result = container.exec_run(
                command,
                workdir=workdir,
                demux=True,
            )

            stdout = result.output[0].decode("utf-8") if result.output[0] else ""
            stderr = result.output[1].decode("utf-8") if result.output[1] else ""
            output = stdout + stderr

            return result.exit_code, output

        except (NotFound, APIError) as e:
            logger.error(f"Failed to exec command: {e}")
            return -1, str(e)

    async def exec_command_stream(
        self,
        container_id: str,
        command: list[str],
        workdir: Optional[str] = None,
    ):
        """Execute a command, streaming output incrementally.

        Async generator yielding ("stdout"|"stderr", text) tuples as output
        arrives, then a final ("exit", exit_code) tuple. The blocking Docker
        iteration runs in a worker thread; chunks are marshalled back to the
        event loop via a queue so callbacks run safely on the main loop.
        """
        if not self.client:
            yield ("error", "Docker client not connected")
            yield ("exit", -1)
            return

        loop = asyncio.get_running_loop()
        queue: asyncio.Queue = asyncio.Queue()
        sentinel = object()

        def _worker():
            try:
                api = self.client.api
                exec_id = api.exec_create(container_id, command, workdir=workdir)["Id"]
                stream = api.exec_start(exec_id, stream=True, demux=True)
                for stdout_chunk, stderr_chunk in stream:
                    if stdout_chunk:
                        loop.call_soon_threadsafe(
                            queue.put_nowait,
                            ("stdout", stdout_chunk.decode("utf-8", "replace")),
                        )
                    if stderr_chunk:
                        loop.call_soon_threadsafe(
                            queue.put_nowait,
                            ("stderr", stderr_chunk.decode("utf-8", "replace")),
                        )
                exit_code = api.exec_inspect(exec_id).get("ExitCode", 0)
                loop.call_soon_threadsafe(queue.put_nowait, ("exit", exit_code))
            except Exception as e:  # noqa: BLE001 - surface to consumer
                loop.call_soon_threadsafe(queue.put_nowait, ("error", str(e)))
                loop.call_soon_threadsafe(queue.put_nowait, ("exit", -1))
            finally:
                loop.call_soon_threadsafe(queue.put_nowait, sentinel)

        worker = loop.run_in_executor(None, _worker)
        try:
            while True:
                item = await queue.get()
                if item is sentinel:
                    break
                yield item
        finally:
            await worker

    async def get_vnc_port(self, container_id: str) -> Optional[int]:
        """Get the mapped VNC port for a container."""
        if not self.client:
            return None

        try:
            container = self.client.containers.get(container_id)
            container.reload()

            ports = container.attrs.get("NetworkSettings", {}).get("Ports", {})
            vnc_binding = ports.get("5900/tcp")

            if vnc_binding and len(vnc_binding) > 0:
                return int(vnc_binding[0]["HostPort"])

            return None
        except (NotFound, APIError) as e:
            logger.error(f"Failed to get VNC port: {e}")
            return None

    async def cleanup_all(self):
        """Remove all managed containers."""
        for container_id in list(self.containers.keys()):
            await self.remove_container(container_id, force=True)

        logger.info("Cleaned up all managed containers")

    async def list_running_agents(self) -> list[dict]:
        """List all running SquadX agent containers."""
        if not self.client:
            return []

        try:
            containers = self.client.containers.list(
                filters={"name": "squadx-agent-"}
            )

            result = []
            for container in containers:
                live_info = self._live_streams.get(container.id)
                result.append({
                    "id": container.id,
                    "name": container.name,
                    "status": container.status,
                    "created": container.attrs.get("Created"),
                    "live_session": live_info.join_code if live_info else None,
                })

            return result
        except APIError as e:
            logger.error(f"Failed to list containers: {e}")
            return []

    async def start_live_stream(
        self,
        container_id: str,
        task_id: int,
        fps: int = 30,
    ) -> Optional[str]:
        """Start live streaming for a container.

        Args:
            container_id: The container ID
            task_id: The task ID for the session
            fps: Frames per second for the stream

        Returns:
            The join code for the live session, or None if failed
        """
        if not self._live_enabled:
            logger.warning("Live streaming not enabled (Supabase not configured)")
            return None

        # Check if already streaming
        if container_id in self._live_streams:
            return self._live_streams[container_id].join_code

        # Get VNC port
        vnc_port = await self.get_vnc_port(container_id)
        if not vnc_port:
            logger.error(f"Cannot start live stream: VNC port not available for {container_id}")
            return None

        try:
            from squadx_client.streaming.webrtc_bridge import create_live_stream

            # Create live stream (connects to VNC and creates Supabase session)
            bridge, join_code = await create_live_stream(
                task_id=task_id,
                vnc_host="localhost",
                vnc_port=vnc_port,
                fps=fps,
            )

            # Track the live stream
            self._live_streams[container_id] = LiveStreamInfo(
                container_id=container_id,
                task_id=task_id,
                session_id=bridge.session_id,
                join_code=join_code,
                vnc_port=vnc_port,
                bridge=bridge,
            )

            logger.info(
                f"Live streaming started for container {container_id[:12]}, "
                f"join code: {join_code}"
            )

            return join_code

        except Exception as e:
            logger.error(f"Failed to start live stream: {e}")
            return None

    async def stop_live_stream(self, container_id: str) -> bool:
        """Stop live streaming for a container.

        Args:
            container_id: The container ID

        Returns:
            True if stopped successfully
        """
        live_info = self._live_streams.pop(container_id, None)
        if not live_info:
            return False

        try:
            if live_info.bridge:
                await live_info.bridge.stop()

            logger.info(f"Live streaming stopped for container {container_id[:12]}")
            return True

        except Exception as e:
            logger.error(f"Error stopping live stream: {e}")
            return False

    async def get_live_stream_info(self, container_id: str) -> Optional[LiveStreamInfo]:
        """Get live stream information for a container.

        Args:
            container_id: The container ID

        Returns:
            LiveStreamInfo if streaming, None otherwise
        """
        return self._live_streams.get(container_id)

    async def start_container_with_live(
        self,
        container_id: str,
        task_id: int,
        enable_live: bool = True,
    ) -> tuple[bool, Optional[str]]:
        """Start a container and optionally enable live streaming.

        Args:
            container_id: The container ID
            task_id: The task ID
            enable_live: Whether to enable live streaming

        Returns:
            Tuple of (success, join_code or None)
        """
        # Start the container
        if not await self.start_container(container_id):
            return False, None

        join_code = None
        if enable_live and self._live_enabled:
            # Wait for VNC to be ready
            await asyncio.sleep(2)

            # Start live streaming
            join_code = await self.start_live_stream(container_id, task_id)

        return True, join_code

    async def stop_container_with_live(
        self,
        container_id: str,
        timeout: int = 10,
    ) -> bool:
        """Stop a container and its live stream.

        Args:
            container_id: The container ID
            timeout: Stop timeout

        Returns:
            True if stopped successfully
        """
        # Stop live streaming first
        await self.stop_live_stream(container_id)

        # Stop the container
        return await self.stop_container(container_id, timeout)


# Global instance
docker_manager = DockerManager()
