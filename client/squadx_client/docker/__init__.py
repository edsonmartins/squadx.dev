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
from squadx_client.docker.lifecycle import (
    SandboxLifecycleManager,
    SandboxState,
    SandboxInfo,
)
from squadx_client.docker.agent_lifecycle import (
    AgentLifecycleProtocol,
    AgentState,
    AgentStatus,
)
from squadx_client.docker.file_ops import SandboxFileOps, FileInfo
from squadx_client.docker.metrics import ContainerMetricsCollector, ContainerMetrics
from squadx_client.docker.network_policy import (
    NetworkPolicy,
    EgressRule,
    EgressAction,
    EgressSidecarConfig,
    get_predefined_policy,
    generate_network_setup_script,
)
from squadx_client.docker.pool import (
    PooledContainer,
    PoolStats,
    WarmContainerPool,
    build_pool_from_settings,
    warm_pool,
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
    # Lifecycle
    "SandboxLifecycleManager",
    "SandboxState",
    "SandboxInfo",
    # Agent Lifecycle
    "AgentLifecycleProtocol",
    "AgentState",
    "AgentStatus",
    # File Operations
    "SandboxFileOps",
    "FileInfo",
    # Metrics
    "ContainerMetricsCollector",
    "ContainerMetrics",
    # Network Policy
    "NetworkPolicy",
    "EgressRule",
    "EgressAction",
    "EgressSidecarConfig",
    "get_predefined_policy",
    "generate_network_setup_script",
    # Warm Container Pool
    "WarmContainerPool",
    "PooledContainer",
    "PoolStats",
    "build_pool_from_settings",
    "warm_pool",
]
