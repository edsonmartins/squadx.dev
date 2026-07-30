"""Unit tests for DockerSandboxBackend + DockerSandboxSession adapter."""

from __future__ import annotations

from unittest.mock import AsyncMock, MagicMock, patch

import pytest

from squadx_client.docker.sandbox import AgentSandbox
from squadx_client.sandbox.docker_backend import DockerSandboxBackend
from squadx_client.sandbox.docker_session import DockerSandboxSession
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
    assert isinstance(session, DockerSandboxSession)
    assert isinstance(session, SandboxSession)
    assert isinstance(session.inner, AgentSandbox)
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
    assert session.inner.manager is manager


@pytest.mark.asyncio
async def test_start_applies_settings_defaults() -> None:
    inner = MagicMock(spec=AgentSandbox)
    inner.start = AsyncMock(return_value=True)
    session = DockerSandboxSession(inner)

    with (
        patch("squadx_client.sandbox.docker_session.settings") as mock_settings,
    ):
        mock_settings.agent_image = "squadx/agent:test"
        mock_settings.agent_memory_limit = "1g"
        mock_settings.agent_cpu_limit = 1.5
        mock_settings.enable_vnc = False

        ok = await session.start(exec_env={"K": "v"})
        assert ok is True
        inner.start.assert_awaited_once_with(
            image="squadx/agent:test",
            memory_limit="1g",
            cpu_limit=1.5,
            enable_vnc=False,
            environment=None,
            exec_env={"K": "v"},
        )


@pytest.mark.asyncio
async def test_start_explicit_kwargs_override_settings() -> None:
    inner = MagicMock(spec=AgentSandbox)
    inner.start = AsyncMock(return_value=True)
    session = DockerSandboxSession(inner)

    await session.start(
        image="custom:latest",
        memory_limit="4g",
        cpu_limit=3.0,
        enable_vnc=True,
    )
    inner.start.assert_awaited_once()
    kwargs = inner.start.await_args.kwargs
    assert kwargs["image"] == "custom:latest"
    assert kwargs["memory_limit"] == "4g"
    assert kwargs["cpu_limit"] == 3.0
    assert kwargs["enable_vnc"] is True


@pytest.mark.asyncio
async def test_execute_and_getattr_delegate() -> None:
    inner = MagicMock(spec=AgentSandbox)
    inner.execute = AsyncMock(return_value=MagicMock(success=True, output="hi"))
    inner.container_id = "cid123"
    inner.live_join_code = "join"
    session = DockerSandboxSession(inner)

    result = await session.execute(["echo", "hi"])
    assert result.success is True
    assert session.container_id == "cid123"
    assert session.live_join_code == "join"
