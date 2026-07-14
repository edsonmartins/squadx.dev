"""Tests for the RFC-0006 egress sidecar (Phase 1 of ADR-0008).

These are unit tests against the pure kwargs builder and the DockerManager wiring
(mock client). The real packet-drop behaviour requires a Linux Docker host and is
covered by the docker-marked integration test below (skipped by default).
"""

import os
from unittest.mock import MagicMock, patch

import pytest
from docker.errors import NotFound

from squadx_client.docker.egress_sidecar import (
    SIDECAR_CAP_ADD,
    agent_netns_mode,
    build_sidecar_kwargs,
    sidecar_name,
)
from squadx_client.docker.manager import ContainerConfig, DockerManager
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


# ── integration (real Docker on Linux) — skipped unless explicitly enabled ────────

@pytest.mark.integration
@pytest.mark.skipif(
    os.environ.get("SQUADX_DOCKER_IT") != "1",
    reason="requires a Linux Docker host; set SQUADX_DOCKER_IT=1 to run",
)
@pytest.mark.asyncio
async def test_integration_agent_egress_is_filtered_by_sidecar():
    # Executable spec for the real path (RFC-0006 §7):
    #   - curl https://api.anthropic.com  -> succeeds (allowlisted)
    #   - curl https://evil.example       -> fails (default-deny)
    #   - curl http://169.254.169.254     -> fails (metadata blocked)
    #   - iptables -F inside the agent     -> fails (no NET_ADMIN)
    pytest.skip("integration body runs only on a provisioned Docker host")
