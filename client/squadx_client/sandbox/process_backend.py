"""PROCESS sandbox backend — OS primitives without Docker (ADR-0009 Phase 4).

Linux: bubblewrap (``bwrap``) bind-mounts the workspace and ro-binds the toolchain.
macOS: ``sandbox-exec`` (Seatbelt) with a generated profile writing only under workspace.

Threat model is **laptop / Dev LIGHT** — not multi-tenant VPS. Network is host-shared
unless ``SQUADX_PROCESS_NETWORK=deny`` (then ``bwrap --unshare-net`` on Linux).
Live View and External CLI remain Docker-only.
"""

from __future__ import annotations

import asyncio
import logging
import os
import platform
import shlex
import shutil
import tempfile
import time
import uuid
from collections.abc import Callable
from dataclasses import dataclass, field
from enum import Enum
from pathlib import Path
from typing import Any

from squadx_client.config import settings
from squadx_client.sandbox.errors import (
    SandboxExecError,
    SandboxNotSupportedError,
    SandboxStartError,
)
from squadx_client.sandbox.paths import resolve_under_workspace
from squadx_client.sandbox.types import (
    ExecResult,
    SandboxBackendKind,
    SandboxHandle,
    SandboxLifecycleStatus,
)

logger = logging.getLogger(__name__)


class ProcessIsolator(str, Enum):
    BWRAP = "bwrap"
    SEATBELT = "seatbelt"
    NONE = "none"  # only with SQUADX_PROCESS_UNSAFE=1


@dataclass
class ProcessSession:
    """Duck-typed session compatible with agent tools (execute / write_file / …).

    Not an ``AgentSandbox`` — no container_id, no VNC, no egress sidecar.
    """

    task_id: int
    agent_type: str
    workspace_path: str
    isolator: ProcessIsolator
    network_policy: str | None = None
    status: SandboxLifecycleStatus = SandboxLifecycleStatus.CREATED
    live_join_code: str | None = None
    vnc_port: int | None = None
    container_id: str | None = None
    _exec_env: dict[str, str] = field(default_factory=dict)
    _session_id: str = field(default_factory=lambda: uuid.uuid4().hex[:12])
    _seatbelt_profile: Path | None = None
    _started: bool = False

    async def start(
        self,
        image: str = "",  # ignored — no image
        memory_limit: str = "",  # ignored (OS cgroups out of scope for MVP)
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
            # Non-secret create-time env merges into exec env for process sessions
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
                self._seatbelt_profile = _write_seatbelt_profile(root)
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
        # Host-side fs helper (not inside bwrap/seatbelt). Path must stay under workspace.
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
        env = _scrubbed_env(self._exec_env)
        # bwrap --clearenv drops Popen env; re-apply secrets as --setenv
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


def detect_process_isolator() -> ProcessIsolator:
    """Pick the best available isolator for this host."""
    system = platform.system()
    if system == "Linux" and shutil.which("bwrap"):
        return ProcessIsolator.BWRAP
    if system == "Darwin" and shutil.which("sandbox-exec"):
        return ProcessIsolator.SEATBELT
    unsafe = str(os.environ.get("SQUADX_PROCESS_UNSAFE", "")).lower() in (
        "1",
        "true",
        "yes",
    )
    if unsafe:
        logger.warning(
            "process_isolator_none SQUADX_PROCESS_UNSAFE=1 — commands run without OS sandbox"
        )
        return ProcessIsolator.NONE
    if system == "Linux":
        raise SandboxNotSupportedError(
            "PROCESS backend requires bubblewrap (bwrap) on Linux. "
            "Install: apt install bubblewrap / dnf install bubblewrap, "
            "or use SQUADX_SANDBOX_BACKEND=docker."
        )
    if system == "Darwin":
        raise SandboxNotSupportedError(
            "PROCESS backend requires sandbox-exec (Seatbelt) on macOS. "
            "Use SQUADX_SANDBOX_BACKEND=docker (Colima) if unavailable."
        )
    raise SandboxNotSupportedError(
        f"PROCESS backend unsupported on {system}; use docker."
    )


def process_network_mode() -> str:
    """``allow`` (default) or ``deny`` (bwrap --unshare-net only)."""
    raw = str(
        getattr(settings, "process_network", None)
        or os.environ.get("SQUADX_PROCESS_NETWORK", "allow")
    ).strip().lower()
    return "deny" if raw in ("deny", "none", "off", "unshare") else "allow"


def build_isolated_command(
    *,
    isolator: ProcessIsolator,
    workspace: Path,
    command: list[str],
    workdir: str = "/workspace",
    seatbelt_profile: Path | None = None,
) -> list[str]:
    """Build argv that runs ``command`` under the isolator."""
    if not command:
        raise SandboxExecError("empty command")

    # Map workdir for host (NONE isolator)
    host_cwd = workspace
    if workdir.startswith("/workspace"):
        rel = workdir[len("/workspace") :].lstrip("/")
        host_cwd = workspace / rel if rel else workspace

    if isolator is ProcessIsolator.NONE:
        # No isolation — run with host cwd = workspace
        if command[0] == "bash" and len(command) >= 3 and command[1] == "-c":
            return ["bash", "-c", f"cd {shlex.quote(str(host_cwd))} && {command[2]}"]
        return ["bash", "-c", f"cd {shlex.quote(str(host_cwd))} && exec " + shlex.join(command)]

    if isolator is ProcessIsolator.BWRAP:
        return _bwrap_argv(workspace, command, workdir=workdir)

    if isolator is ProcessIsolator.SEATBELT:
        if seatbelt_profile is None:
            seatbelt_profile = _write_seatbelt_profile(workspace)
        # sandbox-exec runs on host FS with profile restrictions; cwd = workspace
        inner = (
            f"cd {shlex.quote(str(host_cwd))} && " + shlex.join(command)
            if command[0] != "bash"
            else (
                f"cd {shlex.quote(str(host_cwd))} && {command[2]}"
                if len(command) >= 3 and command[1] == "-c"
                else f"cd {shlex.quote(str(host_cwd))} && " + shlex.join(command)
            )
        )
        return ["sandbox-exec", "-f", str(seatbelt_profile), "bash", "-c", inner]

    raise SandboxNotSupportedError(f"unknown isolator {isolator}")


def _bwrap_argv(workspace: Path, command: list[str], *, workdir: str) -> list[str]:
    """Construct bubblewrap argv: workspace at /workspace, common ro binds."""
    ws = str(workspace)
    argv: list[str] = [
        "bwrap",
        "--die-with-parent",
        "--unshare-pid",
        "--unshare-ipc",
        "--unshare-uts",
        "--hostname",
        "squadx-sandbox",
        "--dev",
        "/dev",
        "--proc",
        "/proc",
        "--tmpfs",
        "/tmp",
        "--dir",
        "/workspace",
        "--bind",
        ws,
        "/workspace",
        "--chdir",
        workdir if workdir.startswith("/") else f"/workspace/{workdir}",
    ]
    if process_network_mode() == "deny":
        argv.append("--unshare-net")

    # Read-only host toolchain (best-effort; skip missing)
    for host_path in ("/usr", "/bin", "/lib", "/lib64", "/sbin", "/etc/resolv.conf", "/etc/ssl"):
        if os.path.exists(host_path):
            argv.extend(["--ro-bind", host_path, host_path])

    # Symlink-heavy distros: also try /etc/alternatives
    if os.path.isdir("/etc/alternatives"):
        argv.extend(["--ro-bind", "/etc/alternatives", "/etc/alternatives"])

    argv.extend(
        [
            "--clearenv",
            "--setenv",
            "HOME",
            "/workspace",
            "--setenv",
            "PATH",
            "/usr/local/bin:/usr/bin:/bin",
            "--setenv",
            "TERM",
            "xterm",
            "--setenv",
            "LANG",
            os.environ.get("LANG", "C.UTF-8"),
        ]
    )
    # Secrets: inject_bwrap_env() adds --setenv before "--" in ProcessSession._run.

    argv.append("--")
    argv.extend(command)
    return argv


def inject_bwrap_env(argv: list[str], env: dict[str, str]) -> list[str]:
    """Insert ``--setenv K V`` before the ``--`` separator for bubblewrap."""
    if not env or "bwrap" not in (argv[0] if argv else ""):
        return argv
    try:
        sep = argv.index("--")
    except ValueError:
        return argv
    extra: list[str] = []
    for k, v in env.items():
        if not k or v is None:
            continue
        extra.extend(["--setenv", str(k), str(v)])
    return argv[:sep] + extra + argv[sep:]


def _write_seatbelt_profile(workspace: Path) -> Path:
    """Write a Seatbelt profile for PROCESS on macOS.

    Honest limits (not multi-tenant isolation):
    - ``file-read*`` is broad so host Python/toolchain still work (no bind mounts).
    - Writes are limited to workspace + temp dirs.
    - Network follows ``SQUADX_PROCESS_NETWORK`` (deny drops ``network*``).
    """
    ws = str(workspace.resolve())
    net_rule = (
        "(deny network*)"
        if process_network_mode() == "deny"
        else "(allow network*)"
    )
    # sandbox-exec profiles use (subpath "...") — include /private variants on macOS
    profile = f"""(version 1)
(deny default)
(allow process*)
(allow signal)
(allow sysctl-read)
(allow mach-lookup)
(allow mach-register)
(allow ipc-posix*)
; Broad read: required for host interpreters without a chroot/bind model.
; Do not treat this as multi-tenant isolation.
(allow file-read*)
(allow file-write* (subpath "{ws}"))
(allow file-write* (subpath "/tmp"))
(allow file-write* (subpath "/private/tmp"))
(allow file-write* (subpath "/var/folders"))
(allow file-write* (subpath "/private/var/folders"))
{net_rule}
"""
    fd, name = tempfile.mkstemp(prefix="squadx-seatbelt-", suffix=".sb")
    os.close(fd)
    path = Path(name)
    path.write_text(profile, encoding="utf-8")
    return path


def _scrubbed_env(extra: dict[str, str]) -> dict[str, str]:
    """Minimal host env + allowed secrets for child process."""
    base = {
        "PATH": os.environ.get("PATH", "/usr/bin:/bin"),
        "HOME": os.environ.get("HOME", "/tmp"),
        "LANG": os.environ.get("LANG", "C.UTF-8"),
        "TERM": os.environ.get("TERM", "xterm"),
        "USER": os.environ.get("USER", "squadx"),
        "TMPDIR": os.environ.get("TMPDIR", "/tmp"),
    }
    # Never pass host secrets by default — only explicit exec_env
    for k, v in (extra or {}).items():
        if v is not None:
            base[str(k)] = str(v)
    return base


class ProcessSandboxBackend:
    """``SandboxBackend`` using bubblewrap / Seatbelt (no Docker)."""

    def __init__(self, isolator: ProcessIsolator | None = None) -> None:
        self._isolator = isolator  # lazy-detect on first use if None
        self._sessions: dict[str, ProcessSession] = {}

    @property
    def kind(self) -> SandboxBackendKind:
        return SandboxBackendKind.PROCESS

    def supports_live_view(self) -> bool:
        return False

    def supports_egress_sidecar(self) -> bool:
        return False

    def _resolve_isolator(self) -> ProcessIsolator:
        if self._isolator is None:
            self._isolator = detect_process_isolator()
        return self._isolator

    def create_session(
        self,
        *,
        task_id: int,
        agent_type: str,
        workspace_path: str,
        network_policy: str | None = None,
        enable_live_streaming: bool = False,
        ttl_seconds: int | None = None,
    ) -> ProcessSession:
        if enable_live_streaming:
            logger.warning(
                "process_backend_live_ignored task=%s — Live View unsupported", task_id
            )
        isolator = self._resolve_isolator()
        if process_network_mode() == "allow":
            logger.warning(
                "process_network_shared task=%s policy=%s — no Docker egress sidecar; "
                "set SQUADX_PROCESS_NETWORK=deny for bwrap --unshare-net",
                task_id,
                network_policy,
            )
        return ProcessSession(
            task_id=task_id,
            agent_type=agent_type,
            workspace_path=workspace_path,
            isolator=isolator,
            network_policy=network_policy,
        )

    def register_session(self, session: ProcessSession) -> SandboxHandle:
        handle_id = f"proc-{session.task_id}-{session._session_id}"
        self._sessions[handle_id] = session
        return SandboxHandle(
            task_id=session.task_id,
            backend=SandboxBackendKind.PROCESS,
            id=handle_id,
            workspace_path=session.workspace_path,
            status=session.status,
            metadata={"isolator": session.isolator.value, "agent_type": session.agent_type},
        )

    def get_session(self, handle: SandboxHandle) -> ProcessSession:
        try:
            return self._sessions[handle.id]
        except KeyError as e:
            raise SandboxExecError(f"unknown sandbox handle id={handle.id!r}") from e

    async def start(
        self,
        *,
        task_id: int,
        agent_type: str,
        workspace_path: str,
        network_policy: str | None = None,
        enable_live: bool = False,
        memory_limit: str | None = None,
        cpu_limit: float | None = None,
        environment: dict[str, str] | None = None,
        exec_env: dict[str, str] | None = None,
        ttl_seconds: int | None = None,
    ) -> SandboxHandle:
        session = self.create_session(
            task_id=task_id,
            agent_type=agent_type,
            workspace_path=workspace_path,
            network_policy=network_policy,
            enable_live_streaming=enable_live,
            ttl_seconds=ttl_seconds,
        )
        ok = await session.start(
            enable_vnc=enable_live,
            environment=environment,
            exec_env=exec_env,
        )
        if not ok:
            raise SandboxStartError(
                f"PROCESS sandbox failed to start task_id={task_id}"
            )
        return self.register_session(session)

    async def exec(
        self,
        handle: SandboxHandle,
        command: list[str] | str,
        *,
        workdir: str | None = None,
        env: dict[str, str] | None = None,
        timeout: float | None = None,
    ) -> ExecResult:
        session = self.get_session(handle)
        if env:
            old = session._exec_env
            session._exec_env = {**old, **env}
        try:
            cmd = ["bash", "-c", command] if isinstance(command, str) else list(command)
            return await session.execute(
                cmd,
                workdir=workdir or "/workspace",
                timeout=timeout if timeout is not None else 300.0,
            )
        finally:
            if env:
                session._exec_env = old

    async def exec_streaming(
        self,
        handle: SandboxHandle,
        command: list[str] | str,
        *,
        on_output: Callable[[str], Any] | None = None,
        workdir: str | None = None,
        env: dict[str, str] | None = None,
        timeout: float | None = None,
    ) -> ExecResult:
        session = self.get_session(handle)
        if env:
            old = session._exec_env
            session._exec_env = {**old, **env}
        try:
            cmd = ["bash", "-c", command] if isinstance(command, str) else list(command)
            return await session.execute_streaming(
                cmd,
                on_output=on_output,
                workdir=workdir or "/workspace",
                timeout=timeout if timeout is not None else 1800.0,
            )
        finally:
            if env:
                session._exec_env = old

    async def write_file(self, handle: SandboxHandle, path: str, content: str) -> bool:
        return await self.get_session(handle).write_file(path, content)

    async def read_file(self, handle: SandboxHandle, path: str) -> str | None:
        return await self.get_session(handle).read_file(path)

    async def list_dir(self, handle: SandboxHandle, path: str) -> list[str]:
        result = await self.exec(handle, ["ls", "-1A", path], workdir="/workspace", timeout=30)
        if not result.success:
            return []
        return [line for line in result.output.splitlines() if line.strip()]

    async def stop(self, handle: SandboxHandle, *, timeout: int = 10) -> bool:
        return await self.get_session(handle).stop(timeout=timeout)

    async def cleanup(self, handle: SandboxHandle) -> bool:
        session = self.get_session(handle)
        ok = await session.cleanup()
        self._sessions.pop(handle.id, None)
        return ok

    def get_metrics(self, handle: SandboxHandle) -> dict[str, Any] | None:
        return self.get_session(handle).get_metrics()

    async def status(self, handle: SandboxHandle) -> SandboxLifecycleStatus:
        return self.get_session(handle).status
