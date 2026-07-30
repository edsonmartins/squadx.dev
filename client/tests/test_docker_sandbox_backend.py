"""Unit tests for DockerSandboxBackend (session-oriented API)."""

from __future__ import annotations

from unittest.mock import MagicMock

from squadx_client.docker.sandbox import AgentSandbox
from squadx_client.sandbox.docker_backend import DockerSandboxBackend
from squadx_client.sandbox.types import SandboxBackendKind


def test_create_session_builds_agent_sandbox() -> None:
    backend = DockerSandboxBackend()
    assert backend.kind is SandboxBackendKind.DOCKER
    assert backend.supports_live_view() is True
    assert backend.supports_egress_sidecar() is True

    session = backend.create_session(
        task_id=7,
        agent_type="coder",
        workspace_path="/ws",
        network_policy="agent-default",
    )
    assert isinstance(session, AgentSandbox)
    assert session.task_id == 7
    assert session.agent_type == "coder"
    assert session.workspace_path == "/ws"


def test_create_session_passes_manager() -> None:
    manager = MagicMock()
    backend = DockerSandboxBackend(manager=manager)
    session = backend.create_session(
        task_id=1,
        agent_type="coder",
        workspace_path="/ws",
    )
    assert session.manager is manager
