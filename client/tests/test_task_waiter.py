"""Tests for task waiter pattern module."""

import asyncio
from unittest.mock import MagicMock, AsyncMock

import pytest

from squadx_client.orchestrator.waiter import TaskWaiter, WaitResult


class TestWaitResult:
    """Test WaitResult dataclass."""

    def test_all_completed_true_when_no_pending_or_failed(self):
        result = WaitResult(
            status="completed",
            completed_tasks=["t1", "t2"],
            pending_tasks=[],
            failed_tasks=[],
        )
        assert result.all_completed is True

    def test_all_completed_false_when_pending(self):
        result = WaitResult(
            status="timeout",
            completed_tasks=["t1"],
            pending_tasks=["t2"],
            failed_tasks=[],
        )
        assert result.all_completed is False

    def test_all_completed_false_when_failed(self):
        result = WaitResult(
            status="completed",
            completed_tasks=["t1"],
            pending_tasks=[],
            failed_tasks=["t2"],
        )
        assert result.all_completed is False

    def test_to_dict_contains_expected_keys(self):
        result = WaitResult(
            status="completed",
            elapsed_seconds=10.5,
            completed_tasks=["t1"],
            pending_tasks=[],
            failed_tasks=["t2"],
            dead_agents=["a1"],
        )
        d = result.to_dict()
        assert d["status"] == "completed"
        assert d["elapsedSeconds"] == 10.5
        assert d["completedTasks"] == ["t1"]
        assert d["failedTasks"] == ["t2"]
        assert d["deadAgents"] == ["a1"]
        assert "allCompleted" in d


class TestTaskWaiter:
    """Test TaskWaiter operations."""

    @pytest.mark.asyncio
    async def test_all_tasks_complete_immediately(self):
        statuses = {"t1": "completed", "t2": "completed"}
        waiter = TaskWaiter(poll_interval=0.01, timeout=5.0)

        result = await waiter.wait(
            task_ids=["t1", "t2"],
            check_status=lambda tid: statuses[tid],
        )

        assert result.status == "completed"
        assert set(result.completed_tasks) == {"t1", "t2"}
        assert result.pending_tasks == []
        assert result.all_completed is True

    @pytest.mark.asyncio
    async def test_tasks_complete_over_time(self):
        call_count = {"n": 0}

        def check_status(tid):
            call_count["n"] += 1
            if tid == "t1":
                return "completed"
            # t2 completes on second poll
            return "completed" if call_count["n"] > 2 else "in_progress"

        waiter = TaskWaiter(poll_interval=0.01, timeout=5.0)
        result = await waiter.wait(
            task_ids=["t1", "t2"],
            check_status=check_status,
        )

        assert result.status == "completed"
        assert set(result.completed_tasks) == {"t1", "t2"}

    @pytest.mark.asyncio
    async def test_timeout(self):
        waiter = TaskWaiter(poll_interval=0.01, timeout=0.05)

        result = await waiter.wait(
            task_ids=["t1"],
            check_status=lambda tid: "in_progress",
        )

        assert result.status == "timeout"
        assert result.pending_tasks == ["t1"]

    @pytest.mark.asyncio
    async def test_timeout_callback_fires(self):
        callback = MagicMock()
        waiter = TaskWaiter(poll_interval=0.01, timeout=0.05, on_timeout=callback)

        await waiter.wait(
            task_ids=["t1"],
            check_status=lambda tid: "in_progress",
        )

        callback.assert_called_once()
        args = callback.call_args[0][0]
        assert "t1" in args

    @pytest.mark.asyncio
    async def test_interrupt(self):
        waiter = TaskWaiter(poll_interval=0.01, timeout=10.0)

        async def interrupt_soon():
            await asyncio.sleep(0.03)
            waiter.interrupt()

        asyncio.create_task(interrupt_soon())

        result = await waiter.wait(
            task_ids=["t1"],
            check_status=lambda tid: "in_progress",
        )

        assert result.status == "interrupted"
        assert "t1" in result.pending_tasks

    @pytest.mark.asyncio
    async def test_task_complete_callback_fires(self):
        callback = MagicMock()
        waiter = TaskWaiter(poll_interval=0.01, timeout=5.0, on_task_complete=callback)

        result = await waiter.wait(
            task_ids=["t1"],
            check_status=lambda tid: "completed",
        )

        assert result.status == "completed"
        callback.assert_called_once_with("t1")

    @pytest.mark.asyncio
    async def test_failed_tasks_tracked(self):
        statuses = {"t1": "completed", "t2": "failed"}
        waiter = TaskWaiter(poll_interval=0.01, timeout=5.0)

        result = await waiter.wait(
            task_ids=["t1", "t2"],
            check_status=lambda tid: statuses[tid],
        )

        assert result.status == "completed"
        assert "t1" in result.completed_tasks
        assert "t2" in result.failed_tasks
        assert result.all_completed is False

    @pytest.mark.asyncio
    async def test_dead_agent_detection(self):
        callback = MagicMock()
        waiter = TaskWaiter(poll_interval=0.01, timeout=5.0, on_agent_dead=callback)

        call_count = {"n": 0}

        def check_status(tid):
            call_count["n"] += 1
            return "completed" if call_count["n"] > 1 else "in_progress"

        result = await waiter.wait(
            task_ids=["t1"],
            check_status=check_status,
            check_agent_health=lambda: ["dead-agent-1"],
        )

        assert result.status == "completed"
        assert "dead-agent-1" in result.dead_agents
        callback.assert_called_with("dead-agent-1")

    @pytest.mark.asyncio
    async def test_progress_callback_fires(self):
        callback = MagicMock()
        waiter = TaskWaiter(poll_interval=0.01, timeout=5.0, on_progress=callback)

        result = await waiter.wait(
            task_ids=["t1"],
            check_status=lambda tid: "completed",
        )

        assert result.status == "completed"
        assert callback.call_count >= 1

    @pytest.mark.asyncio
    async def test_check_status_exception_handled(self):
        """If check_status raises, the task stays pending."""
        call_count = {"n": 0}

        def check_status(tid):
            call_count["n"] += 1
            if call_count["n"] <= 1:
                raise RuntimeError("API error")
            return "completed"

        waiter = TaskWaiter(poll_interval=0.01, timeout=5.0)
        result = await waiter.wait(
            task_ids=["t1"],
            check_status=check_status,
        )

        assert result.status == "completed"
        assert "t1" in result.completed_tasks

    @pytest.mark.asyncio
    async def test_async_check_status(self):
        async def async_check(tid):
            return "completed"

        waiter = TaskWaiter(poll_interval=0.01, timeout=5.0)
        result = await waiter.wait(
            task_ids=["t1"],
            check_status=async_check,
        )

        assert result.status == "completed"
        assert "t1" in result.completed_tasks
