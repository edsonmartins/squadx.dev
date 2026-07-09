"""Attention Budget classification for run events (RFC-0005 §1).

Mirrors the backend ``RunEventMetadata`` so the daemon can tag each emitted log/event with a
``visibility`` (human | audit | debug) and ``importance`` (low | normal | high | blocking). The
dashboard stays quiet by default, surfacing only ``human`` events.
"""

from __future__ import annotations

from typing import NamedTuple


class RunEventMetadata(NamedTuple):
    visibility: str
    importance: str


_HUMAN_NORMAL = RunEventMetadata("human", "normal")

# Known event types → metadata (mirror of the backend map / opentag thread-runtime Delta 7).
_BY_TYPE: dict[str, RunEventMetadata] = {
    "run.created": RunEventMetadata("audit", "normal"),
    "admission.decided": RunEventMetadata("audit", "normal"),
    "follow_up_request.queued": RunEventMetadata("audit", "normal"),
    "subtask.started": RunEventMetadata("debug", "low"),
    "tool.log": RunEventMetadata("debug", "low"),
    "agent.prompt": RunEventMetadata("audit", "low"),
    "context_packet.generated": RunEventMetadata("audit", "low"),
    "run.escalated": RunEventMetadata("human", "blocking"),
    "run.blocked": RunEventMetadata("human", "blocking"),
    "run.completed": RunEventMetadata("human", "high"),
    "run.failed": RunEventMetadata("human", "high"),
    "cost.budget_exceeded": RunEventMetadata("human", "blocking"),
}

# Review severities map onto importance; only blockers are human-facing.
_SEVERITY_TO_IMPORTANCE: dict[str, str] = {
    "blocker": "blocking",
    "major": "high",
    "minor": "normal",
    "nit": "low",
}


def for_level(level: str | None) -> RunEventMetadata:
    """Default metadata for a log level. Unknown level → safe ``human/normal``."""
    if not level:
        return _HUMAN_NORMAL
    normalized = level.upper()
    if normalized in ("TRACE", "DEBUG"):
        return RunEventMetadata("debug", "low")
    if normalized == "INFO":
        return RunEventMetadata("audit", "normal")
    if normalized in ("WARN", "WARNING"):
        return RunEventMetadata("human", "normal")
    if normalized in ("ERROR", "FATAL", "CRITICAL"):
        return RunEventMetadata("human", "high")
    return _HUMAN_NORMAL


def for_severity(severity: str | None) -> RunEventMetadata:
    """Metadata for a review finding severity (blocker/major/minor/nit)."""
    if not severity:
        return _HUMAN_NORMAL
    importance = _SEVERITY_TO_IMPORTANCE.get(severity.lower(), "normal")
    visibility = "human" if severity.lower() == "blocker" else "audit"
    return RunEventMetadata(visibility, importance)


def default_run_event_metadata(
    event_type: str | None = None,
    level: str | None = None,
    visibility: str | None = None,
    importance: str | None = None,
) -> RunEventMetadata:
    """Resolve metadata: explicit values win, then event type, then log level.

    Unknown type/level falls back to ``human/normal`` so nothing is hidden by accident.
    """
    base = _BY_TYPE.get(event_type) if event_type else None
    if base is None:
        base = for_level(level)
    return RunEventMetadata(
        visibility=visibility or base.visibility,
        importance=importance or base.importance,
    )
