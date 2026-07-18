"""
Agent lifecycle protocol with heartbeat, idle detection, and dead agent recovery.
Inspired by ClawTeam's lifecycle management.
"""
import asyncio
import logging
import time
from collections.abc import Callable
from dataclasses import dataclass, field
from enum import Enum

logger = logging.getLogger(__name__)


class AgentState(str, Enum):
    STARTING = "starting"
    READY = "ready"
    WORKING = "working"
    IDLE = "idle"
    SHUTTING_DOWN = "shutting_down"
    DEAD = "dead"


@dataclass
class AgentStatus:
    agent_id: str
    agent_name: str
    state: AgentState = AgentState.STARTING
    last_heartbeat: float = field(default_factory=time.time)
    current_task_id: str | None = None
    idle_since: float | None = None
    error_message: str | None = None

    @property
    def seconds_since_heartbeat(self) -> float:
        return time.time() - self.last_heartbeat

    @property
    def is_alive(self) -> bool:
        return self.state not in {AgentState.DEAD, AgentState.SHUTTING_DOWN}

    def to_dict(self) -> dict:
        return {
            "agentId": self.agent_id,
            "agentName": self.agent_name,
            "state": self.state.value,
            "lastHeartbeat": self.last_heartbeat,
            "currentTaskId": self.current_task_id,
            "idleSince": self.idle_since,
            "secondsSinceHeartbeat": self.seconds_since_heartbeat,
            "error": self.error_message,
        }


class AgentLifecycleProtocol:
    """Manages agent lifecycle with heartbeat and dead agent detection."""

    def __init__(self, heartbeat_interval: int = 30, dead_threshold: int = 120):
        self._agents: dict[str, AgentStatus] = {}
        self._heartbeat_interval = heartbeat_interval
        self._dead_threshold = dead_threshold
        self._on_agent_dead: Callable | None = None
        self._on_agent_idle: Callable | None = None

    def on_agent_dead(self, callback: Callable):
        self._on_agent_dead = callback

    def on_agent_idle(self, callback: Callable):
        self._on_agent_idle = callback

    def register(self, agent_id: str, agent_name: str) -> AgentStatus:
        status = AgentStatus(agent_id=agent_id, agent_name=agent_name, state=AgentState.STARTING)
        self._agents[agent_id] = status
        return status

    def heartbeat(self, agent_id: str):
        if agent_id in self._agents:
            self._agents[agent_id].last_heartbeat = time.time()

    def transition(self, agent_id: str, new_state: AgentState, task_id: str | None = None):
        status = self._agents.get(agent_id)
        if status is None:
            return
        status.state = new_state
        if new_state == AgentState.WORKING:
            status.current_task_id = task_id
            status.idle_since = None
        elif new_state == AgentState.IDLE:
            status.current_task_id = None
            status.idle_since = time.time()
            if self._on_agent_idle:
                try:
                    self._on_agent_idle(agent_id)
                except Exception as e:
                    logger.error(f"Idle callback error: {e}")

    def detect_dead_agents(self) -> list[AgentStatus]:
        dead = []
        for status in self._agents.values():
            if status.is_alive and status.seconds_since_heartbeat > self._dead_threshold:
                status.state = AgentState.DEAD
                status.error_message = f"No heartbeat for {status.seconds_since_heartbeat:.0f}s"
                dead.append(status)
                if self._on_agent_dead:
                    try:
                        self._on_agent_dead(status.agent_id, status.current_task_id)
                    except Exception as e:
                        logger.error(f"Dead agent callback error: {e}")
        return dead

    def get_status(self, agent_id: str) -> AgentStatus | None:
        return self._agents.get(agent_id)

    def get_all_statuses(self) -> list[AgentStatus]:
        return list(self._agents.values())

    def get_idle_agents(self) -> list[AgentStatus]:
        return [s for s in self._agents.values() if s.state == AgentState.IDLE]

    def get_working_agents(self) -> list[AgentStatus]:
        return [s for s in self._agents.values() if s.state == AgentState.WORKING]

    def remove(self, agent_id: str):
        self._agents.pop(agent_id, None)

    async def start_heartbeat_monitor(self):
        while True:
            await asyncio.sleep(self._heartbeat_interval)
            self.detect_dead_agents()
