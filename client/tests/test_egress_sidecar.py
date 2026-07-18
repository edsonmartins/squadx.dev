"""Tests for the RFC-0006 egress sidecar (Phase 1 of ADR-0008).

These are unit tests against the pure kwargs builder and the DockerManager wiring
(mock client). The real packet-drop behaviour requires a Linux Docker host and is
covered by the docker-marked integration test below (skipped by default).
"""

import os
from unittest.mock import AsyncMock, MagicMock, patch

import pytest
from docker.errors import NotFound

from squadx_client.docker.egress_sidecar import (
    SIDECAR_CAP_ADD,
    agent_netns_mode,
    build_sidecar_kwargs,
    sidecar_name,
)
from squadx_client.docker.hardening import SandboxRuntime
from squadx_client.docker.manager import ContainerConfig, DockerManager
from squadx_client.docker.sandbox import AgentSandbox
from squadx_client.docker.network_policy import (
    POLICY_AGENT_DEFAULT,
    EgressAction,
    get_predefined_policy,
)

# ── pure builder ────────────────────────────────────────────────────────────────

def test_build_sidecar_kwargs_has_net_admin_and_no_new_privs():
    kwargs = build_sidecar_kwargs(
        name="squadx-egress-1-backend",
        image="squadx/egress-proxy:latest",
        published_ports={"5900/tcp": None},
    )
    assert kwargs["cap_add"] == list(SIDECAR_CAP_ADD) == ["NET_ADMIN"]
    assert "no-new-privileges:true" in kwargs["security_opt"]
    assert kwargs["network_mode"] == "bridge"
    assert kwargs["ports"] == {"5900/tcp": None}
    assert kwargs["image"] == "squadx/egress-proxy:latest"


def test_agent_netns_mode_targets_the_sidecar():
    assert agent_netns_mode("sc123") == "container:sc123"


def test_sidecar_name_is_deterministic():
    assert sidecar_name(7, "frontend") == "squadx-egress-7-frontend"


# ── policy preset ────────────────────────────────────────────────────────────────

def test_agent_default_policy_is_deny_with_llm_allowlist_and_metadata_block():
    assert POLICY_AGENT_DEFAULT.default_action == EgressAction.DENY
    targets = {r.target: r.action for r in POLICY_AGENT_DEFAULT.rules}
    assert targets["api.anthropic.com"] == EgressAction.ALLOW
    assert targets["api.openai.com"] == EgressAction.ALLOW
    assert targets["169.254.169.254"] == EgressAction.DENY  # metadata blocked
    assert targets["169.254.170.2"] == EgressAction.DENY


def test_get_predefined_policy_names():
    assert get_predefined_policy("agent-default") is POLICY_AGENT_DEFAULT
    assert get_predefined_policy("deny-all").default_action == EgressAction.DENY
    # legacy aliases still resolve
    assert get_predefined_policy("none") is not None
    with pytest.raises(ValueError):
        get_predefined_policy("bogus")


# ── DockerManager wiring (mock client) ───────────────────────────────────────────

@pytest.fixture
def connected_manager():
    with patch("squadx_client.docker.manager.settings") as mock_settings:
        mock_settings.supabase_url = ""
        mock_settings.supabase_anon_key = ""
        mock_settings.api_url = "http://localhost:8080"
        mock_settings.egress_sidecar_image = "squadx/egress-proxy:latest"
        mgr = DockerManager()
    mgr.client = MagicMock()
    return mgr


@pytest.mark.asyncio
async def test_create_egress_sidecar_creates_and_starts(connected_manager):
    sc = MagicMock()
    sc.id = "sc-1"
    sc.short_id = "sc-1"
    connected_manager.client.containers.get.side_effect = NotFound("nope")
    connected_manager.client.containers.create.return_value = sc

    result = await connected_manager.create_egress_sidecar(
        task_id=1, agent_type="backend", published_ports={"5900/tcp": None}
    )

    assert result == "sc-1"
    sc.start.assert_called_once()
    kwargs = connected_manager.client.containers.create.call_args.kwargs
    assert kwargs["cap_add"] == ["NET_ADMIN"]
    assert kwargs["ports"] == {"5900/tcp": None}


@pytest.mark.asyncio
async def test_create_container_joins_sidecar_netns(connected_manager):
    agent = MagicMock()
    agent.id = "agent-1"
    agent.short_id = "agent-1"
    connected_manager.client.containers.get.side_effect = NotFound("nope")
    connected_manager.client.containers.create.return_value = agent

    config = ContainerConfig(enable_hardening=False, enable_vnc=True)
    result = await connected_manager.create_container(
        config, task_id=1, agent_type="backend", netns_container="sc-1"
    )

    assert result == "agent-1"
    kwargs = connected_manager.client.containers.create.call_args.kwargs
    assert kwargs["network_mode"] == "container:sc-1"
    assert kwargs["ports"] == {}          # no port publishing when sharing a netns
    assert "network" not in kwargs        # cannot set a network alongside container-mode


@pytest.mark.asyncio
async def test_create_container_without_netns_publishes_vnc(connected_manager):
    agent = MagicMock()
    agent.id = "agent-2"
    agent.short_id = "agent-2"
    connected_manager.client.containers.get.side_effect = NotFound("nope")
    connected_manager.client.containers.create.return_value = agent

    config = ContainerConfig(enable_hardening=False, enable_vnc=True, vnc_port=5900)
    await connected_manager.create_container(config, task_id=1, agent_type="backend")

    kwargs = connected_manager.client.containers.create.call_args.kwargs
    assert "5900/tcp" in kwargs["ports"]
    assert "container:" not in str(kwargs.get("network_mode", ""))


# ── live-view under a shared netns (RFC-0006 §6) ─────────────────────────────────

@pytest.mark.asyncio
async def test_live_stream_uses_the_port_published_on_the_sidecar():
    """Under RFC-0006 the agent has no published ports — the sidecar owns the netns
    and publishes VNC. The stream must be started against the sidecar's port while
    still being keyed by the agent container (what stop/cleanup tear down).

    Regression: passing only the agent's container_id made the manager re-resolve the
    port against a container with none, silently disabling live view whenever egress
    enforcement was on.
    """
    manager = MagicMock()
    manager.client = MagicMock()
    manager.connect = AsyncMock(return_value=True)
    manager.create_egress_sidecar = AsyncMock(return_value="sc-1")
    manager.create_container = AsyncMock(return_value="agent-1")
    manager.start_container = AsyncMock(return_value=True)
    manager.apply_network_setup = AsyncMock(return_value=(True, ""))
    manager.start_live_stream = AsyncMock(return_value="JOIN42")
    # The port is published on the sidecar; the agent container has none.
    manager.get_vnc_port = AsyncMock(
        side_effect=lambda cid: 49999 if cid == "sc-1" else None
    )
    manager.warm_pool = None

    with patch("squadx_client.docker.sandbox.settings") as s:
        s.egress_sidecar_enabled = True
        s.egress_fail_open = False
        s.network_policy = "agent-default"

        sandbox = AgentSandbox(
            task_id=42,
            agent_type="backend",
            workspace_path="/tmp/ws",
            manager=manager,
            enable_live_streaming=True,
            runtime=SandboxRuntime.DOCKER,
        )
        with patch("squadx_client.docker.sandbox.asyncio.sleep", new=AsyncMock()):
            started = await sandbox.start(enable_vnc=True)

    assert started is True
    assert sandbox.sidecar_id == "sc-1"
    assert sandbox.vnc_port == 49999
    manager.start_live_stream.assert_awaited_once()
    kwargs = manager.start_live_stream.await_args.kwargs
    assert kwargs["vnc_port"] == 49999          # the sidecar's port, not None
    assert kwargs["container_id"] == "agent-1"  # still keyed by the agent
    assert sandbox.live_join_code == "JOIN42"


@pytest.mark.asyncio
async def test_start_live_stream_resolves_port_itself_when_not_given():
    """Non-sidecar path is unchanged: the manager still resolves the agent's port."""
    with patch("squadx_client.docker.manager.settings") as mock_settings:
        mock_settings.supabase_url = "https://x.supabase.co"
        mock_settings.supabase_anon_key = "anon"
        mgr = DockerManager()
    mgr.client = MagicMock()
    mgr.get_vnc_port = AsyncMock(return_value=32768)

    with patch(
        "squadx_client.streaming.webrtc_bridge.create_live_stream",
        new=AsyncMock(return_value=(MagicMock(session_id="s1"), "JOIN01")),
    ):
        join = await mgr.start_live_stream(container_id="agent-9", task_id=1)

    assert join == "JOIN01"
    mgr.get_vnc_port.assert_awaited_once_with("agent-9")


# ── integration (real Docker on Linux) — skipped unless explicitly enabled ────────

@pytest.mark.integration
@pytest.mark.skipif(
    os.environ.get("SQUADX_DOCKER_IT") != "1",
    reason="requires a Docker host + `make build-egress-proxy`; set SQUADX_DOCKER_IT=1 to run",
)
@pytest.mark.asyncio
async def test_integration_agent_egress_is_filtered_by_sidecar():
    """Executable spec for the real path (RFC-0006 §7).

    Everything above this line is mocks: they prove we *ask* Docker for the right
    topology, not that packets actually drop. This is the only test that proves the
    firewall works, so it asserts the four claims the design rests on:

      - an allowlisted host is reachable
      - a non-allowlisted host is not (default-deny)
      - cloud metadata is not (SSRF -> credentials)
      - the agent cannot undo any of it (no NET_ADMIN)

    Run: make build-egress-proxy && SQUADX_DOCKER_IT=1 pytest -m integration client/tests
    """
    from squadx_client.docker.manager import DockerManager
    from squadx_client.docker.sandbox import AgentSandbox

    manager = DockerManager()
    assert await manager.connect(), "Docker daemon unreachable"

    sandbox = AgentSandbox(
        task_id=99_999,
        agent_type="backend",
        workspace_path="/tmp",
        manager=manager,
        enable_live_streaming=False,
        network_policy="agent-default",
    )
    with patch("squadx_client.docker.sandbox.settings") as s:
        s.egress_sidecar_enabled = True
        s.egress_fail_open = False
        s.network_policy = "agent-default"
        s.agent_image = os.environ.get("SQUADX_AGENT_IMAGE", "squadx/agent:latest")
        assert await sandbox.start(enable_vnc=False), "sandbox failed to start"

    try:
        # Allowlisted: reachable. -sS surfaces the error if this regresses.
        allowed = await sandbox.execute(
            ["curl", "-sS", "-o", "/dev/null", "-w", "%{http_code}",
             "--max-time", "20", "https://api.anthropic.com"],
            timeout=30,
        )
        assert allowed.exit_code == 0, f"allowlisted host unreachable: {allowed.output}"

        # Not allowlisted: must fail. curl exits non-zero on connect failure/timeout.
        denied = await sandbox.execute(
            ["curl", "-sS", "--max-time", "10", "https://example.com"], timeout=30
        )
        assert denied.exit_code != 0, "default-deny did not block a non-allowlisted host"

        # Cloud metadata: must fail even though it is link-local, not egress proper.
        metadata = await sandbox.execute(
            ["curl", "-sS", "--max-time", "5", "http://169.254.169.254/latest/meta-data/"],
            timeout=20,
        )
        assert metadata.exit_code != 0, "cloud metadata endpoint was reachable"

        # The agent must not be able to take the firewall down. cap-drop ALL means
        # NET_ADMIN cannot be regained, so this is expected to fail.
        tamper = await sandbox.execute(["iptables", "-F", "OUTPUT"], timeout=20)
        assert tamper.exit_code != 0, "agent was able to flush the egress rules"

        # And the policy must still hold afterwards.
        still_denied = await sandbox.execute(
            ["curl", "-sS", "--max-time", "10", "https://example.com"], timeout=30
        )
        assert still_denied.exit_code != 0, "egress opened up after tamper attempt"
    finally:
        await sandbox.cleanup()
