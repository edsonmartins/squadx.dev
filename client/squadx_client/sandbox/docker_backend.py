"""Docker ``SandboxBackend`` — builds ``AgentSandbox`` sessions."""

from __future__ import annotations

from typing import TYPE_CHECKING, Any

from squadx_client.sandbox.session import SandboxSession
from squadx_client.sandbox.types import SandboxBackendKind

if TYPE_CHECKING:
    from squadx_client.docker.manager import DockerManager


class DockerSandboxBackend:
    """Factory for Docker sessions; implementation lives in ``docker/sandbox.py``."""

    def __init__(self, manager: DockerManager | None = None) -> None:
        self._manager = manager

    @property
    def kind(self) -> SandboxBackendKind:
        return SandboxBackendKind.DOCKER

    def supports_live_view(self) -> bool:
        return True

    def supports_egress_sidecar(self) -> bool:
        return True

    def create_session(
        self,
        *,
        task_id: int,
        agent_type: str,
        workspace_path: str,
        network_policy: str | None = None,
        enable_live_streaming: bool = True,
        ttl_seconds: int | None = None,
    ) -> SandboxSession:
        """Build an unstarted Docker session (settings applied on ``start()``)."""
        from squadx_client.docker.sandbox import AgentSandbox

        kwargs: dict[str, Any] = {
            "task_id": task_id,
            "agent_type": agent_type,
            "workspace_path": workspace_path,
            "network_policy": network_policy,
            "enable_live_streaming": enable_live_streaming,
        }
        if self._manager is not None:
            kwargs["manager"] = self._manager
        if ttl_seconds is not None:
            kwargs["ttl_seconds"] = ttl_seconds
        return AgentSandbox(**kwargs)
