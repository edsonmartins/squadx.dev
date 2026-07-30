"""Shared Docker sandbox status/result types."""

from __future__ import annotations

from dataclasses import dataclass
from enum import Enum


class SandboxStatus(str, Enum):
    """Sandbox lifecycle status."""

    CREATED = "created"
    STARTING = "starting"
    RUNNING = "running"
    STOPPING = "stopping"
    STOPPED = "stopped"
    ERROR = "error"


@dataclass
class SandboxResult:
    """Result of a sandbox command execution."""

    success: bool
    exit_code: int
    output: str
    error: str | None = None
    duration_seconds: float = 0.0
