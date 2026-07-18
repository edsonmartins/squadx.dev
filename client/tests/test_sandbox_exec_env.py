"""Provider secrets must ride the exec, not the container.

Container-create env is baked in for the container's whole life (readable via
`docker inspect` and /proc/1/environ) and is fixed at create time. That second
property is what made the warm pool and the External-CLI path mutually exclusive:
a pool container is created before any task exists, so it could never carry that
task's credentials. Injecting per-exec fixes both.
"""

from unittest.mock import AsyncMock, MagicMock, patch

import pytest

from squadx_client.docker.hardening import SandboxRuntime
from squadx_client.docker.manager import DockerManager
from squadx_client.docker.sandbox import AgentSandbox, SandboxStatus

SECRETS = {"ANTHROPIC_API_KEY": "sk-ant-secret"}


def _running_sandbox(manager, exec_env=None):
    sandbox = AgentSandbox(
        task_id=1,
        agent_type="external_cli",
        workspace_path="/tmp/ws",
        manager=manager,
        enable_live_streaming=False,
        runtime=SandboxRuntime.DOCKER,
    )
    sandbox.container_id = "agent-1"
    sandbox.status = SandboxStatus.RUNNING
    sandbox._exec_env = dict(exec_env or {})
    return sandbox


@pytest.mark.asyncio
async def test_execute_passes_exec_env_to_the_exec():
    manager = MagicMock()
    manager.exec_command = AsyncMock(return_value=(0, "ok"))
    sandbox = _running_sandbox(manager, SECRETS)

    await sandbox.execute(["claude", "-p", "hi"])

    assert manager.exec_command.await_args.kwargs["environment"] == SECRETS


@pytest.mark.asyncio
async def test_execute_streaming_passes_exec_env_to_the_exec():
    manager = MagicMock()

    async def _stream(*_a, **_kw):
        yield ("stdout", "done")
        yield ("exit", 0)

    manager.exec_command_stream = MagicMock(side_effect=_stream)
    sandbox = _running_sandbox(manager, SECRETS)

    result = await sandbox.execute_streaming(["claude", "-p", "hi"])

    assert result.success is True
    assert manager.exec_command_stream.call_args.kwargs["environment"] == SECRETS


@pytest.mark.asyncio
async def test_no_exec_env_sends_none_not_empty_dict():
    """Empty dict would be a no-op but None keeps the Docker call identical to before."""
    manager = MagicMock()
    manager.exec_command = AsyncMock(return_value=(0, "ok"))
    sandbox = _running_sandbox(manager, exec_env={})

    await sandbox.execute(["ls"])

    assert manager.exec_command.await_args.kwargs["environment"] is None


@pytest.mark.asyncio
async def test_start_routes_exec_env_away_from_container_create():
    """The regression that matters: secrets must not appear in create-time env."""
    manager = MagicMock()
    manager.client = MagicMock()
    manager.connect = AsyncMock(return_value=True)
    manager.create_container = AsyncMock(return_value="agent-1")
    manager.start_container = AsyncMock(return_value=True)
    manager.warm_pool = None

    with patch("squadx_client.docker.sandbox.settings") as s:
        s.egress_sidecar_enabled = False
        sandbox = AgentSandbox(
            task_id=1,
            agent_type="external_cli",
            workspace_path="/tmp/ws",
            manager=manager,
            enable_live_streaming=False,
            runtime=SandboxRuntime.DOCKER,
        )
        await sandbox.start(enable_vnc=False, exec_env=SECRETS)

    created_config = manager.create_container.await_args.kwargs["config"]
    assert "ANTHROPIC_API_KEY" not in created_config.environment
    assert sandbox._exec_env == SECRETS


@pytest.mark.asyncio
async def test_manager_exec_command_forwards_environment_to_docker():
    with patch("squadx_client.docker.manager.settings") as s:
        s.supabase_url = ""
        s.supabase_anon_key = ""
        mgr = DockerManager()
    mgr.client = MagicMock()
    container = MagicMock()
    container.exec_run.return_value = MagicMock(exit_code=0, output=(b"ok", None))
    mgr.client.containers.get.return_value = container

    await mgr.exec_command("c1", ["echo", "hi"], environment=SECRETS)

    assert container.exec_run.call_args.kwargs["environment"] == SECRETS
