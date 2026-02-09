"""Docker container manager for SquadX agents."""

import asyncio
import logging
from typing import Optional, Any
from dataclasses import dataclass, field

import docker
from docker.models.containers import Container
from docker.errors import DockerException, NotFound, APIError

from squadx_client.config import settings

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
    resolution: str = "1280x720"
    # Security hardening
    enable_hardening: bool = True
    security_level: str = "standard"  # development, standard, maximum


class DockerManager:
    """Manages Docker containers for SquadX agents."""

    def __init__(self):
        self.client: Optional[docker.DockerClient] = None
        self.containers: dict[str, Container] = {}
        self._lock = asyncio.Lock()

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

                # Prepare volumes
                volumes = {
                    **config.volumes,
                }

                # Add workspace volume if specified
                if settings.workspace_path:
                    volumes[settings.workspace_path] = {
                        "bind": config.workspace_path,
                        "mode": "rw",
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
                result.append({
                    "id": container.id,
                    "name": container.name,
                    "status": container.status,
                    "created": container.attrs.get("Created"),
                })

            return result
        except APIError as e:
            logger.error(f"Failed to list containers: {e}")
            return []


# Global instance
docker_manager = DockerManager()
