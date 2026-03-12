"""Docker container security hardening configuration.

This module implements Phase 1 security hardening as defined in
DECISAO-ARQUITETURAL-SANDBOXING.md.

Security Layers:
1. Capability dropping (CAP_DROP=ALL)
2. Read-only root filesystem
3. No new privileges (no-new-privileges)
4. Seccomp profile (restricted syscalls)
5. Non-root user execution
6. Resource limits (memory, CPU, PIDs)
7. Network isolation (--network=none by default)
8. tmpfs for /tmp with noexec

Upgrade Path:
- Phase 2: gVisor (runsc) - When >100 exec/day or enterprise client
- Phase 3: Firecracker - When multi-tenant or >1000 exec/day
"""

import logging
import os
import shutil
from dataclasses import dataclass, field
from enum import Enum
from pathlib import Path
from typing import Any

from squadx_client.config import settings

logger = logging.getLogger(__name__)


class SecurityLevel(str, Enum):
    """Security level for container hardening."""

    # Minimal security - for development/debugging only
    DEVELOPMENT = "development"

    # Standard hardening - Phase 1 Docker Hardened
    STANDARD = "standard"

    # Maximum security - all restrictions enabled
    MAXIMUM = "maximum"


class SandboxRuntime(str, Enum):
    """Container runtime for sandbox execution.

    - DOCKER: Standard Docker runtime (runc) - Phase 1
    - GVISOR: gVisor (runsc) kernel-level isolation - Phase 2
    - FIRECRACKER: Firecracker microVM isolation - Phase 3
    """

    DOCKER = "docker"
    GVISOR = "gvisor"
    FIRECRACKER = "firecracker"


def get_runtime_config(runtime: SandboxRuntime) -> dict[str, Any]:
    """Get container configuration overrides for the specified runtime.

    Args:
        runtime: The sandbox runtime to configure for.

    Returns:
        Dictionary of kwargs to merge into docker.containers.run() call.
    """
    if runtime == SandboxRuntime.GVISOR:
        logger.info("Configuring gVisor (runsc) runtime")
        return {
            "runtime": "runsc",
        }
    elif runtime == SandboxRuntime.FIRECRACKER:
        logger.info("Configuring Firecracker (firecracker-containerd) runtime")
        return {
            "runtime": "firecracker",
        }
    else:
        # Standard Docker (runc) - no extra config needed
        return {}


@dataclass
class SecurityConfig:
    """Security configuration for hardened containers.

    This configuration is applied to all agent containers to ensure
    secure code execution in an isolated environment.
    """

    # Security level
    level: SecurityLevel = SecurityLevel.STANDARD

    # User configuration
    user: str = "1000:1000"  # Non-root user

    # Filesystem security
    read_only: bool = True  # Read-only root filesystem
    tmpfs_mounts: dict[str, str] = field(default_factory=lambda: {
        "/tmp": "size=100M,noexec,nosuid,nodev",
        "/run": "size=10M,noexec,nosuid,nodev",
    })

    # Capability restrictions
    cap_drop: list[str] = field(default_factory=lambda: ["ALL"])
    cap_add: list[str] = field(default_factory=list)  # Empty by default

    # Security options
    no_new_privileges: bool = True
    seccomp_profile: str | None = None  # Path to seccomp profile
    apparmor_profile: str | None = None  # AppArmor profile name

    # Network isolation
    network_disabled: bool = True  # --network=none
    network_mode: str = "none"

    # Resource limits
    memory_limit: str = "2g"
    memory_swap: str = "2g"  # Same as memory to disable swap
    cpu_limit: float = 2.0
    pids_limit: int = 256

    # Process restrictions
    privileged: bool = False
    pid_mode: str | None = None  # No host PID namespace

    def to_docker_kwargs(self) -> dict[str, Any]:
        """Convert to Docker SDK container.run() kwargs.

        Returns:
            Dictionary of kwargs for docker.containers.run()
        """
        kwargs: dict[str, Any] = {
            "user": self.user,
            "read_only": self.read_only,
            "cap_drop": self.cap_drop,
            "privileged": self.privileged,
            "mem_limit": self.memory_limit,
            "memswap_limit": self.memory_swap,
            "cpu_period": 100000,
            "cpu_quota": int(self.cpu_limit * 100000),
            "pids_limit": self.pids_limit,
        }

        # Security options
        security_opts = []
        if self.no_new_privileges:
            security_opts.append("no-new-privileges:true")
        # Note: seccomp via Docker SDK is complex - use CLI for full hardening
        # The seccomp_profile path is used in get_run_command() for CLI mode
        if self.apparmor_profile:
            security_opts.append(f"apparmor={self.apparmor_profile}")

        if security_opts:
            kwargs["security_opt"] = security_opts

        # Network
        if self.network_disabled:
            kwargs["network_mode"] = self.network_mode

        # Capabilities
        if self.cap_add:
            kwargs["cap_add"] = self.cap_add

        # tmpfs mounts
        if self.tmpfs_mounts:
            kwargs["tmpfs"] = self.tmpfs_mounts

        # PID mode
        if self.pid_mode:
            kwargs["pid_mode"] = self.pid_mode

        return kwargs


class HardeningManager:
    """Manages security hardening for agent containers."""

    # Default paths for security profiles
    DEFAULT_SECCOMP_PATH = Path(__file__).parent.parent.parent / "docker" / "seccomp" / "agent.json"
    DEFAULT_APPARMOR_PROFILE = "squadx-agent"

    def __init__(self):
        self._seccomp_path: Path | None = None
        self._apparmor_profile: str | None = None

    @property
    def seccomp_profile_path(self) -> str | None:
        """Get the seccomp profile path."""
        # Check settings first
        if settings.seccomp_profile:
            return settings.seccomp_profile

        # Check default path
        if self._seccomp_path and self._seccomp_path.exists():
            return str(self._seccomp_path)

        if self.DEFAULT_SECCOMP_PATH.exists():
            self._seccomp_path = self.DEFAULT_SECCOMP_PATH
            return str(self._seccomp_path)

        return None

    @property
    def apparmor_profile(self) -> str | None:
        """Get the AppArmor profile name."""
        # Check settings first
        if settings.apparmor_profile:
            return settings.apparmor_profile

        return self._apparmor_profile

    def create_security_config(
        self,
        level: SecurityLevel = SecurityLevel.STANDARD,
        enable_network: bool | None = None,
        memory_limit: str | None = None,
        cpu_limit: float | None = None,
    ) -> SecurityConfig:
        """Create a security configuration based on level.

        Args:
            level: Security level (development, standard, maximum)
            enable_network: Override network setting (None uses level default)
            memory_limit: Override memory limit
            cpu_limit: Override CPU limit

        Returns:
            SecurityConfig instance
        """
        # Use settings for network if not overridden
        network_enabled = enable_network if enable_network is not None else settings.enable_network

        if level == SecurityLevel.DEVELOPMENT:
            return self._create_development_config(
                network_enabled=network_enabled,
                memory_limit=memory_limit,
                cpu_limit=cpu_limit,
            )
        elif level == SecurityLevel.MAXIMUM:
            return self._create_maximum_config(
                network_enabled=network_enabled,
                memory_limit=memory_limit,
                cpu_limit=cpu_limit,
            )
        else:
            return self._create_standard_config(
                network_enabled=network_enabled,
                memory_limit=memory_limit,
                cpu_limit=cpu_limit,
            )

    def _create_development_config(
        self,
        network_enabled: bool = True,
        memory_limit: str | None = None,
        cpu_limit: float | None = None,
    ) -> SecurityConfig:
        """Create development security config (minimal restrictions)."""
        return SecurityConfig(
            level=SecurityLevel.DEVELOPMENT,
            read_only=False,  # Allow writes for debugging
            no_new_privileges=False,
            network_disabled=not network_enabled,
            network_mode="bridge" if network_enabled else "none",
            memory_limit=memory_limit or settings.agent_memory_limit,
            cpu_limit=cpu_limit or settings.agent_cpu_limit,
            # No seccomp in dev mode for easier debugging
            seccomp_profile=None,
        )

    def _create_standard_config(
        self,
        network_enabled: bool = False,
        memory_limit: str | None = None,
        cpu_limit: float | None = None,
    ) -> SecurityConfig:
        """Create standard security config (Phase 1 hardening)."""
        return SecurityConfig(
            level=SecurityLevel.STANDARD,
            read_only=True,
            no_new_privileges=True,
            network_disabled=not network_enabled,
            network_mode="bridge" if network_enabled else "none",
            memory_limit=memory_limit or settings.agent_memory_limit,
            cpu_limit=cpu_limit or settings.agent_cpu_limit,
            seccomp_profile=self.seccomp_profile_path,
            apparmor_profile=self.apparmor_profile,
        )

    def _create_maximum_config(
        self,
        network_enabled: bool = False,
        memory_limit: str | None = None,
        cpu_limit: float | None = None,
    ) -> SecurityConfig:
        """Create maximum security config (all restrictions)."""
        config = self._create_standard_config(
            network_enabled=network_enabled,
            memory_limit=memory_limit,
            cpu_limit=cpu_limit,
        )
        config.level = SecurityLevel.MAXIMUM

        # Additional restrictions
        config.pids_limit = 128  # Stricter PID limit
        config.tmpfs_mounts = {
            "/tmp": "size=50M,noexec,nosuid,nodev",
            "/run": "size=5M,noexec,nosuid,nodev",
        }

        return config

    def get_run_command(
        self,
        image: str,
        workspace_path: str,
        config: SecurityConfig | None = None,
    ) -> list[str]:
        """Generate docker run command for debugging.

        This is useful for debugging or manually running containers
        with the same security settings.

        Args:
            image: Docker image name
            workspace_path: Host path to mount as /workspace
            config: Security configuration (uses standard if None)

        Returns:
            List of command arguments for docker run
        """
        if config is None:
            config = self.create_security_config()

        cmd = ["docker", "run", "--rm"]

        # User
        cmd.extend(["--user", config.user])

        # Read-only
        if config.read_only:
            cmd.append("--read-only")

        # Capabilities
        for cap in config.cap_drop:
            cmd.extend(["--cap-drop", cap])
        for cap in config.cap_add:
            cmd.extend(["--cap-add", cap])

        # Security options
        if config.no_new_privileges:
            cmd.extend(["--security-opt", "no-new-privileges:true"])
        if config.seccomp_profile:
            cmd.extend(["--security-opt", f"seccomp={config.seccomp_profile}"])
        if config.apparmor_profile:
            cmd.extend(["--security-opt", f"apparmor={config.apparmor_profile}"])

        # Resource limits
        cmd.extend(["--memory", config.memory_limit])
        cmd.extend(["--cpus", str(config.cpu_limit)])
        cmd.extend(["--pids-limit", str(config.pids_limit)])

        # Network
        if config.network_disabled:
            cmd.extend(["--network", "none"])

        # tmpfs
        for path, options in config.tmpfs_mounts.items():
            cmd.extend(["--tmpfs", f"{path}:{options}"])

        # Workspace volume
        cmd.extend(["-v", f"{workspace_path}:/workspace:rw"])

        # Image
        cmd.append(image)

        return cmd


# Global instance
hardening_manager = HardeningManager()


def get_hardened_config(
    level: SecurityLevel = SecurityLevel.STANDARD,
    **overrides: Any,
) -> SecurityConfig:
    """Get a hardened security configuration.

    Convenience function to get a security configuration with
    optional overrides.

    Args:
        level: Security level
        **overrides: Override specific config values

    Returns:
        SecurityConfig instance
    """
    config = hardening_manager.create_security_config(level=level)

    # Apply overrides
    for key, value in overrides.items():
        if hasattr(config, key):
            setattr(config, key, value)

    return config


def detect_available_runtimes() -> list[SandboxRuntime]:
    """Detect which sandbox runtimes are available on this system.

    Checks for the presence of runtime binaries:
    - Docker (runc): always available if Docker is running
    - gVisor: checks for 'runsc' binary in PATH
    - Firecracker: checks for 'firecracker-containerd' binary in PATH

    Returns:
        List of available SandboxRuntime values.
    """
    available = [SandboxRuntime.DOCKER]

    if shutil.which("runsc"):
        available.append(SandboxRuntime.GVISOR)
        logger.debug("gVisor (runsc) runtime detected")

    if shutil.which("firecracker-containerd"):
        available.append(SandboxRuntime.FIRECRACKER)
        logger.debug("Firecracker runtime detected")

    return available


def resolve_runtime() -> SandboxRuntime:
    """Resolve which sandbox runtime to use based on settings.

    Reads the configured runtime from settings and validates
    that it is available on the system. Falls back to DOCKER
    if the requested runtime is not available.

    Returns:
        The SandboxRuntime to use.
    """
    configured = settings.sandbox_runtime.lower()

    try:
        requested = SandboxRuntime(configured)
    except ValueError:
        logger.warning(
            f"Unknown sandbox runtime '{configured}', falling back to docker"
        )
        return SandboxRuntime.DOCKER

    available = detect_available_runtimes()

    if requested in available:
        logger.info(f"Using sandbox runtime: {requested.value}")
        return requested

    logger.warning(
        f"Requested runtime '{requested.value}' is not available, "
        f"falling back to docker. Available: {[r.value for r in available]}"
    )
    return SandboxRuntime.DOCKER
