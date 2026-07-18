"""Tests for network policy injection in AgentSandbox.start()."""

import logging
from unittest.mock import AsyncMock, MagicMock, patch

import pytest

from squadx_client.docker.hardening import SandboxRuntime
from squadx_client.docker.manager import DockerManager
from squadx_client.docker.network_policy import POLICY_AGENT_DEFAULT, EgressAction
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


class TestPolicyAlwaysResolves:
    """A policy you have to pass to get is not a default.

    Neither production call site ever passed `network_policy`, so for the whole life
    of the parameter `_network_policy` was None and no policy was applied anywhere.
    It now resolves from settings, so forgetting to pass it cannot disable enforcement.
    """

    def test_unset_policy_falls_back_to_settings(self, manager):
        with patch("squadx_client.docker.sandbox.settings") as s:
            s.network_policy = "deny-all"
            sandbox = _stub_sandbox(manager, network_policy_name=None)
        assert sandbox._network_policy is not None
        assert sandbox._network_policy.default_action == EgressAction.DENY
        assert sandbox._network_policy.rules == []  # deny-all, from settings

    def test_explicit_policy_wins_over_settings(self, manager):
        with patch("squadx_client.docker.sandbox.settings") as s:
            s.network_policy = "deny-all"
            sandbox = _stub_sandbox(manager, network_policy_name="agent-default")
        assert sandbox._network_policy is POLICY_AGENT_DEFAULT

    def test_unknown_policy_degrades_to_agent_default_not_to_nothing(self, manager):
        with patch("squadx_client.docker.sandbox.settings") as s:
            s.network_policy = "agent-default"
            sandbox = _stub_sandbox(manager, network_policy_name="typo-policy")
        assert sandbox._network_policy is POLICY_AGENT_DEFAULT


class TestSandboxStartAppliesPolicy:
    """These drive the real AgentSandbox.start() rather than re-implementing it."""

    def _mock_start_path(self, manager, *, apply_ok=True):
        manager.connect = AsyncMock(return_value=True)
        manager.create_egress_sidecar = AsyncMock(return_value="sc-1")
        manager.create_container = AsyncMock(return_value="agent-1")
        manager.start_container = AsyncMock(return_value=True)
        manager.remove_container = AsyncMock(return_value=True)
        manager.apply_network_setup = AsyncMock(
            return_value=(apply_ok, "" if apply_ok else "iptables: not found")
        )
        manager.warm_pool = None
        return manager

    @pytest.mark.asyncio
    async def test_start_applies_the_policy_on_the_sidecar(self, manager):
        self._mock_start_path(manager)
        with patch("squadx_client.docker.sandbox.settings") as s:
            s.egress_sidecar_enabled = True
            s.egress_fail_open = False
            s.network_policy = "agent-default"
            sandbox = AgentSandbox(
                task_id=42, agent_type="backend", workspace_path="/tmp/ws",
                manager=manager, enable_live_streaming=False, runtime=SandboxRuntime.DOCKER,
            )
            started = await sandbox.start(enable_vnc=False)

        assert started is True
        manager.apply_network_setup.assert_awaited_once()
        target, script = manager.apply_network_setup.await_args.args
        assert target == "sc-1"          # applied on the sidecar, which holds NET_ADMIN
        assert "iptables -P OUTPUT DROP" in script

    @pytest.mark.asyncio
    async def test_policy_is_applied_before_the_agent_is_created(self, manager):
        """The RFC-0006 invariant: the agent must not exist in the netns un-policed."""
        self._mock_start_path(manager)
        order = []
        manager.apply_network_setup = AsyncMock(
            side_effect=lambda *_a: order.append("policy") or (True, "")
        )
        manager.create_container = AsyncMock(
            side_effect=lambda **_kw: order.append("agent_create") or "agent-1"
        )
        with patch("squadx_client.docker.sandbox.settings") as s:
            s.egress_sidecar_enabled = True
            s.egress_fail_open = False
            s.network_policy = "agent-default"
            sandbox = AgentSandbox(
                task_id=42, agent_type="backend", workspace_path="/tmp/ws",
                manager=manager, enable_live_streaming=False, runtime=SandboxRuntime.DOCKER,
            )
            await sandbox.start(enable_vnc=False)

        assert order == ["policy", "agent_create"]

    @pytest.mark.asyncio
    async def test_start_fails_closed_when_policy_cannot_be_applied(self, manager):
        self._mock_start_path(manager, apply_ok=False)
        with patch("squadx_client.docker.sandbox.settings") as s:
            s.egress_sidecar_enabled = True
            s.egress_fail_open = False
            s.network_policy = "agent-default"
            sandbox = AgentSandbox(
                task_id=42, agent_type="backend", workspace_path="/tmp/ws",
                manager=manager, enable_live_streaming=False, runtime=SandboxRuntime.DOCKER,
            )
            started = await sandbox.start(enable_vnc=False)

        assert started is False
        assert sandbox.status == SandboxStatus.ERROR
        manager.create_container.assert_not_awaited()  # agent never came up
        manager.remove_container.assert_awaited()      # sidecar torn down

    @pytest.mark.asyncio
    async def test_fail_open_lets_the_run_proceed_but_is_never_the_default(self, manager):
        self._mock_start_path(manager, apply_ok=False)
        with patch("squadx_client.docker.sandbox.settings") as s:
            s.egress_sidecar_enabled = True
            s.egress_fail_open = True
            s.network_policy = "agent-default"
            sandbox = AgentSandbox(
                task_id=42, agent_type="backend", workspace_path="/tmp/ws",
                manager=manager, enable_live_streaming=False, runtime=SandboxRuntime.DOCKER,
            )
            started = await sandbox.start(enable_vnc=False)
        assert started is True

    @pytest.mark.asyncio
    async def test_disabled_sidecar_reports_egress_as_unenforced(self, manager, caplog):
        """With no sidecar there is nowhere to enforce; that must be loud, not silent."""
        self._mock_start_path(manager)
        with caplog.at_level(logging.ERROR, logger="squadx_client.docker.sandbox"):
            with patch("squadx_client.docker.sandbox.settings") as s:
                s.egress_sidecar_enabled = False
                s.network_policy = "agent-default"
                sandbox = AgentSandbox(
                    task_id=42, agent_type="backend", workspace_path="/tmp/ws",
                    manager=manager, enable_live_streaming=False,
                    runtime=SandboxRuntime.DOCKER,
                )
                await sandbox.start(enable_vnc=False)

        assert any("egress_unenforced" in r.message for r in caplog.records)
        manager.apply_network_setup.assert_not_awaited()  # no dead best-effort apply
