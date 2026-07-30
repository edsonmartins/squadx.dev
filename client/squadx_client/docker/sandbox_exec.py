"""Command execution helpers for AgentSandbox (non-streaming + streaming)."""

from __future__ import annotations

import asyncio
import logging
import time
from collections.abc import Callable
from typing import TYPE_CHECKING, Any

from squadx_client.docker.sandbox_types import SandboxResult, SandboxStatus

if TYPE_CHECKING:
    from squadx_client.docker.manager import DockerManager

logger = logging.getLogger(__name__)


async def exec_command(
    *,
    manager: DockerManager,
    container_id: str | None,
    status: SandboxStatus,
    command: list[str],
    workdir: str = "/workspace",
    timeout: float = 300,
    exec_env: dict[str, str] | None = None,
    on_output: Callable[[str], Any] | None = None,
) -> SandboxResult:
    """Run a command to completion inside the agent container."""
    if status != SandboxStatus.RUNNING:
        return SandboxResult(
            success=False,
            exit_code=-1,
            output="",
            error=f"Sandbox not running (status: {status})",
        )
    if not container_id:
        return SandboxResult(
            success=False,
            exit_code=-1,
            output="",
            error="No container ID",
        )

    start_time = time.time()
    try:
        exit_code, output = await asyncio.wait_for(
            manager.exec_command(
                container_id,
                command,
                workdir=workdir,
                environment=exec_env or None,
            ),
            timeout=timeout,
        )
        duration = time.time() - start_time
        if on_output and output:
            try:
                on_output(output)
            except Exception as e:  # noqa: BLE001
                logger.error(f"Output callback error: {e}")
        return SandboxResult(
            success=exit_code == 0,
            exit_code=exit_code,
            output=output,
            duration_seconds=duration,
        )
    except TimeoutError:
        return SandboxResult(
            success=False,
            exit_code=-1,
            output="",
            error=f"Command timed out after {timeout}s",
            duration_seconds=timeout,
        )
    except Exception as e:  # noqa: BLE001
        return SandboxResult(
            success=False,
            exit_code=-1,
            output="",
            error=str(e),
            duration_seconds=time.time() - start_time,
        )


async def exec_command_streaming(
    *,
    manager: DockerManager,
    container_id: str | None,
    status: SandboxStatus,
    command: list[str],
    workdir: str = "/workspace",
    timeout: float = 1800,
    exec_env: dict[str, str] | None = None,
    on_output: Callable[[str], Any] | None = None,
) -> SandboxResult:
    """Run a long-running command, streaming output chunks via ``on_output``."""
    if status != SandboxStatus.RUNNING or not container_id:
        return SandboxResult(
            success=False,
            exit_code=-1,
            output="",
            error=f"Sandbox not running (status: {status})",
        )

    start_time = time.time()
    chunks: list[str] = []
    exit_code = 0
    error: str | None = None
    cid = container_id

    async def _run() -> None:
        nonlocal exit_code, error
        async for kind, payload in manager.exec_command_stream(
            cid,
            command,
            workdir=workdir,
            environment=exec_env or None,
        ):
            if kind in ("stdout", "stderr"):
                chunks.append(payload)
                if on_output:
                    try:
                        on_output(payload)
                    except Exception as cb_err:  # noqa: BLE001
                        logger.error(f"Streaming output callback error: {cb_err}")
            elif kind == "exit":
                exit_code = payload
            elif kind == "error":
                error = payload
                exit_code = -1

    try:
        await asyncio.wait_for(_run(), timeout=timeout)
    except TimeoutError:
        return SandboxResult(
            success=False,
            exit_code=-1,
            output="".join(chunks),
            error=f"Command timed out after {timeout}s",
            duration_seconds=timeout,
        )

    return SandboxResult(
        success=exit_code == 0 and error is None,
        exit_code=exit_code,
        output="".join(chunks),
        error=error,
        duration_seconds=time.time() - start_time,
    )
