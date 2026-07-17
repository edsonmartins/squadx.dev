"""
Sandbox lifecycle management with TTL-based expiration.
Inspired by OpenSandbox's lifecycle state machine.
"""
import asyncio
import logging
from collections.abc import Callable
from dataclasses import dataclass, field
from datetime import UTC, datetime
from enum import Enum
from threading import Lock

logger = logging.getLogger(__name__)


class SandboxState(str, Enum):
    PENDING = "pending"
    CREATING = "creating"
    RUNNING = "running"
    PAUSED = "paused"
    STOPPING = "stopping"
    STOPPED = "stopped"
    EXPIRED = "expired"
    FAILED = "failed"

# Valid state transitions
VALID_TRANSITIONS = {
    SandboxState.PENDING: {SandboxState.CREATING, SandboxState.FAILED},
    SandboxState.CREATING: {SandboxState.RUNNING, SandboxState.FAILED},
    SandboxState.RUNNING: {SandboxState.PAUSED, SandboxState.STOPPING, SandboxState.EXPIRED, SandboxState.FAILED},
    SandboxState.PAUSED: {SandboxState.RUNNING, SandboxState.STOPPING, SandboxState.EXPIRED, SandboxState.FAILED},
    SandboxState.STOPPING: {SandboxState.STOPPED, SandboxState.FAILED},
    SandboxState.STOPPED: set(),  # Terminal
    SandboxState.EXPIRED: set(),  # Terminal
    SandboxState.FAILED: set(),  # Terminal
}


@dataclass
class SandboxInfo:
    """Tracks sandbox metadata and lifecycle state."""
    sandbox_id: str
    container_id: str | None = None
    state: SandboxState = SandboxState.PENDING
    image: str = ""
    created_at: datetime = field(default_factory=lambda: datetime.now(UTC))
    started_at: datetime | None = None
    stopped_at: datetime | None = None
    expires_at: datetime | None = None
    last_activity: datetime | None = None
    error_message: str | None = None
    metadata: dict = field(default_factory=dict)

    @property
    def is_terminal(self) -> bool:
        return self.state in {SandboxState.STOPPED, SandboxState.EXPIRED, SandboxState.FAILED}

    @property
    def is_active(self) -> bool:
        return self.state in {SandboxState.RUNNING, SandboxState.PAUSED}

    @property
    def uptime_seconds(self) -> float:
        if self.started_at is None:
            return 0.0
        end = self.stopped_at or datetime.now(UTC)
        return (end - self.started_at).total_seconds()

    @property
    def is_expired(self) -> bool:
        if self.expires_at is None:
            return False
        return datetime.now(UTC) >= self.expires_at

    def to_dict(self) -> dict:
        return {
            "sandboxId": self.sandbox_id,
            "containerId": self.container_id,
            "state": self.state.value,
            "image": self.image,
            "createdAt": self.created_at.isoformat(),
            "startedAt": self.started_at.isoformat() if self.started_at else None,
            "stoppedAt": self.stopped_at.isoformat() if self.stopped_at else None,
            "expiresAt": self.expires_at.isoformat() if self.expires_at else None,
            "uptimeSeconds": self.uptime_seconds,
            "error": self.error_message,
            "metadata": self.metadata,
        }


class SandboxLifecycleManager:
    """Manages sandbox lifecycle with TTL-based expiration and state tracking."""

    def __init__(self, default_ttl_seconds: int = 3600, max_ttl_seconds: int = 86400):
        self._sandboxes: dict[str, SandboxInfo] = {}
        self._expiration_tasks: dict[str, asyncio.Task] = {}
        self._lock = Lock()
        self._default_ttl = default_ttl_seconds
        self._max_ttl = max_ttl_seconds
        self._on_expire_callback: Callable | None = None
        self._on_state_change_callback: Callable | None = None

    def on_expire(self, callback: Callable):
        """Register callback for sandbox expiration. callback(sandbox_id)"""
        self._on_expire_callback = callback

    def on_state_change(self, callback: Callable):
        """Register callback for state changes. callback(sandbox_id, old_state, new_state)"""
        self._on_state_change_callback = callback

    def register(self, sandbox_id: str, image: str = "", ttl_seconds: int | None = None, metadata: dict = None) -> SandboxInfo:
        """Register a new sandbox with optional TTL."""
        ttl = min(ttl_seconds or self._default_ttl, self._max_ttl)
        now = datetime.now(UTC)

        info = SandboxInfo(
            sandbox_id=sandbox_id,
            image=image,
            created_at=now,
            expires_at=datetime.fromtimestamp(now.timestamp() + ttl, tz=UTC),
            metadata=metadata or {},
        )

        with self._lock:
            self._sandboxes[sandbox_id] = info

        logger.info(f"Sandbox {sandbox_id} registered, TTL={ttl}s")
        return info

    def transition(self, sandbox_id: str, new_state: SandboxState, error: str | None = None) -> bool:
        """Transition sandbox to a new state. Returns False if transition is invalid."""
        with self._lock:
            info = self._sandboxes.get(sandbox_id)
            if info is None:
                logger.warning(f"Sandbox {sandbox_id} not found")
                return False

            if new_state not in VALID_TRANSITIONS.get(info.state, set()):
                logger.warning(
                    f"Invalid transition {info.state.value} -> {new_state.value} "
                    f"for sandbox {sandbox_id}"
                )
                return False

            old_state = info.state
            info.state = new_state
            now = datetime.now(UTC)

            if new_state == SandboxState.RUNNING:
                info.started_at = info.started_at or now
                info.last_activity = now
            elif new_state in {SandboxState.STOPPED, SandboxState.EXPIRED, SandboxState.FAILED}:
                info.stopped_at = now

            if error:
                info.error_message = error

        logger.info(f"Sandbox {sandbox_id}: {old_state.value} -> {new_state.value}")

        if self._on_state_change_callback:
            try:
                self._on_state_change_callback(sandbox_id, old_state, new_state)
            except Exception as e:
                logger.error(f"State change callback error: {e}")

        return True

    def renew(self, sandbox_id: str, additional_seconds: int | None = None) -> datetime | None:
        """Extend sandbox TTL. Returns new expiration time."""
        with self._lock:
            info = self._sandboxes.get(sandbox_id)
            if info is None or info.is_terminal:
                return None

            extra = min(additional_seconds or self._default_ttl, self._max_ttl)
            now = datetime.now(UTC)
            info.expires_at = datetime.fromtimestamp(now.timestamp() + extra, tz=UTC)
            info.last_activity = now

        logger.info(f"Sandbox {sandbox_id} renewed, new TTL={extra}s")
        return info.expires_at

    def record_activity(self, sandbox_id: str):
        """Update last activity timestamp (for idle detection)."""
        with self._lock:
            info = self._sandboxes.get(sandbox_id)
            if info and info.is_active:
                info.last_activity = datetime.now(UTC)

    def get(self, sandbox_id: str) -> SandboxInfo | None:
        """Get sandbox info."""
        return self._sandboxes.get(sandbox_id)

    def list_active(self) -> list[SandboxInfo]:
        """List all active (non-terminal) sandboxes."""
        return [s for s in self._sandboxes.values() if s.is_active]

    def list_all(self) -> list[SandboxInfo]:
        """List all sandboxes."""
        return list(self._sandboxes.values())

    def remove(self, sandbox_id: str):
        """Remove sandbox from tracking."""
        with self._lock:
            self._sandboxes.pop(sandbox_id, None)
        task = self._expiration_tasks.pop(sandbox_id, None)
        if task:
            task.cancel()

    async def start_expiration_monitor(self):
        """Background task that checks for expired sandboxes."""
        while True:
            await asyncio.sleep(30)  # Check every 30 seconds
            expired = []

            with self._lock:
                for sid, info in self._sandboxes.items():
                    if info.is_active and info.is_expired:
                        expired.append(sid)
                # Transition INSIDE the lock to avoid race condition
                for sid in expired:
                    info = self._sandboxes.get(sid)
                    if info and info.is_active:
                        now = datetime.now(UTC)
                        info.state = SandboxState.EXPIRED
                        info.stopped_at = now
                        logger.warning(f"Sandbox {sid} expired, triggering cleanup")

            # Callbacks OUTSIDE the lock to avoid deadlock
            for sid in expired:
                if self._on_state_change_callback:
                    try:
                        self._on_state_change_callback(sid, SandboxState.RUNNING, SandboxState.EXPIRED)
                    except Exception as e:
                        logger.error(f"State change callback error: {e}")
                if self._on_expire_callback:
                    try:
                        if asyncio.iscoroutinefunction(self._on_expire_callback):
                            await self._on_expire_callback(sid)
                        else:
                            self._on_expire_callback(sid)
                    except Exception as e:
                        logger.error(f"Expire callback error for {sid}: {e}")

    def get_stats(self) -> dict:
        """Get summary statistics."""
        states = {}
        for info in self._sandboxes.values():
            states[info.state.value] = states.get(info.state.value, 0) + 1

        active = self.list_active()
        return {
            "total": len(self._sandboxes),
            "active": len(active),
            "byState": states,
            "avgUptimeSeconds": sum(s.uptime_seconds for s in active) / max(len(active), 1),
        }
