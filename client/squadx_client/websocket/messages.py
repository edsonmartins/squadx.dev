"""Message type definitions for STOMP communication."""

from dataclasses import dataclass, field
from datetime import datetime
from enum import Enum
from typing import Any


class StompCommand(str, Enum):
    """STOMP protocol commands."""

    # Client commands
    CONNECT = "CONNECT"
    STOMP = "STOMP"
    SEND = "SEND"
    SUBSCRIBE = "SUBSCRIBE"
    UNSUBSCRIBE = "UNSUBSCRIBE"
    ACK = "ACK"
    NACK = "NACK"
    BEGIN = "BEGIN"
    COMMIT = "COMMIT"
    ABORT = "ABORT"
    DISCONNECT = "DISCONNECT"

    # Server commands
    CONNECTED = "CONNECTED"
    MESSAGE = "MESSAGE"
    RECEIPT = "RECEIPT"
    ERROR = "ERROR"


class MessageType(str, Enum):
    """Application-level message types."""

    # Task messages
    TASK_ASSIGNED = "task_assigned"
    TASK_CANCELLED = "task_cancelled"
    TASK_STATUS = "task_status"
    TASK_COMPLETED = "task_completed"
    TASK_FAILED = "task_failed"
    TASK_REJECTED = "task_rejected"

    # Execution messages
    EXECUTION_STARTED = "execution_started"
    EXECUTION_LOG = "execution_log"
    EXECUTION_PROGRESS = "execution_progress"
    EXECUTION_COMPLETED = "execution_completed"
    EXECUTION_FAILED = "execution_failed"

    # Live view messages
    START_LIVE_VIEW = "start_live_view"
    LIVE_VIEW_READY = "live_view_ready"
    STOP_LIVE_VIEW = "stop_live_view"

    # Client messages
    CLIENT_REGISTER = "client_register"
    CLIENT_HEARTBEAT = "client_heartbeat"

    # System messages
    PING = "ping"
    PONG = "pong"


@dataclass
class StompFrame:
    """Represents a STOMP protocol frame."""

    command: StompCommand
    headers: dict[str, str] = field(default_factory=dict)
    body: str = ""

    def encode(self) -> bytes:
        """Encode frame to bytes for transmission."""
        lines = [self.command.value]

        for key, value in self.headers.items():
            # Escape special characters in header values
            escaped_value = (
                value.replace("\\", "\\\\")
                .replace("\r", "\\r")
                .replace("\n", "\\n")
                .replace(":", "\\c")
            )
            lines.append(f"{key}:{escaped_value}")

        lines.append("")  # Empty line between headers and body
        lines.append(self.body)

        frame = "\n".join(lines) + "\x00"
        return frame.encode("utf-8")

    @classmethod
    def decode(cls, data: bytes | str) -> "StompFrame":
        """Decode bytes to a STOMP frame."""
        if isinstance(data, bytes):
            data = data.decode("utf-8")

        # Remove null terminator
        data = data.rstrip("\x00")

        lines = data.split("\n")
        if not lines:
            raise ValueError("Empty STOMP frame")

        command = StompCommand(lines[0])
        headers: dict[str, str] = {}
        body_start = 1

        # Parse headers
        for i, line in enumerate(lines[1:], start=1):
            if line == "":
                body_start = i + 1
                break
            if ":" in line:
                key, value = line.split(":", 1)
                # Unescape special characters
                value = (
                    value.replace("\\c", ":")
                    .replace("\\n", "\n")
                    .replace("\\r", "\r")
                    .replace("\\\\", "\\")
                )
                headers[key] = value

        # Parse body
        body = "\n".join(lines[body_start:]) if body_start < len(lines) else ""

        return cls(command=command, headers=headers, body=body)


@dataclass
class TaskMessage:
    """Task-related message payload."""

    task_id: int
    type: MessageType
    data: dict[str, Any] = field(default_factory=dict)
    timestamp: datetime = field(default_factory=datetime.now)

    def to_dict(self) -> dict[str, Any]:
        """Convert to dictionary for JSON serialization."""
        return {
            "task_id": self.task_id,
            "type": self.type.value,
            "data": self.data,
            "timestamp": self.timestamp.isoformat(),
        }


@dataclass
class StatusMessage:
    """Task status update message."""

    task_id: int
    status: str
    progress: int = 0
    current_step: str = ""
    timestamp: datetime = field(default_factory=datetime.now)

    def to_dict(self) -> dict[str, Any]:
        """Convert to dictionary for JSON serialization."""
        return {
            "task_id": self.task_id,
            "status": self.status,
            "progress": self.progress,
            "current_step": self.current_step,
            "timestamp": self.timestamp.isoformat(),
        }


@dataclass
class LogMessage:
    """Execution log message."""

    execution_id: int
    level: str
    message: str
    agent: str = ""
    timestamp: datetime = field(default_factory=datetime.now)

    def to_dict(self) -> dict[str, Any]:
        """Convert to dictionary for JSON serialization."""
        return {
            "execution_id": self.execution_id,
            "level": self.level,
            "message": self.message,
            "agent": self.agent,
            "timestamp": self.timestamp.isoformat(),
        }
