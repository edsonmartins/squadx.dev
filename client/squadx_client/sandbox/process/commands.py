"""Build argv for PROCESS isolators (bwrap / Seatbelt / none)."""

from __future__ import annotations

import os
import shlex
import tempfile
from pathlib import Path

from squadx_client.sandbox.errors import SandboxExecError, SandboxNotSupportedError
from squadx_client.sandbox.process.isolator import ProcessIsolator, process_network_mode


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

    host_cwd = workspace
    if workdir.startswith("/workspace"):
        rel = workdir[len("/workspace") :].lstrip("/")
        host_cwd = workspace / rel if rel else workspace

    if isolator is ProcessIsolator.NONE:
        if command[0] == "bash" and len(command) >= 3 and command[1] == "-c":
            return ["bash", "-c", f"cd {shlex.quote(str(host_cwd))} && {command[2]}"]
        return [
            "bash",
            "-c",
            f"cd {shlex.quote(str(host_cwd))} && exec " + shlex.join(command),
        ]

    if isolator is ProcessIsolator.BWRAP:
        return bwrap_argv(workspace, command, workdir=workdir)

    if isolator is ProcessIsolator.SEATBELT:
        if seatbelt_profile is None:
            seatbelt_profile = write_seatbelt_profile(workspace)
        if command[0] == "bash" and len(command) >= 3 and command[1] == "-c":
            inner = f"cd {shlex.quote(str(host_cwd))} && {command[2]}"
        else:
            inner = f"cd {shlex.quote(str(host_cwd))} && " + shlex.join(command)
        return ["sandbox-exec", "-f", str(seatbelt_profile), "bash", "-c", inner]

    raise SandboxNotSupportedError(f"unknown isolator {isolator}")


def bwrap_argv(workspace: Path, command: list[str], *, workdir: str) -> list[str]:
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

    for host_path in (
        "/usr",
        "/bin",
        "/lib",
        "/lib64",
        "/sbin",
        "/etc/resolv.conf",
        "/etc/ssl",
    ):
        if os.path.exists(host_path):
            argv.extend(["--ro-bind", host_path, host_path])

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
    argv.append("--")
    argv.extend(command)
    return argv


def inject_bwrap_env(argv: list[str], env: dict[str, str]) -> list[str]:
    """Insert ``--setenv K V`` before the ``--`` separator for bubblewrap."""
    if not env or not argv or argv[0] != "bwrap":
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


def write_seatbelt_profile(workspace: Path) -> Path:
    """Write a Seatbelt profile for PROCESS on macOS (best-effort, not multi-tenant)."""
    ws = str(workspace.resolve())
    net_rule = (
        "(deny network*)" if process_network_mode() == "deny" else "(allow network*)"
    )
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


def scrubbed_env(extra: dict[str, str]) -> dict[str, str]:
    """Minimal host env + allowed secrets for child process."""
    base = {
        "PATH": os.environ.get("PATH", "/usr/bin:/bin"),
        "HOME": os.environ.get("HOME", "/tmp"),
        "LANG": os.environ.get("LANG", "C.UTF-8"),
        "TERM": os.environ.get("TERM", "xterm"),
        "USER": os.environ.get("USER", "squadx"),
        "TMPDIR": os.environ.get("TMPDIR", "/tmp"),
    }
    for k, v in (extra or {}).items():
        if v is not None:
            base[str(k)] = str(v)
    return base
