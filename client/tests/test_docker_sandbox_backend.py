"""Unit tests for DockerSandboxBackend + AgentSandbox session."""

from __future__ import annotations

from unittest.mock import AsyncMock, MagicMock, patch

import pytest

from squadx_client.docker.sandbox import AgentSandbox
from squadx_client.sandbox.docker_backend import DockerSandboxBackend
from squadx_client.sandbox.session import SandboxSession
from squadx_client.sandbox.types import SandboxBackendKind


def test_create_session_returns_docker_session() -> None:
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
    assert isinstance(session, SandboxSession)
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


@pytest.mark.asyncio
async def test_start_applies_settings_defaults() -> None:
    manager = MagicMock()
    manager.client = None
    manager.connect = AsyncMock(return_value=False)
    session = AgentSandbox(1, "coder", "/ws", manager=manager)

    with (
        patch("squadx_client.docker.sandbox.settings") as mock_settings,
    ):
        mock_settings.agent_image = "squadx/agent:test"
        mock_settings.agent_memory_limit = "1g"
        mock_settings.agent_cpu_limit = 1.5
        mock_settings.enable_vnc = False

        with patch(
            "squadx_client.docker.sandbox.start_agent_sandbox",
            new=AsyncMock(return_value=True),
        ) as start:
            ok = await session.start(exec_env={"K": "v"})
        assert ok is True
        kwargs = start.await_args.kwargs
        assert kwargs["image"] == "squadx/agent:test"
        assert kwargs["memory_limit"] == "1g"
        assert kwargs["cpu_limit"] == 1.5
        assert kwargs["enable_vnc"] is False
        assert kwargs["exec_env"] == {"K": "v"}


@pytest.mark.asyncio
async def test_start_explicit_kwargs_override_settings() -> None:
    session = AgentSandbox(1, "coder", "/ws", manager=MagicMock())

    with patch(
        "squadx_client.docker.sandbox.start_agent_sandbox",
        new=AsyncMock(return_value=True),
    ) as start:
        await session.start(
            image="custom:latest",
            memory_limit="4g",
            cpu_limit=3.0,
            enable_vnc=True,
        )
    kwargs = start.await_args.kwargs
    assert kwargs["image"] == "custom:latest"
    assert kwargs["memory_limit"] == "4g"
    assert kwargs["cpu_limit"] == 3.0
    assert kwargs["enable_vnc"] is True


@pytest.mark.asyncio
async def test_session_exposes_docker_state_directly() -> None:
    session = AgentSandbox(1, "coder", "/ws", manager=MagicMock())
    session.container_id = "cid123"
    session.live_join_code = "join"
    assert session.container_id == "cid123"
    assert session.live_join_code == "join"
