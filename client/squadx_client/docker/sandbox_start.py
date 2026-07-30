"""AgentSandbox.start orchestration: warm pool vs cold start + VNC/live.

Mutates the sandbox instance in place (container_id, sidecar_id, file_ops, …).
"""

from __future__ import annotations

import asyncio
import logging
from typing import TYPE_CHECKING, Any

from squadx_client.docker.file_ops import SandboxFileOps
from squadx_client.docker.hardening import get_runtime_config
from squadx_client.docker.manager import ContainerConfig
from squadx_client.docker.metrics import ContainerMetricsCollector
from squadx_client.docker.sandbox_types import SandboxStatus

if TYPE_CHECKING:
    from squadx_client.docker.sandbox import AgentSandbox

logger = logging.getLogger(__name__)


async def start_agent_sandbox(
    sandbox: AgentSandbox,
    *,
    image: str = "squadx/agent:latest",
    memory_limit: str = "2g",
    cpu_limit: float = 2.0,
    enable_vnc: bool = True,
    environment: dict | None = None,
    exec_env: dict | None = None,
    settings: Any | None = None,
) -> bool:
    """Start container (+ optional egress sidecar / warm pool / live stream).

    ``settings`` should be the settings object bound in ``docker.sandbox`` so tests
    that patch ``squadx_client.docker.sandbox.settings`` keep working.
    """
    if settings is None:
        from squadx_client.config import settings as settings  # noqa: PLW0621

    if sandbox.status not in (
        SandboxStatus.CREATED,
        SandboxStatus.STOPPED,
        SandboxStatus.ERROR,
    ):
        logger.warning(f"Cannot start sandbox in status: {sandbox.status}")
        return False

    sandbox._set_status(SandboxStatus.STARTING)
    sandbox._exec_env = dict(exec_env or {})

    try:
        if not sandbox.manager.client:
            if not await sandbox.manager.connect():
                sandbox._set_status(SandboxStatus.ERROR)
                return False

        runtime_kwargs = get_runtime_config(sandbox.runtime)
        logger.info(
            f"Starting sandbox for task {sandbox.task_id} with "
            f"runtime={sandbox.runtime.value}"
        )

        config = ContainerConfig(
            image=image,
            memory_limit=memory_limit,
            cpu_limit=cpu_limit,
            enable_vnc=enable_vnc,
            environment=environment or {},
            volumes={
                sandbox.workspace_path: {
                    "bind": "/workspace",
                    "mode": "rw",
                }
            },
            **runtime_kwargs,
        )

        sidecar_enabled = getattr(settings, "egress_sidecar_enabled", False)
        if not await _acquire_or_create(
            sandbox, config, enable_vnc, sidecar_enabled, settings
        ):
            return False

        if not sidecar_enabled:
            logger.error(
                f"egress_unenforced task={sandbox.task_id} "
                f"policy={sandbox._network_policy.default_action.value} — the egress "
                f"sidecar is disabled, so the agent has UNRESTRICTED network access "
                f"(only host-side cloud-metadata rules apply, where the host supports "
                f"them). Set SQUADX_EGRESS_SIDECAR=true. See RFC-0006 / ADR-0008."
            )

        assert sandbox.container_id is not None
        if sandbox.manager.client:
            sandbox.file_ops = SandboxFileOps(
                sandbox.manager.client, sandbox.container_id
            )
            sandbox.metrics_collector = ContainerMetricsCollector(sandbox.manager.client)

        if enable_vnc:
            await _setup_vnc_and_live(sandbox)

        sandbox._set_status(SandboxStatus.RUNNING)
        logger.info(f"Sandbox started for task {sandbox.task_id}")
        return True

    except Exception as e:
        logger.error(f"Failed to start sandbox: {e}")
        sandbox._set_status(SandboxStatus.ERROR)
        return False


async def _acquire_or_create(
    sandbox: AgentSandbox,
    config: ContainerConfig,
    enable_vnc: bool,
    sidecar_enabled: bool,
    settings: Any,
) -> bool:
    """Warm pool acquire or cold create+start. Returns False on hard failure."""
    warm_pool = getattr(sandbox.manager, "warm_pool", None)
    used_pool = False

    if warm_pool is not None and warm_pool.is_enabled:
        try:
            pooled = await warm_pool.acquire(
                task_id=sandbox.task_id,
                agent_type=sandbox.agent_type,
                before_start=sandbox._apply_pooled_policy if sidecar_enabled else None,
            )
            sandbox.container_id = pooled.container_id
            sandbox.sidecar_id = pooled.sidecar_id
            sandbox._pooled_container = pooled
            used_pool = True
            logger.info(
                f"sandbox_acquired_from_pool task={sandbox.task_id} "
                f"container_id={pooled.container_id[:12]} "
                f"use_count={pooled.use_count}"
            )
        except Exception as e:  # noqa: BLE001 - cold fallback below
            if sidecar_enabled and not getattr(settings, "egress_fail_open", False):
                logger.error(
                    f"warm_pool_acquire_failed_fail_closed task={sandbox.task_id} "
                    f"error={e}"
                )
                sandbox._set_status(SandboxStatus.ERROR)
                return False
            logger.warning(
                f"warm_pool_acquire_failed_falling_back task={sandbox.task_id} "
                f"error={e}"
            )

    if used_pool:
        return True

    return await _cold_start(sandbox, config, enable_vnc, sidecar_enabled)


async def _cold_start(
    sandbox: AgentSandbox,
    config: ContainerConfig,
    enable_vnc: bool,
    sidecar_enabled: bool,
) -> bool:
    """Create egress sidecar (optional) + agent container from scratch."""
    if sidecar_enabled:
        published = {f"{config.vnc_port}/tcp": None} if enable_vnc else {}
        sandbox.sidecar_id = await sandbox.manager.create_egress_sidecar(
            task_id=sandbox.task_id,
            agent_type=sandbox.agent_type,
            published_ports=published,
        )
        if not sandbox.sidecar_id:
            logger.error(
                f"egress_sidecar_failed_fail_closed task={sandbox.task_id}"
            )
            sandbox._set_status(SandboxStatus.ERROR)
            return False

        if not await sandbox._apply_sidecar_policy():
            await sandbox._teardown_sidecar()
            sandbox._set_status(SandboxStatus.ERROR)
            return False

    sandbox.container_id = await sandbox.manager.create_container(
        config=config,
        task_id=sandbox.task_id,
        agent_type=sandbox.agent_type,
        netns_container=sandbox.sidecar_id,
    )

    if not sandbox.container_id:
        sandbox._set_status(SandboxStatus.ERROR)
        return False

    if not await sandbox.manager.start_container(sandbox.container_id):
        sandbox._set_status(SandboxStatus.ERROR)
        return False

    return True


async def _setup_vnc_and_live(sandbox: AgentSandbox) -> None:
    await asyncio.sleep(2)
    vnc_host_container = sandbox.sidecar_id or sandbox.container_id
    assert vnc_host_container is not None
    sandbox.vnc_port = await sandbox.manager.get_vnc_port(vnc_host_container)
    logger.info(f"VNC available on port: {sandbox.vnc_port}")

    if sandbox.enable_live_streaming and sandbox.vnc_port:
        assert sandbox.container_id is not None
        sandbox.live_join_code = await sandbox.manager.start_live_stream(
            container_id=sandbox.container_id,
            task_id=sandbox.task_id,
            vnc_port=sandbox.vnc_port,
        )
        if sandbox.live_join_code:
            logger.info(f"Live streaming available: {sandbox.live_join_code}")
