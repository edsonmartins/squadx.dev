"""Egress firewall sidecar (RFC-0006 / ADR-0008 Phase 1).

The agent container runs ``cap-drop ALL`` + non-root, so egress cannot be filtered
with iptables *inside* it. Instead a privileged **sidecar** owns the network
namespace and enforces the policy; the agent joins that namespace via
``network_mode=container:<sidecar>`` and — still capability-less — cannot alter the
rules. See RFC-0006 for the full topology and rollout.

This module builds the sidecar container kwargs. Creating/starting/tearing down the
sidecar and applying the policy live on ``DockerManager`` (which owns the client and
already has a generic ``apply_network_setup`` that works here because the sidecar
*does* hold NET_ADMIN).
"""

from __future__ import annotations

from typing import Any

# The one capability the sidecar needs to program iptables in its netns. The agent
# never gets this — that is the whole point of moving enforcement out of it.
SIDECAR_CAP_ADD: tuple[str, ...] = ("NET_ADMIN",)


def sidecar_name(task_id: int, agent_type: str) -> str:
    return f"squadx-egress-{task_id}-{agent_type}"


def build_sidecar_kwargs(
    *,
    name: str,
    image: str,
    published_ports: dict | None = None,
    command: list[str] | None = None,
) -> dict[str, Any]:
    """Build docker ``containers.create`` kwargs for the egress sidecar.

    The sidecar is the netns owner, so it holds the published ports (e.g. VNC) that the
    agent would otherwise expose. It keeps ``no-new-privileges`` and adds only
    NET_ADMIN — it is trusted infrastructure, not agent code.
    """
    kwargs: dict[str, Any] = {
        "image": image,
        "name": name,
        "detach": True,
        "tty": True,
        "network_mode": "bridge",  # the sidecar is the only container with real egress
        "cap_add": list(SIDECAR_CAP_ADD),
        "security_opt": ["no-new-privileges:true"],
        "ports": dict(published_ports or {}),
    }
    if command is not None:
        kwargs["command"] = command
    return kwargs


def agent_netns_mode(sidecar_id: str) -> str:
    """The ``network_mode`` value that makes the agent share the sidecar's netns."""
    return f"container:{sidecar_id}"
