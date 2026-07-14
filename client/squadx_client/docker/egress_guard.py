"""Host-side cloud-metadata egress block (ADR-0008, Phase 0).

The sandbox runs untrusted agent code with ``cap-drop ALL`` + non-root, so egress
policy **cannot** be enforced with ``iptables`` *inside* the container (NET_ADMIN
cannot be regained via ``exec`` — see ``apply_network_setup`` in ``manager.py``,
which is why the in-container path is dead). The minimum-viable enforcement that
closes the worst vector (SSRF -> cloud credentials via the instance metadata
service) is a **host-side** rule on Docker's ``DOCKER-USER`` chain, which the
container cannot touch.

This module applies that rule once, idempotently, at daemon start. It is a
best-effort host operation: if it cannot run (no privilege, no iptables, non-Linux
dev host, or a *remote* Docker daemon), it **loudly** reports that metadata egress
is NOT blocked rather than failing silently — an unenforced block must never read
as an enforced one.

The durable production path is to provision this rule via infra (systemd unit /
DaemonSet) so it survives host reboots; this runtime application is the safety net.
Later phases (firewall sidecar, gVisor) supersede it — see ADR-0008.
"""

from __future__ import annotations

import logging
import shutil
import subprocess
from collections.abc import Callable
from dataclasses import dataclass, field

logger = logging.getLogger(__name__)

# Link-local instance metadata endpoints, across clouds:
#   169.254.169.254 — AWS/Azure/GCP IMDS (metadata.google.internal resolves here too)
#   169.254.170.2   — AWS ECS task metadata / container credentials endpoint
CLOUD_METADATA_TARGETS: tuple[str, ...] = ("169.254.169.254", "169.254.170.2")

# Docker routes bridge egress through this chain; a rule here applies to every
# container without touching the (untrusted) container's own netns.
_DOCKER_USER_CHAIN = "DOCKER-USER"

# A command runner: takes argv, returns (exit_code, combined_output). Injectable
# for tests. Never raises — turns OS errors into a non-zero exit + message.
Runner = Callable[[list[str]], "tuple[int, str]"]


def _default_runner(argv: list[str]) -> tuple[int, str]:
    try:
        proc = subprocess.run(  # noqa: S603 - fixed argv, no shell
            argv,
            capture_output=True,
            text=True,
            timeout=10,
        )
        return proc.returncode, (proc.stdout or "") + (proc.stderr or "")
    except FileNotFoundError as e:
        return 127, str(e)
    except (OSError, subprocess.SubprocessError) as e:  # timeout, perms, etc.
        return 1, str(e)


@dataclass
class MetadataBlockResult:
    """Outcome of attempting the host-side metadata block.

    ``enforced`` is True only if every target now has a DROP rule (already present
    or freshly inserted). Any other outcome means egress to metadata is NOT blocked.
    """

    enforced: bool
    blocked_targets: list[str] = field(default_factory=list)
    detail: str = ""


def ensure_cloud_metadata_blocked(
    targets: tuple[str, ...] = CLOUD_METADATA_TARGETS,
    runner: Runner = _default_runner,
    iptables_bin: str | None = None,
) -> MetadataBlockResult:
    """Ensure host-side DROP rules for cloud metadata endpoints (idempotent).

    For each target: check the ``DOCKER-USER`` chain (``iptables -C``); insert the
    DROP rule only if absent (``iptables -I``). Returns a result and NEVER raises,
    so the caller can start the daemon in a degraded (but loud) state on a host
    without privilege/iptables. See module docstring.
    """
    iptables = iptables_bin or shutil.which("iptables")
    if not iptables:
        detail = (
            "iptables not found on host — cloud metadata egress is NOT blocked "
            "(SSRF->credentials vector open). Provision the DOCKER-USER rule via "
            "infra, or run on a Linux Docker host. See ADR-0008."
        )
        logger.error("metadata_block_unenforced: %s", detail)
        return MetadataBlockResult(enforced=False, detail=detail)

    blocked: list[str] = []
    for target in targets:
        rule = ["-d", target, "-j", "DROP"]
        check_code, _ = runner([iptables, "-C", _DOCKER_USER_CHAIN, *rule])
        if check_code == 0:
            blocked.append(target)  # already present
            continue
        # Insert at the head so it wins over any later ACCEPT in the chain.
        insert_code, insert_out = runner(
            [iptables, "-I", _DOCKER_USER_CHAIN, "1", *rule]
        )
        if insert_code == 0:
            logger.info("metadata_block_applied target=%s", target)
            blocked.append(target)
        else:
            detail = (
                f"failed to insert DROP for {target} into {_DOCKER_USER_CHAIN} "
                f"(exit={insert_code}): {insert_out.strip()[:300]}. Cloud metadata "
                "egress is NOT blocked — likely missing root/CAP_NET_ADMIN on the "
                "host or a remote Docker daemon. See ADR-0008."
            )
            logger.error("metadata_block_unenforced: %s", detail)
            return MetadataBlockResult(
                enforced=False, blocked_targets=blocked, detail=detail
            )

    logger.info("metadata_block_enforced targets=%s", ",".join(blocked))
    return MetadataBlockResult(
        enforced=True,
        blocked_targets=blocked,
        detail="cloud metadata egress blocked on DOCKER-USER",
    )
