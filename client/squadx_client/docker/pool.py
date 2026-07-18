"""
Warm container pool — pre-created Docker containers held in `created` state
so task startup is a `docker start` (sub-second) instead of `docker create +
start` (10-20s on cold images).

The pool is opt-in via SQUADX_SANDBOX_POOL_ENABLED. It is daemon-scoped:
one pool per daemon, anchored to a single Docker host.

Containers are bound to a shared host workspace directory; per-subtask
isolation is delegated to the worktree-per-subtask layer wired in
orchestrator/nodes.py (Item 2). Don't enable the pool without
worktrees if you run concurrent tasks — they will step on each other.

A background refill task keeps `available` topped up to `target_size`,
replacing containers that fail the post-release health check.
"""

import asyncio
import logging
import time
from dataclasses import dataclass
from typing import TYPE_CHECKING

from squadx_client.config import settings
from squadx_client.docker.manager import ContainerConfig

if TYPE_CHECKING:
    from squadx_client.docker.manager import DockerManager

logger = logging.getLogger(__name__)


@dataclass
class PooledContainer:
    """A container the pool owns. Identity is the container_id (set by Docker)."""

    container_id: str
    container_name: str
    created_at: float
    in_use_since: float | None = None
    use_count: int = 0


@dataclass
class PoolStats:
    """Snapshot of pool state for logging / health checks."""

    target_size: int
    available: int
    in_use: int
    total_created: int
    enabled: bool

    def to_dict(self) -> dict:
        return {
            "targetSize": self.target_size,
            "available": self.available,
            "inUse": self.in_use,
            "totalCreated": self.total_created,
            "enabled": self.enabled,
        }


class WarmContainerPool:
    """Holds N pre-created containers and serves them on demand.

    Lifecycle:
        pool = WarmContainerPool(manager, image=..., target_size=4, ...)
        await pool.initialize()           # creates min_ready + starts refill loop
        pooled = await pool.acquire(...)  # returns a started container
        await pool.release(pooled)        # stops, health-checks, recycles
        await pool.shutdown()             # tears everything down
    """

    def __init__(
        self,
        manager: "DockerManager",
        *,
        image: str,
        target_size: int,
        min_ready: int,
        workspace_mount: str,
        memory_limit: str = "2g",
        cpu_limit: float = 2.0,
        enable_vnc: bool = True,
        vnc_port: int = 5900,
        refill_interval_seconds: float = 5.0,
    ):
        if target_size < 1:
            raise ValueError("target_size must be >= 1")
        if min_ready < 0 or min_ready > target_size:
            raise ValueError("min_ready must be in [0, target_size]")

        self._manager = manager
        self._image = image
        self._target_size = target_size
        self._min_ready = min_ready
        self._workspace_mount = workspace_mount
        self._memory_limit = memory_limit
        self._cpu_limit = cpu_limit
        self._enable_vnc = enable_vnc
        self._vnc_port = vnc_port
        self._refill_interval = refill_interval_seconds

        self._available: asyncio.Queue[PooledContainer] = asyncio.Queue()
        self._in_use: dict[str, PooledContainer] = {}
        self._counter: int = 0
        self._total_created: int = 0
        self._lock = asyncio.Lock()
        self._refill_task: asyncio.Task | None = None
        self._running: bool = False

    @property
    def is_enabled(self) -> bool:
        """Pool is enabled only if initialize() succeeded and shutdown() hasn't been called."""
        return self._running

    @property
    def stats(self) -> PoolStats:
        return PoolStats(
            target_size=self._target_size,
            available=self._available.qsize(),
            in_use=len(self._in_use),
            total_created=self._total_created,
            enabled=self.is_enabled,
        )

    async def initialize(self) -> None:
        """Pre-create min_ready containers and start the background refill loop.

        On failure, leaves the pool disabled — callers should fall back to the
        cold-start path. Never raises: a broken pool must not break the daemon.
        """
        try:
            self._running = True
            for _ in range(self._min_ready):
                pooled = await self._create_one()
                if pooled is not None:
                    await self._available.put(pooled)
            self._refill_task = asyncio.create_task(self._refill_loop())
            logger.info(
                f"warm_pool_initialized target={self._target_size} "
                f"min_ready={self._min_ready} pre_created={self._available.qsize()}"
            )
        except Exception as e:  # noqa: BLE001 - pool must never crash the daemon
            logger.error(f"warm_pool_init_failed error={e}")
            await self.shutdown()

    async def shutdown(self) -> None:
        """Cancel refill loop and remove all containers (in_use + available)."""
        self._running = False
        if self._refill_task is not None:
            self._refill_task.cancel()
            try:
                await self._refill_task
            except (asyncio.CancelledError, Exception):  # noqa: BLE001
                pass
            self._refill_task = None

        # Stop in_use containers (best effort)
        for pooled in list(self._in_use.values()):
            await self._safe_remove(pooled)
        self._in_use.clear()

        # Drain available queue
        while not self._available.empty():
            pooled = self._available.get_nowait()
            await self._safe_remove(pooled)

        logger.info("warm_pool_shutdown")

    async def acquire(
        self,
        *,
        task_id: int,
        agent_type: str,
    ) -> PooledContainer:
        """Return a started container. Cold-creates one if the pool is empty.

        Raises RuntimeError if the cold-create fallback also fails. Callers
        should catch and fall back to the unmanaged create_container path.
        """
        pooled: PooledContainer | None = None
        try:
            pooled = self._available.get_nowait()
        except asyncio.QueueEmpty:
            logger.warning(
                f"warm_pool_empty_cold_fallback task={task_id} agent={agent_type}"
            )
            pooled = await self._create_one()
            if pooled is None:
                raise RuntimeError(
                    f"Warm pool empty and cold-create failed for task {task_id}"
                ) from None

        # Start the container; on failure, discard and cold-create a fresh one
        assert self._manager.client is not None  # pool only runs once the manager connected
        try:
            container = self._manager.client.containers.get(pooled.container_id)
            container.start()
        except Exception as e:  # noqa: BLE001 - discard and try once more
            logger.warning(
                f"warm_pool_start_failed discarding container_id={pooled.container_id} "
                f"error={e}"
            )
            await self._safe_remove(pooled)
            pooled = await self._create_one()
            if pooled is None:
                raise RuntimeError(
                    f"Warm pool start failed and replacement cold-create also failed: {e}"
                ) from e
            container = self._manager.client.containers.get(pooled.container_id)
            container.start()

        pooled.in_use_since = time.time()
        pooled.use_count += 1
        async with self._lock:
            self._in_use[pooled.container_id] = pooled
        return pooled

    async def release(self, pooled: PooledContainer) -> None:
        """Stop the container, health-check, and either recycle or discard.

        Unhealthy containers (status != 'created' after stop) are removed and
        left to the refill loop to replace, so the pool self-heals.
        """
        async with self._lock:
            self._in_use.pop(pooled.container_id, None)

        if self._manager.client is None:
            return

        try:
            container = self._manager.client.containers.get(pooled.container_id)
            try:
                container.stop(timeout=5)
            except Exception as e:  # noqa: BLE001 - already stopped is fine
                logger.debug(
                    f"warm_pool_stop_ignored container_id={pooled.container_id} error={e}"
                )
            container.reload()
            if container.status == "created":
                pooled.in_use_since = None
                await self._available.put(pooled)
                logger.debug(
                    f"warm_pool_recycled container_id={pooled.container_id}"
                )
            else:
                logger.warning(
                    f"warm_pool_unhealthy_after_release container_id="
                    f"{pooled.container_id} status={container.status}"
                )
                await self._safe_remove(pooled)
        except Exception as e:  # noqa: BLE001 - any docker error → drop the container
            logger.warning(
                f"warm_pool_release_error container_id={pooled.container_id} error={e}"
            )
            await self._safe_remove(pooled)

    async def _refill_loop(self) -> None:
        """Background task: keep `available` at `target_size`."""
        while self._running:
            try:
                await asyncio.sleep(self._refill_interval)
            except asyncio.CancelledError:
                break
            while self._running and self._available.qsize() < self._target_size:
                pooled = await self._create_one()
                if pooled is None:
                    break
                await self._available.put(pooled)
                logger.debug(
                    f"warm_pool_refilled available={self._available.qsize()}/"
                    f"{self._target_size}"
                )

    async def _create_one(self) -> PooledContainer | None:
        """Create a single container in Docker's `created` state and return its handle."""
        if self._manager.client is None:
            return None
        async with self._lock:
            counter = self._counter
            self._counter += 1
            self._total_created += 1

        name = f"squadx-pool-{counter}"
        config = ContainerConfig(
            image=self._image,
            name=name,
            memory_limit=self._memory_limit,
            cpu_limit=self._cpu_limit,
            enable_vnc=self._enable_vnc,
            vnc_port=self._vnc_port,
            volumes={
                self._workspace_mount: {"bind": "/workspace", "mode": "rw"},
            },
        )
        container_id = await self._manager.create_container(
            config=config,
            task_id=0,  # not tied to a specific task
            agent_type="pool",
        )
        if not container_id:
            logger.error(f"warm_pool_create_failed name={name}")
            return None
        return PooledContainer(
            container_id=container_id,
            container_name=name,
            created_at=time.time(),
        )

    async def _safe_remove(self, pooled: PooledContainer) -> None:
        """Best-effort container removal; never raises."""
        if self._manager.client is None:
            return
        try:
            container = self._manager.client.containers.get(pooled.container_id)
            try:
                container.stop(timeout=2)
            except Exception:  # noqa: BLE001
                pass
            container.remove(force=True)
        except Exception as e:  # noqa: BLE001
            logger.debug(
                f"warm_pool_safe_remove_failed container_id={pooled.container_id} error={e}"
            )


# Module-level singleton placeholder. The daemon assigns the real instance
# at startup; tests construct their own.
warm_pool: WarmContainerPool | None = None


def build_pool_from_settings(manager: "DockerManager") -> WarmContainerPool:
    """Construct a WarmContainerPool from current settings (for daemon init)."""
    return WarmContainerPool(
        manager=manager,
        image=settings.agent_image,
        target_size=settings.sandbox_pool_size,
        min_ready=settings.sandbox_pool_min_ready,
        workspace_mount=settings.sandbox_pool_workspace_root,
        memory_limit=settings.agent_memory_limit,
        cpu_limit=settings.agent_cpu_limit,
        enable_vnc=settings.enable_vnc,
        refill_interval_seconds=settings.sandbox_pool_refill_interval_seconds,
    )
