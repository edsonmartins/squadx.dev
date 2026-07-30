"""``SandboxBackend`` Protocol — factory surface only (ADR-0009).

Production uses ``create_sandbox_session()`` → ``SandboxSession``.
Backends only need to construct sessions and advertise capabilities.
"""

from __future__ import annotations

from typing import Protocol, runtime_checkable

from squadx_client.sandbox.session import SandboxSession
from squadx_client.sandbox.types import SandboxBackendKind


@runtime_checkable
class SandboxBackend(Protocol):
    """Pluggable isolator factory: create sessions + feature flags."""

    @property
    def kind(self) -> SandboxBackendKind: ...

    def create_session(
        self,
        *,
        task_id: int,
        agent_type: str,
        workspace_path: str,
        network_policy: str | None = None,
        enable_live_streaming: bool = True,
        ttl_seconds: int | None = None,
    ) -> SandboxSession: ...

    def supports_live_view(self) -> bool: ...

    def supports_egress_sidecar(self) -> bool: ...


__all__ = ["SandboxBackend"]
