"""ProcessSandboxBackend — create SandboxSession for PROCESS (ADR-0009)."""

from __future__ import annotations

import logging

from squadx_client.sandbox.process.isolator import (
    ProcessIsolator,
    detect_process_isolator,
    process_network_mode,
)
from squadx_client.sandbox.process.session import ProcessSession
from squadx_client.sandbox.types import SandboxBackendKind

logger = logging.getLogger(__name__)


class ProcessSandboxBackend:
    """SandboxBackend for bubblewrap / Seatbelt (session-oriented hot path)."""

    def __init__(self, isolator: ProcessIsolator | None = None) -> None:
        self._isolator = isolator

    @property
    def kind(self) -> SandboxBackendKind:
        return SandboxBackendKind.PROCESS

    def supports_live_view(self) -> bool:
        return False

    def supports_egress_sidecar(self) -> bool:
        return False

    def _resolve_isolator(self) -> ProcessIsolator:
        if self._isolator is None:
            self._isolator = detect_process_isolator()
        return self._isolator

    def create_session(
        self,
        *,
        task_id: int,
        agent_type: str,
        workspace_path: str,
        network_policy: str | None = None,
        enable_live_streaming: bool = False,
        ttl_seconds: int | None = None,
    ) -> ProcessSession:
        if enable_live_streaming:
            logger.warning(
                "process_backend_live_ignored task=%s — Live View unsupported", task_id
            )
        isolator = self._resolve_isolator()
        if process_network_mode() == "allow":
            logger.warning(
                "process_network_shared task=%s policy=%s — no Docker egress sidecar; "
                "set SQUADX_PROCESS_NETWORK=deny for bwrap --unshare-net",
                task_id,
                network_policy,
            )
        return ProcessSession(
            task_id=task_id,
            agent_type=agent_type,
            workspace_path=workspace_path,
            isolator=isolator,
            network_policy=network_policy,
        )
