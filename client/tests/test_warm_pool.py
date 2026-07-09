"""Tests for the warm container pool."""

import asyncio
from unittest.mock import AsyncMock, MagicMock, patch

import pytest

from squadx_client.docker.pool import PooledContainer, WarmContainerPool


def _make_manager():
    """Build a DockerManager with a fully mocked Docker client."""
    with patch("squadx_client.docker.manager.settings") as mock_settings:
        mock_settings.supabase_url = ""
        mock_settings.supabase_anon_key = ""
        mock_settings.api_url = "http://localhost:8080"
        mgr = MagicMock()
        # Per-container mock dict, keyed by container_id, used by
        # _mock_container / _install_containers_get.
        mgr._containers_by_id = {}
        return mgr


def _make_container_mock(container_id: str, *, status_after_stop: str = "created"):
    """Build a fresh MagicMock for one container with the given post-stop status."""
    container = MagicMock()
    container.id = container_id
    container.short_id = container_id[:12]
    container.status = status_after_stop
    container.stop = MagicMock()
    container.reload = MagicMock(
        side_effect=lambda: setattr(container, "status", status_after_stop)
    )
    container.remove = MagicMock()
    return container


def _install_containers_get(manager):
    """Wire manager.client.containers.get to dispatch by container id."""
    manager.client.containers.get.side_effect = lambda cid: manager._containers_by_id[cid]


def _mock_container(manager, container_id: str, *, status_after_stop: str = "created"):
    """Register a container mock under its id and wire up dispatch."""
    container = _make_container_mock(container_id, status_after_stop=status_after_stop)
    manager._containers_by_id[container_id] = container
    _install_containers_get(manager)
    return container


def _patched_pool(*, target_size=2, min_ready=1, create_ids=None):
    """Construct a pool with the docker manager and create_container stubbed."""
    manager = _make_manager()
    create_ids = create_ids or ["c-1", "c-2", "c-3"]
    counter = {"i": 0}

    async def fake_create(config, task_id, agent_type):
        if counter["i"] >= len(create_ids):
            return None
        cid = create_ids[counter["i"]]
        counter["i"] += 1
        return cid

    manager.create_container = AsyncMock(side_effect=fake_create)

    pool = WarmContainerPool(
        manager=manager,
        image="squadx/agent:test",
        target_size=target_size,
        min_ready=min_ready,
        workspace_mount="/var/squadx/workspaces",
        refill_interval_seconds=0.05,
    )
    return pool, manager


class TestWarmContainerPoolInit:
    @pytest.mark.asyncio
    async def test_init_creates_min_ready_containers(self):
        pool, manager = _patched_pool(target_size=4, min_ready=2, create_ids=["a", "b", "c"])
        _mock_container(manager, "a")
        _mock_container(manager, "b")
        _mock_container(manager, "c")

        await pool.initialize()

        assert pool.is_enabled is True
        assert pool._available.qsize() == 2
        assert pool._total_created == 2

        await pool.shutdown()

    @pytest.mark.asyncio
    async def test_init_failure_disables_pool(self):
        """If create_container raises on init, the pool must end up disabled
        and never crash the caller — broken pools must not break the daemon."""
        manager = _make_manager()

        async def boom(*args, **kwargs):
            raise RuntimeError("docker down")

        manager.create_container = AsyncMock(side_effect=boom)

        pool = WarmContainerPool(
            manager=manager,
            image="x",
            target_size=2,
            min_ready=1,
            workspace_mount="/w",
        )

        await pool.initialize()  # must not raise

        assert pool.is_enabled is False
        assert pool._available.qsize() == 0

    def test_init_validates_args(self):
        with pytest.raises(ValueError):
            WarmContainerPool(
                manager=_make_manager(),
                image="x",
                target_size=0,
                min_ready=0,
                workspace_mount="/w",
            )
        with pytest.raises(ValueError):
            WarmContainerPool(
                manager=_make_manager(),
                image="x",
                target_size=2,
                min_ready=3,
                workspace_mount="/w",
            )


class TestWarmContainerPoolAcquireRelease:
    @pytest.mark.asyncio
    async def test_acquire_uses_warm_container(self):
        pool, manager = _patched_pool(target_size=2, min_ready=1, create_ids=["a", "b"])
        _mock_container(manager, "a")
        _mock_container(manager, "b")
        await pool.initialize()

        # Reset the create_container mock to count cold fallbacks AFTER init.
        cold_calls = {"n": 0}

        async def counting_create(*args, **kwargs):
            cold_calls["n"] += 1
            return None  # simulate cold-create failure to prove we didn't hit it

        manager.create_container = AsyncMock(side_effect=counting_create)

        pooled = await pool.acquire(task_id=1, agent_type="backend")

        assert pooled.container_id == "a"
        assert cold_calls["n"] == 0  # we did NOT cold-create
        assert pool._available.qsize() == 0
        assert pool._in_use[pooled.container_id] is pooled
        # Use count incremented
        assert pooled.use_count == 1

        await pool.shutdown()

    @pytest.mark.asyncio
    async def test_acquire_falls_back_to_cold_create_when_empty(self):
        pool, manager = _patched_pool(target_size=1, min_ready=0, create_ids=["a"])
        # Don't initialize — pool is empty.

        _mock_container(manager, "a")
        pooled = await pool.acquire(task_id=2, agent_type="frontend")

        assert pooled.container_id == "a"
        assert manager.create_container.await_count == 1

        await pool.shutdown()

    @pytest.mark.asyncio
    async def test_acquire_raises_on_cold_create_failure(self):
        manager = _make_manager()
        manager.create_container = AsyncMock(return_value=None)
        pool = WarmContainerPool(
            manager=manager,
            image="x",
            target_size=1,
            min_ready=0,
            workspace_mount="/w",
        )
        # Pool is not enabled (no initialize) and not started, but the
        # manager is "available" so acquire goes to cold fallback.

        with pytest.raises(RuntimeError):
            await pool.acquire(task_id=1, agent_type="backend")

    @pytest.mark.asyncio
    async def test_release_recycles_healthy_container(self):
        pool, manager = _patched_pool(target_size=2, min_ready=1, create_ids=["a", "b"])
        container_a = _mock_container(manager, "a", status_after_stop="created")
        _mock_container(manager, "b", status_after_stop="created")
        await pool.initialize()

        pooled = await pool.acquire(task_id=1, agent_type="backend")
        assert pooled.container_id == "a"

        await pool.release(pooled)

        assert pool._in_use == {}
        assert pool._available.qsize() == 1  # a was put back
        assert container_a.stop.call_count == 1

        await pool.shutdown()

    @pytest.mark.asyncio
    async def test_release_discards_unhealthy_container(self):
        pool, manager = _patched_pool(target_size=2, min_ready=1, create_ids=["a", "b"])
        _mock_container(manager, "a", status_after_stop="dead")
        _mock_container(manager, "b", status_after_stop="created")
        await pool.initialize()

        pooled = await pool.acquire(task_id=1, agent_type="backend")
        await pool.release(pooled)

        # Unhealthy container was removed, not recycled
        assert pool._available.qsize() == 0
        # The refill task is running but the interval is 0.05s; let it tick once
        await asyncio.sleep(0.1)
        assert pool._available.qsize() >= 1  # refill replaced it

        await pool.shutdown()


class TestWarmContainerPoolShutdown:
    @pytest.mark.asyncio
    async def test_shutdown_removes_all_containers(self):
        pool, manager = _patched_pool(target_size=2, min_ready=2, create_ids=["a", "b"])
        for cid in ("a", "b"):
            _mock_container(manager, cid, status_after_stop="created")
        await pool.initialize()

        # Acquire one to make sure shutdown handles in_use too
        pooled = await pool.acquire(task_id=1, agent_type="x")
        assert pooled.container_id in pool._in_use

        await pool.shutdown()

        assert pool.is_enabled is False
        assert pool._in_use == {}
        assert pool._available.qsize() == 0
        # Each container that init created was removed on shutdown
        for cid in ("a", "b"):
            assert manager._containers_by_id[cid].remove.call_count >= 1


class TestWarmContainerPoolStats:
    @pytest.mark.asyncio
    async def test_stats_reflects_state(self):
        pool, manager = _patched_pool(target_size=3, min_ready=2, create_ids=["a", "b", "c"])
        for cid in ("a", "b", "c"):
            _mock_container(manager, cid, status_after_stop="created")
        await pool.initialize()

        s = pool.stats
        assert s.target_size == 3
        assert s.available == 2
        assert s.in_use == 0
        assert s.total_created == 2
        assert s.enabled is True

        # Move one to in_use
        _ = await pool.acquire(task_id=1, agent_type="x")
        s = pool.stats
        assert s.available == 1
        assert s.in_use == 1

        d = s.to_dict()
        assert d["targetSize"] == 3
        assert d["available"] == 1
        assert d["inUse"] == 1
        assert d["enabled"] is True

        await pool.shutdown()


class TestSandboxPoolIntegration:
    """Smoke test: AgentSandbox uses the pool when manager.warm_pool is set."""

    @pytest.mark.asyncio
    async def test_sandbox_uses_pool_then_returns_on_cleanup(self):
        from squadx_client.docker.sandbox import AgentSandbox, SandboxStatus

        manager = MagicMock()
        manager.client = MagicMock()
        manager._containers_by_id = {}
        manager.connect = AsyncMock(return_value=True)

        # Pool's cold-create returns a fake container id
        manager.create_container = AsyncMock(side_effect=["a", "b", "c"])

        manager.start_container = AsyncMock(return_value=True)
        manager.stop_container = AsyncMock(return_value=True)
        manager.remove_container = AsyncMock(return_value=True)
        manager.get_vnc_port = AsyncMock(return_value=5900)
        manager.get_container_status = AsyncMock(return_value="running")
        manager.exec_command = AsyncMock(return_value=(0, ""))
        manager.apply_network_setup = AsyncMock(return_value=(True, ""))

        # Container that comes back to "created" on reload → recyclable
        container = _make_container_mock("a", status_after_stop="created")
        manager._containers_by_id["a"] = container
        _install_containers_get(manager)

        # Stub a small warm pool and attach it to the manager
        pool = WarmContainerPool(
            manager=manager,
            image="x",
            target_size=1,
            min_ready=0,
            workspace_mount="/w",
            refill_interval_seconds=10.0,
        )
        # Pre-seed the pool with one ready container (no init needed)
        pool._available.put_nowait(
            PooledContainer(
                container_id="a", container_name="squadx-pool-0", created_at=0.0
            )
        )
        pool._running = True
        pool._total_created = 1
        manager.warm_pool = pool

        sandbox = AgentSandbox(
            task_id=7,
            agent_type="backend",
            workspace_path="/tmp/ws",
            manager=manager,
            enable_live_streaming=False,
        )
        # Simulate the pool path that AgentSandbox.start() would do
        pooled = await manager.warm_pool.acquire(task_id=7, agent_type="backend")
        sandbox.container_id = pooled.container_id
        sandbox._pooled_container = pooled

        # Now drive cleanup() — should release to the pool, not remove
        await sandbox.cleanup()

        # Container was stopped (released back to pool) and NOT removed
        assert container.stop.call_count == 1
        assert manager.remove_container.await_count == 0
        # And the pool got it back
        assert pool._available.qsize() == 1
        assert pool._in_use == {}
        # Sandbox state is STOPPED
        assert sandbox.status == SandboxStatus.STOPPED
        assert sandbox.container_id is None
        assert sandbox._pooled_container is None

        await pool.shutdown()
