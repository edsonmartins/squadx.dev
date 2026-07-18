"""Docker container management for agent sandboxing."""

from squadx_client.docker.agent_lifecycle import (
    AgentLifecycleProtocol,
    AgentState,
    AgentStatus,
)
from squadx_client.docker.file_ops import FileInfo, SandboxFileOps
from squadx_client.docker.hardening import (
    HardeningManager,
    SecurityConfig,
    SecurityLevel,
    get_hardened_config,
    hardening_manager,
)
from squadx_client.docker.lifecycle import (
    SandboxInfo,
    SandboxLifecycleManager,
    SandboxState,
)
from squadx_client.docker.manager import ContainerConfig, DockerManager, docker_manager
from squadx_client.docker.metrics import ContainerMetrics, ContainerMetricsCollector
from squadx_client.docker.network_policy import (
    EgressAction,
    EgressRule,
    EgressSidecarConfig,
    NetworkPolicy,
    generate_sidecar_setup_script,
    get_predefined_policy,
)
from squadx_client.docker.pool import (
    PooledContainer,
    PoolStats,
    WarmContainerPool,
    build_pool_from_settings,
    warm_pool,
)
from squadx_client.docker.sandbox import AgentSandbox, SandboxResult, SandboxStatus

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
    "generate_sidecar_setup_script",
    # Warm Container Pool
    "WarmContainerPool",
    "PooledContainer",
    "PoolStats",
    "build_pool_from_settings",
    "warm_pool",
]
