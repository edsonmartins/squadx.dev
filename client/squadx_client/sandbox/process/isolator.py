"""Detect PROCESS isolator (bwrap / Seatbelt / unsafe none)."""

from __future__ import annotations

import logging
import os
import platform
import shutil
from enum import Enum

from squadx_client.config import settings
from squadx_client.sandbox.errors import SandboxNotSupportedError

logger = logging.getLogger(__name__)


class ProcessIsolator(str, Enum):
    BWRAP = "bwrap"
    SEATBELT = "seatbelt"
    NONE = "none"  # only with SQUADX_PROCESS_UNSAFE=1


def process_network_mode() -> str:
    """``allow`` (default) or ``deny`` (bwrap --unshare-net / Seatbelt deny network)."""
    raw = str(
        getattr(settings, "process_network", None)
        or os.environ.get("SQUADX_PROCESS_NETWORK", "allow")
    ).strip().lower()
    return "deny" if raw in ("deny", "none", "off", "unshare") else "allow"


def detect_process_isolator() -> ProcessIsolator:
    """Pick the best available isolator for this host."""
    system = platform.system()
    if system == "Linux" and shutil.which("bwrap"):
        return ProcessIsolator.BWRAP
    if system == "Darwin" and shutil.which("sandbox-exec"):
        return ProcessIsolator.SEATBELT
    unsafe = str(os.environ.get("SQUADX_PROCESS_UNSAFE", "")).lower() in (
        "1",
        "true",
        "yes",
    )
    if unsafe:
        logger.warning(
            "process_isolator_none SQUADX_PROCESS_UNSAFE=1 — commands run without OS sandbox"
        )
        return ProcessIsolator.NONE
    if system == "Linux":
        raise SandboxNotSupportedError(
            "PROCESS backend requires bubblewrap (bwrap) on Linux. "
            "Install: apt install bubblewrap / dnf install bubblewrap, "
            "or use SQUADX_SANDBOX_BACKEND=docker."
        )
    if system == "Darwin":
        raise SandboxNotSupportedError(
            "PROCESS backend requires sandbox-exec (Seatbelt) on macOS. "
            "Use SQUADX_SANDBOX_BACKEND=docker (Colima) if unavailable."
        )
    raise SandboxNotSupportedError(
        f"PROCESS backend unsupported on {system}; use docker."
    )
