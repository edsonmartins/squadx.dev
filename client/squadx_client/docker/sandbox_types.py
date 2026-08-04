"""Compatibility names for the shared sandbox lifecycle and command result."""

from __future__ import annotations

from squadx_client.sandbox.types import CommandResult, SandboxLifecycleStatus

# Keep old import paths working without maintaining duplicate runtime types.
SandboxStatus = SandboxLifecycleStatus
SandboxResult = CommandResult

__all__ = ["SandboxResult", "SandboxStatus"]
