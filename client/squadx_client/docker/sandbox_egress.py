"""RFC-0006 egress sidecar policy apply / teardown helpers for AgentSandbox."""

from __future__ import annotations

import logging
from typing import TYPE_CHECKING

from squadx_client.docker.network_policy import (
    EgressSidecarConfig,
    NetworkPolicy,
    generate_sidecar_setup_script,
)

if TYPE_CHECKING:
    from squadx_client.docker.manager import DockerManager

logger = logging.getLogger(__name__)


async def apply_sidecar_policy(
    *,
    manager: DockerManager,
    sidecar_id: str,
    policy: NetworkPolicy,
    task_id: int,
    egress_image: str | None = None,
    fail_open: bool | None = None,
) -> bool:
    """Apply egress policy on the sidecar. False only when apply failed and fail-open is off.

    ``egress_image`` / ``fail_open`` default from settings when omitted so tests that
    patch ``squadx_client.docker.sandbox.settings`` keep working via the AgentSandbox
    wrapper, which passes them through.
    """
    from squadx_client.config import settings

    image = (
        egress_image
        if egress_image is not None
        else getattr(settings, "egress_sidecar_image", "squadx/egress-proxy:latest")
    )
    open_on_fail = (
        fail_open
        if fail_open is not None
        else bool(getattr(settings, "egress_fail_open", False))
    )
    config = EgressSidecarConfig(image=image, policy=policy)
    script = generate_sidecar_setup_script(config)
    ok, log = await manager.apply_network_setup(sidecar_id, script)
    if ok:
        return True
    if open_on_fail:
        logger.warning(
            f"egress_policy_apply_failed_fail_open task={task_id} log={log[:500]}"
        )
        return True
    logger.error(
        f"egress_policy_apply_failed_fail_closed task={task_id} log={log[:500]}"
    )
    return False


async def teardown_sidecar(
    *,
    manager: DockerManager,
    sidecar_id: str | None,
    task_id: int,
) -> None:
    """Best-effort removal of the egress sidecar; never raises."""
    if not sidecar_id:
        return
    try:
        await manager.remove_container(sidecar_id, force=True)
    except Exception as e:  # noqa: BLE001 - best effort
        logger.warning(f"egress_sidecar_teardown_failed task={task_id} error={e}")
