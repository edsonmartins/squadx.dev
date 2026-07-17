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

Egress enforcement (RFC-0006) composes with the pool: when it is on, a pooled
unit is an agent *plus* the sidecar whose netns it was created into. Netns
membership is fixed at create time, which is exactly the slow work the pool
exists to pre-pay, so the pair is created, recycled and removed together. The
run's policy is applied through acquire()'s `before_start` hook while the agent
is still `created` — so a pooled agent, like a cold one, can never execute an
instruction before its egress rules are in place.

Note the pool carries no per-run credentials: secrets ride the exec, not the
container (see AgentSandbox._exec_env). Container-create env is fixed at create
time, so a pre-created container could never carry them.
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


class _PolicyRejected(RuntimeError):
    """The pre-start hook refused this unit (e.g. egress policy could not be applied).

    Distinct from a generic start failure: retrying with a fresh container would hit
    the same rejection, so acquire() must not paper over it with a cold-create.
    """


@dataclass
class PooledContainer:
    """A container the pool owns. Identity is the container_id (set by Docker).

    When egress enforcement is on (RFC-0006) the pool holds a *pair*: the agent plus
    the sidecar whose netns it joined at create time. The pair is inseparable — netns
    membership is fixed at create — so they are pooled, recycled and removed together.
    """

    container_id: str
    container_name: str
    created_at: float
    in_use_since: float | None = None
    use_count: int = 0
    sidecar_id: str | None = None


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
        egress_enabled: bool = False,
    ):
        if target_size < 1:
            raise ValueError("target_size must be >= 1")
        if min_ready < 0 or min_ready > target_size:
            raise ValueError("min_ready must be in [0, target_size]")

        self._manager = manager
        self._image = image
        self._egress_enabled = egress_enabled
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
        before_start=None,
    ) -> PooledContainer:
        """Return a started container. Cold-creates one if the pool is empty.

        ``before_start`` is an optional async hook called with the ``PooledContainer``
        after its sidecar is available and *before* the agent starts. It is how the
        caller applies the run's egress policy while the agent still cannot execute
        anything; returning False aborts the acquire fail-closed. The pool deliberately
        knows nothing about policy — it owns container mechanics, the caller owns rules.

        Raises RuntimeError if the cold-create fallback also fails, or if
        ``before_start`` rejects. Callers should catch and fall back to the unmanaged
        create_container path.
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
        try:
            await self._start_agent(pooled, before_start)
        except _PolicyRejected:
            # Never recycle a unit we could not put a policy on.
            await self._safe_remove(pooled)
            raise
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
            await self._start_agent(pooled, before_start)

        pooled.in_use_since = time.time()
        pooled.use_count += 1
        async with self._lock:
            self._in_use[pooled.container_id] = pooled
        return pooled

    async def _start_agent(self, pooled: PooledContainer, before_start) -> None:
        """Ensure the sidecar is up, run the pre-start hook, then start the agent.

        Order is the RFC-0006 invariant: the agent must never be runnable before its
        egress policy is in place.
        """
        if pooled.sidecar_id:
            # A recycled unit's sidecar was left running, but a restart (or a daemon
            # crash) can leave it stopped — the agent cannot join a dead netns.
            sidecar = self._manager.client.containers.get(pooled.sidecar_id)
            sidecar.reload()
            if sidecar.status != "running":
                sidecar.start()

        if before_start is not None:
            ok = await before_start(pooled)
            if ok is False:
                raise _PolicyRejected(
                    f"egress policy rejected for pooled unit {pooled.container_id}"
                )

        container = self._manager.client.containers.get(pooled.container_id)
        container.start()

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
        """Create a single pooled unit in Docker's `created` state and return its handle.

        With egress enforcement on, the unit is a pair: an egress sidecar (started, so
        it owns a live netns) plus an agent created into that netns. Only the *create*
        is done here — that is the 10-20s the pool exists to hide. The agent is left
        `created` so acquire() can apply the run's policy before it ever executes.
        """
        if self._manager.client is None:
            return None
        async with self._lock:
            counter = self._counter
            self._counter += 1
            self._total_created += 1

        name = f"squadx-pool-{counter}"

        # The sidecar must exist and be running before the agent can be created into
        # its netns, so it is part of the pre-created (slow) work, not the acquire path.
        sidecar_id: str | None = None
        if self._egress_enabled:
            published = {f"{self._vnc_port}/tcp": None} if self._enable_vnc else {}
            sidecar_id = await self._manager.create_egress_sidecar(
                task_id=0,
                agent_type=f"pool-{counter}",
                published_ports=published,
            )
            if not sidecar_id:
                logger.error(f"warm_pool_sidecar_create_failed name={name}")
                return None

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
            netns_container=sidecar_id,
        )
        if not container_id:
            logger.error(f"warm_pool_create_failed name={name}")
            if sidecar_id:
                await self._manager.remove_container(sidecar_id, force=True)
            return None
        return PooledContainer(
            container_id=container_id,
            container_name=name,
            created_at=time.time(),
            sidecar_id=sidecar_id,
        )

    async def _safe_remove(self, pooled: PooledContainer) -> None:
        """Best-effort removal of the whole unit (agent + its sidecar); never raises.

        The sidecar goes last: it owns the netns the agent lives in, and removing it
        first would strand the agent.
        """
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
        if pooled.sidecar_id:
            try:
                await self._manager.remove_container(pooled.sidecar_id, force=True)
            except Exception as e:  # noqa: BLE001
                logger.debug(
                    f"warm_pool_sidecar_remove_failed id={pooled.sidecar_id} error={e}"
                )
            pooled.sidecar_id = None


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
        egress_enabled=settings.egress_sidecar_enabled,
    )
