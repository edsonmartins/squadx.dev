"""Docker container management for agent sandboxing."""

from squadx_client.docker.manager import DockerManager, ContainerConfig, docker_manager
from squadx_client.docker.sandbox import AgentSandbox, SandboxStatus, SandboxResult
from squadx_client.docker.hardening import (
    SecurityLevel,
    SecurityConfig,
    HardeningManager,
    hardening_manager,
    get_hardened_config,
)

__all__ = [
    # Manager
    "DockerManager",
    "ContainerConfig",
    "docker_manager",
    # Sandbox
    "AgentSandbox",
    "SandboxStatus",
    "SandboxResult",
    # Hardening
    "SecurityLevel",
    "SecurityConfig",
    "HardeningManager",
    "hardening_manager",
    "get_hardened_config",
]
