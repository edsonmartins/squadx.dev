"""PROCESS session — SandboxSession implementation without Docker."""

from __future__ import annotations

import asyncio
import logging
import time
import uuid
from collections.abc import Callable
from dataclasses import dataclass, field
from pathlib import Path
from typing import Any

from squadx_client.sandbox.errors import SandboxExecError
from squadx_client.sandbox.paths import resolve_under_workspace
from squadx_client.sandbox.process.commands import (
    build_isolated_command,
    inject_bwrap_env,
    scrubbed_env,
    write_seatbelt_profile,
)
from squadx_client.sandbox.process.isolator import ProcessIsolator
from squadx_client.sandbox.types import ExecResult, SandboxLifecycleStatus

logger = logging.getLogger(__name__)


@dataclass
class ProcessSession:
    """SandboxSession for PROCESS: execute* under isolator; fs on host with containment."""

    task_id: int
    agent_type: str
    workspace_path: str
    isolator: ProcessIsolator
    network_policy: str | None = None
    status: SandboxLifecycleStatus = SandboxLifecycleStatus.CREATED
    live_join_code: str | None = None
    vnc_port: int | None = None
    _exec_env: dict[str, str] = field(default_factory=dict)
    _session_id: str = field(default_factory=lambda: uuid.uuid4().hex[:12])
    _seatbelt_profile: Path | None = None
    _started: bool = False

    async def start(
        self,
        image: str = "",
        memory_limit: str = "",
        cpu_limit: float = 0.0,
        enable_vnc: bool = False,
        environment: dict | None = None,
        exec_env: dict | None = None,
    ) -> bool:
        if enable_vnc:
            logger.warning(
                "process_live_view_unsupported task=%s — Live View requires Docker",
                self.task_id,
            )
        self._exec_env = dict(exec_env or {})
        if environment:
            self._exec_env = {**dict(environment), **self._exec_env}

        root = Path(self.workspace_path).expanduser().resolve()
        try:
            root.mkdir(parents=True, exist_ok=True)
        except OSError as e:
            logger.error("process_workspace_mkdir_failed path=%s err=%s", root, e)
            self.status = SandboxLifecycleStatus.ERROR
            return False

        if self.isolator is ProcessIsolator.SEATBELT:
            try:
                self._seatbelt_profile = write_seatbelt_profile(root)
            except OSError as e:
                logger.error("seatbelt_profile_failed err=%s", e)
                self.status = SandboxLifecycleStatus.ERROR
                return False

        self._started = True
        self.status = SandboxLifecycleStatus.RUNNING
        logger.info(
            "process_sandbox_started task=%s isolator=%s workspace=%s",
            self.task_id,
            self.isolator.value,
            root,
        )
        return True

    async def stop(self, timeout: int = 10) -> bool:
        self.status = SandboxLifecycleStatus.STOPPED
        return True

    async def cleanup(self) -> bool:
        if self._seatbelt_profile and self._seatbelt_profile.exists():
            try:
                self._seatbelt_profile.unlink()
            except OSError:
                pass
            self._seatbelt_profile = None
        self.status = SandboxLifecycleStatus.STOPPED
        self._started = False
        return True

    async def execute(
        self,
        command: list[str],
        workdir: str = "/workspace",
        timeout: float = 300,
    ) -> ExecResult:
        return await self._run(command, workdir=workdir, timeout=timeout, on_output=None)

    async def execute_streaming(
        self,
        command: list[str],
        on_output=None,
        workdir: str = "/workspace",
        timeout: float = 1800,
    ) -> ExecResult:
        return await self._run(
            command, workdir=workdir, timeout=timeout, on_output=on_output
        )

    async def write_file(self, path: str, content: str) -> bool:
        try:
            host_path = resolve_under_workspace(self.workspace_path, path)
            host_path.parent.mkdir(parents=True, exist_ok=True)
            host_path.write_text(content, encoding="utf-8")
            return True
        except SandboxExecError as e:
            logger.error("process_write_file_refused err=%s", e)
            return False
        except OSError as e:
            logger.error("process_write_file_failed path=%s err=%s", path, e)
            return False

    async def read_file(self, path: str) -> str | None:
        try:
            host_path = resolve_under_workspace(self.workspace_path, path)
            return host_path.read_text(encoding="utf-8")
        except (SandboxExecError, OSError):
            return None

    def get_metrics(self) -> dict[str, Any] | None:
        return {
            "backend": "process",
            "isolator": self.isolator.value,
            "workspace": self.workspace_path,
        }

    async def _run(
        self,
        command: list[str],
        *,
        workdir: str,
        timeout: float,
        on_output: Callable[[str], Any] | None,
    ) -> ExecResult:
        if not self._started or self.status is not SandboxLifecycleStatus.RUNNING:
            return ExecResult(
                success=False,
                exit_code=-1,
                output="",
                error=f"Sandbox not running (status: {self.status})",
            )

        full_cmd = build_isolated_command(
            isolator=self.isolator,
            workspace=Path(self.workspace_path).expanduser().resolve(),
            command=list(command),
            workdir=workdir,
            seatbelt_profile=self._seatbelt_profile,
        )
        env = scrubbed_env(self._exec_env)
        if self.isolator is ProcessIsolator.BWRAP and self._exec_env:
            full_cmd = inject_bwrap_env(full_cmd, self._exec_env)

        start = time.monotonic()
        deadline = start + max(0.1, float(timeout))

        def _remaining() -> float:
            return max(0.01, deadline - time.monotonic())

        try:
            proc = await asyncio.create_subprocess_exec(
                *full_cmd,
                stdout=asyncio.subprocess.PIPE,
                stderr=asyncio.subprocess.STDOUT,
                env=env,
                cwd=(
                    str(Path(self.workspace_path).expanduser().resolve())
                    if self.isolator is ProcessIsolator.NONE
                    else None
                ),
            )
            chunks: list[str] = []
            assert proc.stdout is not None
            try:
                while True:
                    if time.monotonic() >= deadline:
                        raise TimeoutError
                    line = await asyncio.wait_for(
                        proc.stdout.readline(), timeout=_remaining()
                    )
                    if not line:
                        break
                    text = line.decode("utf-8", errors="replace")
                    chunks.append(text)
                    if on_output:
                        try:
                            on_output(text)
                        except Exception as cb_err:  # noqa: BLE001
                            logger.error("process_output_callback_error err=%s", cb_err)
                await asyncio.wait_for(proc.wait(), timeout=_remaining())
            except TimeoutError:
                proc.kill()
                await proc.wait()
                return ExecResult(
                    success=False,
                    exit_code=-1,
                    output="".join(chunks),
                    error=f"Command timed out after {timeout}s",
                    duration_seconds=time.monotonic() - start,
                )

            exit_code = proc.returncode if proc.returncode is not None else -1
            return ExecResult(
                success=exit_code == 0,
                exit_code=exit_code,
                output="".join(chunks),
                duration_seconds=time.monotonic() - start,
            )
        except FileNotFoundError as e:
            return ExecResult(
                success=False,
                exit_code=-1,
                output="",
                error=f"isolator binary missing: {e}",
                duration_seconds=time.monotonic() - start,
            )
        except Exception as e:  # noqa: BLE001
            logger.exception("process_exec_failed")
            return ExecResult(
                success=False,
                exit_code=-1,
                output="",
                error=str(e),
                duration_seconds=time.monotonic() - start,
            )
