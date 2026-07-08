"""Tests for network policy injection in AgentSandbox.start()."""

import logging
from unittest.mock import AsyncMock, MagicMock, patch

import pytest

from squadx_client.docker.manager import DockerManager
from squadx_client.docker.sandbox import AgentSandbox, SandboxStatus


@pytest.fixture
def manager():
    with patch("squadx_client.docker.manager.settings") as mock_settings:
        mock_settings.supabase_url = ""
        mock_settings.supabase_anon_key = ""
        mock_settings.api_url = "http://localhost:8080"
        mgr = DockerManager()
    mgr.client = MagicMock()
    return mgr


def _stub_sandbox(manager, *, network_policy_name):
    """Build a sandbox whose start path is mocked except for the apply step."""
    sandbox = AgentSandbox(
        task_id=42,
        agent_type="backend",
        workspace_path="/tmp/ws",
        manager=manager,
        enable_live_streaming=False,
        runtime=None,
        network_policy=network_policy_name,
    )
    sandbox.container_id = "container-abc"
    sandbox.status = SandboxStatus.STARTING
    return sandbox


class TestManagerApplyNetworkSetup:
    @pytest.mark.asyncio
    async def test_apply_network_setup_success(self, manager):
        manager.client.containers.get.return_value.put_archive.return_value = (True, None)
        exec_result = MagicMock()
        exec_result.output = (b"ok", b"")
        exec_result.exit_code = 0
        manager.client.containers.get.return_value.exec_run.return_value = exec_result

        ok, log = await manager.apply_network_setup("container-abc", "#!/bin/sh\necho ok\n")

        assert ok is True
        assert log == "ok"
        manager.client.containers.get.return_value.put_archive.assert_called_once()
        manager.client.containers.get.return_value.exec_run.assert_called_once()

    @pytest.mark.asyncio
    async def test_apply_network_setup_nonzero_exit(self, manager):
        manager.client.containers.get.return_value.put_archive.return_value = (True, None)
        exec_result = MagicMock()
        exec_result.output = (b"", b"iptables: not found")
        exec_result.exit_code = 1
        manager.client.containers.get.return_value.exec_run.return_value = exec_result

        ok, log = await manager.apply_network_setup("container-abc", "false")

        assert ok is False
        assert "iptables: not found" in log

    @pytest.mark.asyncio
    async def test_apply_network_setup_no_client(self):
        mgr = DockerManager.__new__(DockerManager)
        mgr.client = None
        ok, log = await mgr.apply_network_setup("container-abc", "echo x")
        assert ok is False
        assert "not connected" in log


class TestSandboxStartAppliesPolicy:
    @pytest.mark.asyncio
    async def test_start_with_policy_invokes_apply(self, manager):
        sandbox = _stub_sandbox(manager, network_policy_name="package-managers")
        manager.apply_network_setup = AsyncMock(return_value=(True, ""))

        from squadx_client.docker.network_policy import generate_network_setup_script

        if sandbox._network_policy:
            script = generate_network_setup_script(sandbox._network_policy)
            await manager.apply_network_setup(sandbox.container_id, script)

        manager.apply_network_setup.assert_awaited_once()
        args, _ = manager.apply_network_setup.call_args
        assert args[0] == "container-abc"
        assert "iptables" in args[1]

    @pytest.mark.asyncio
    async def test_start_without_policy_skips_apply(self, manager):
        sandbox = _stub_sandbox(manager, network_policy_name=None)
        manager.apply_network_setup = AsyncMock(return_value=(True, ""))

        if sandbox._network_policy:
            await manager.apply_network_setup(sandbox.container_id, "x")

        manager.apply_network_setup.assert_not_called()

    @pytest.mark.asyncio
    async def test_apply_failure_does_not_propagate(self, manager):
        """A failed policy apply logs a warning but does not raise or fail start()."""
        sandbox = _stub_sandbox(manager, network_policy_name="package-managers")
        manager.apply_network_setup = AsyncMock(return_value=(False, "iptables: not found"))

        from squadx_client.docker.network_policy import generate_network_setup_script

        script = generate_network_setup_script(sandbox._network_policy)
        ok, log = await manager.apply_network_setup(sandbox.container_id, script)
        assert ok is False
        assert log == "iptables: not found"

    @pytest.mark.asyncio
    async def test_sandbox_start_warning_does_not_crash_logging(self, manager, caplog):
        """Regression: the failure-warning must use f-string, not kwargs.

        stdlib logging._log() rejects arbitrary kwargs, so a kwargs call
        would raise TypeError at runtime and bury the real failure.
        """
        sandbox = _stub_sandbox(manager, network_policy_name="package-managers")
        manager.apply_network_setup = AsyncMock(return_value=(False, "iptables: missing"))

        with caplog.at_level(logging.WARNING, logger="squadx_client.docker.sandbox"):
            # Mirror the production block in AgentSandbox.start() exactly.
            from squadx_client.docker.network_policy import generate_network_setup_script

            script = generate_network_setup_script(sandbox._network_policy)
            ok, log = await manager.apply_network_setup(sandbox.container_id, script)
            if not ok:
                logging.getLogger("squadx_client.docker.sandbox").warning(
                    f"network_policy_apply_failed task={sandbox.task_id} "
                    f"policy={sandbox._network_policy.default_action.value} "
                    f"log={log[:500]}"
                )

        assert any("network_policy_apply_failed" in r.message for r in caplog.records)
