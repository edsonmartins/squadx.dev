"""
Task waiter pattern for blocking until tasks complete.
Supports callbacks for task completion, agent death, and timeout.
Inspired by ClawTeam's TaskWaiter.
"""
import asyncio
import logging
import time
from dataclasses import dataclass, field
from typing import Any, Callable, Optional

logger = logging.getLogger(__name__)


@dataclass
class WaitResult:
    status: str  # "completed", "timeout", "interrupted"
    elapsed_seconds: float = 0.0
    completed_tasks: list[str] = field(default_factory=list)
    pending_tasks: list[str] = field(default_factory=list)
    failed_tasks: list[str] = field(default_factory=list)
    dead_agents: list[str] = field(default_factory=list)

    @property
    def all_completed(self) -> bool:
        return len(self.pending_tasks) == 0 and len(self.failed_tasks) == 0

    def to_dict(self) -> dict:
        return {
            "status": self.status,
            "elapsedSeconds": self.elapsed_seconds,
            "completedTasks": self.completed_tasks,
            "pendingTasks": self.pending_tasks,
            "failedTasks": self.failed_tasks,
            "deadAgents": self.dead_agents,
            "allCompleted": self.all_completed,
        }


class TaskWaiter:
    """Blocks until all tasks complete, with callbacks and dead agent detection."""

    def __init__(
        self,
        poll_interval: float = 5.0,
        timeout: float = 300.0,
        on_task_complete: Optional[Callable] = None,
        on_agent_dead: Optional[Callable] = None,
        on_timeout: Optional[Callable] = None,
        on_progress: Optional[Callable] = None,
    ):
        self._poll_interval = poll_interval
        self._timeout = timeout
        self._on_task_complete = on_task_complete
        self._on_agent_dead = on_agent_dead
        self._on_timeout = on_timeout
        self._on_progress = on_progress
        self._interrupted = False

    def interrupt(self):
        self._interrupted = True

    async def wait(
        self,
        task_ids: list[str],
        check_status: Callable,
        check_agent_health: Optional[Callable] = None,
    ) -> WaitResult:
        """
        Wait for all tasks to complete.

        Args:
            task_ids: list of task IDs to wait for
            check_status: callable(task_id) -> status string ("pending", "in_progress", "completed", "failed")
            check_agent_health: callable() -> list of dead agent IDs
        """
        start_time = time.time()
        completed = set()
        failed = set()
        dead_agents = set()
        pending = set(task_ids)

        while not self._interrupted:
            elapsed = time.time() - start_time

            # Check timeout
            if elapsed > self._timeout:
                if self._on_timeout:
                    try:
                        self._on_timeout(list(pending))
                    except Exception as e:
                        logger.error(f"Timeout callback error: {e}")
                return WaitResult(
                    status="timeout",
                    elapsed_seconds=elapsed,
                    completed_tasks=list(completed),
                    pending_tasks=list(pending),
                    failed_tasks=list(failed),
                    dead_agents=list(dead_agents),
                )

            # Check task statuses
            newly_completed = []
            for task_id in list(pending):
                try:
                    status = check_status(task_id) if not asyncio.iscoroutinefunction(check_status) else await check_status(task_id)
                except Exception:
                    continue

                if status == "completed":
                    pending.discard(task_id)
                    completed.add(task_id)
                    newly_completed.append(task_id)
                elif status == "failed":
                    pending.discard(task_id)
                    failed.add(task_id)

            # Fire completion callbacks
            for task_id in newly_completed:
                if self._on_task_complete:
                    try:
                        self._on_task_complete(task_id)
                    except Exception as e:
                        logger.error(f"Task complete callback error: {e}")

            # Check agent health
            if check_agent_health:
                try:
                    new_dead = check_agent_health() if not asyncio.iscoroutinefunction(check_agent_health) else await check_agent_health()
                    for agent_id in new_dead:
                        if agent_id not in dead_agents:
                            dead_agents.add(agent_id)
                            if self._on_agent_dead:
                                try:
                                    self._on_agent_dead(agent_id)
                                except Exception as e:
                                    logger.error(f"Agent dead callback error: {e}")
                except Exception:
                    pass

            # Progress callback
            if self._on_progress:
                try:
                    self._on_progress(len(completed), len(pending), len(failed))
                except Exception:
                    pass

            # All done?
            if not pending:
                return WaitResult(
                    status="completed",
                    elapsed_seconds=time.time() - start_time,
                    completed_tasks=list(completed),
                    pending_tasks=[],
                    failed_tasks=list(failed),
                    dead_agents=list(dead_agents),
                )

            await asyncio.sleep(self._poll_interval)

        return WaitResult(
            status="interrupted",
            elapsed_seconds=time.time() - start_time,
            completed_tasks=list(completed),
            pending_tasks=list(pending),
            failed_tasks=list(failed),
            dead_agents=list(dead_agents),
        )
