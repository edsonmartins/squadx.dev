"""Backward-compatible re-export of PROCESS sandbox (prefer ``sandbox.process``)."""

from squadx_client.sandbox.process import (
    ProcessIsolator,
    ProcessSandboxBackend,
    ProcessSession,
    build_isolated_command,
    detect_process_isolator,
    inject_bwrap_env,
    process_network_mode,
)

__all__ = [
    "ProcessIsolator",
    "ProcessSandboxBackend",
    "ProcessSession",
    "build_isolated_command",
    "detect_process_isolator",
    "inject_bwrap_env",
    "process_network_mode",
]
