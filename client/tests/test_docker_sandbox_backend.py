"""Unit tests for DockerSandboxBackend (ADR-0009 Phase 2)."""

from __future__ import annotations

from unittest.mock import AsyncMock, MagicMock

import pytest

from squadx_client.docker.sandbox import SandboxResult, SandboxStatus
from squadx_client.sandbox.docker_backend import DockerSandboxBackend
from squadx_client.sandbox.errors import SandboxStartError
from squadx_client.sandbox.types import SandboxBackendKind, SandboxLifecycleStatus


@pytest.fixture
def backend() -> DockerSandboxBackend:
    return DockerSandboxBackend()


@pytest.mark.asyncio
async def test_start_registers_handle_and_maps_status(backend: DockerSandboxBackend) -> None:
    session = MagicMock()
    session.task_id = 7
    session.agent_type = "coder"
    session.workspace_path = "/ws"
    session.container_id = "cid1234567890"
    session.sidecar_id = "sid"
    session.vnc_port = 5901
    session.live_join_code = "join-me"
    session.status = SandboxStatus.RUNNING
    session.start = AsyncMock(return_value=True)

    backend.create_session = MagicMock(return_value=session)  # type: ignore[method-assign]

    handle = await backend.start(
        task_id=7,
        agent_type="coder",
        workspace_path="/ws",
        enable_live=True,
        exec_env={"OPENAI_API_KEY": "x"},
    )

    assert handle.backend is SandboxBackendKind.DOCKER
    assert handle.id == "cid1234567890"
    assert handle.live_join_code == "join-me"
    assert handle.vnc_port == 5901
    assert backend.get_session(handle) is session
    session.start.assert_awaited_once()


@pytest.mark.asyncio
async def test_start_failure_raises(backend: DockerSandboxBackend) -> None:
    session = MagicMock()
    session.start = AsyncMock(return_value=False)
    backend.create_session = MagicMock(return_value=session)  # type: ignore[method-assign]

    with pytest.raises(SandboxStartError, match="failed to start"):
        await backend.start(task_id=1, agent_type="coder", workspace_path="/ws")


@pytest.mark.asyncio
async def test_exec_and_cleanup(backend: DockerSandboxBackend) -> None:
    session = MagicMock()
    session.task_id = 1
    session.agent_type = "coder"
    session.workspace_path = "/ws"
    session.container_id = "abc"
    session.sidecar_id = None
    session.vnc_port = None
    session.live_join_code = None
    session.status = SandboxStatus.RUNNING
    session._exec_env = {}
    session.execute = AsyncMock(
        return_value=SandboxResult(success=True, exit_code=0, output="hi\n", duration_seconds=0.1)
    )
    session.cleanup = AsyncMock(return_value=True)
    session.get_metrics = MagicMock(return_value=None)

    handle = backend.register_session(session)
    result = await backend.exec(handle, ["echo", "hi"], timeout=10)
    assert result.success is True
    assert result.output == "hi\n"

    assert await backend.cleanup(handle) is True
    session.cleanup.assert_awaited_once()
    with pytest.raises(Exception, match="unknown sandbox handle"):
        backend.get_session(handle)


@pytest.mark.asyncio
async def test_status_maps_enum(backend: DockerSandboxBackend) -> None:
    session = MagicMock()
    session.task_id = 1
    session.agent_type = "coder"
    session.workspace_path = "/ws"
    session.container_id = "c1"
    session.sidecar_id = None
    session.vnc_port = None
    session.live_join_code = None
    session.status = SandboxStatus.STOPPED
    handle = backend.register_session(session)
    assert await backend.status(handle) is SandboxLifecycleStatus.STOPPED
