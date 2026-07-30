"""Health checks for a SquadX client host (ADR-0009 / install-vps)."""

from __future__ import annotations

import os
import shutil
import subprocess
from collections.abc import Callable
from dataclasses import dataclass, field
from enum import Enum
from urllib.parse import urlparse

import httpx
import structlog

from squadx_client.config import settings

logger = structlog.get_logger()


class CheckStatus(str, Enum):
    OK = "ok"
    WARN = "warn"
    FAIL = "fail"
    SKIP = "skip"


@dataclass
class CheckResult:
    name: str
    status: CheckStatus
    detail: str = ""


@dataclass
class DoctorReport:
    checks: list[CheckResult] = field(default_factory=list)

    @property
    def ok(self) -> bool:
        return not any(c.status == CheckStatus.FAIL for c in self.checks)

    def add(self, name: str, status: CheckStatus, detail: str = "") -> None:
        self.checks.append(CheckResult(name=name, status=status, detail=detail))


def _run(cmd: list[str], timeout: float = 15.0) -> tuple[int, str]:
    try:
        proc = subprocess.run(  # noqa: S603
            cmd,
            capture_output=True,
            text=True,
            timeout=timeout,
        )
        out = (proc.stdout or "") + (proc.stderr or "")
        return proc.returncode, out.strip()
    except FileNotFoundError:
        return 127, "command not found"
    except subprocess.TimeoutExpired:
        return 124, "timeout"


def check_sandbox_backend(report: DoctorReport) -> None:
    """Report configured ADR-0009 backend and feature matrix row."""
    from squadx_client.sandbox import (
        features_for,
        get_sandbox_backend_kind,
        parse_backend_kind,
    )

    raw = getattr(settings, "sandbox_backend", "docker")
    try:
        kind = parse_backend_kind(raw)
    except ValueError as e:
        report.add("sandbox.backend", CheckStatus.FAIL, str(e))
        return

    # Ensure factory path is exercised (settings.sandbox_backend is not a dead-cap)
    configured = get_sandbox_backend_kind()
    feats = features_for(configured)
    detail = (
        f"{configured.value} | live={feats.live_view} egress_sidecar={feats.egress_sidecar} "
        f"external_cli={feats.external_cli} implemented={feats.implemented}"
    )
    if kind is not configured:
        report.add(
            "sandbox.backend",
            CheckStatus.FAIL,
            f"parse mismatch raw={raw!r} kind={kind.value} configured={configured.value}",
        )
        return
    if not feats.implemented:
        report.add(
            "sandbox.backend",
            CheckStatus.FAIL,
            f"{detail} — {feats.notes}. Set SQUADX_SANDBOX_BACKEND=docker",
        )
        return

    report.add("sandbox.backend", CheckStatus.OK, f"{detail} ({feats.notes})")

    if not feats.requires_docker:
        check_process_isolator(report)


def check_process_isolator(report: DoctorReport) -> None:
    """Verify bubblewrap / Seatbelt availability for PROCESS backend."""
    from squadx_client.sandbox.errors import SandboxNotSupportedError
    from squadx_client.sandbox.process_backend import detect_process_isolator

    try:
        isolator = detect_process_isolator()
        net = getattr(settings, "process_network", "allow")
        report.add(
            "sandbox.process_isolator",
            CheckStatus.OK,
            f"{isolator.value} | SQUADX_PROCESS_NETWORK={net} | live_view=unsupported",
        )
        if str(net).lower() not in ("deny", "none", "off", "unshare"):
            report.add(
                "sandbox.process_network",
                CheckStatus.WARN,
                "host network shared (no Docker egress sidecar) — set "
                "SQUADX_PROCESS_NETWORK=deny for bwrap --unshare-net",
            )
    except SandboxNotSupportedError as e:
        report.add("sandbox.process_isolator", CheckStatus.FAIL, str(e))


def check_colima(report: DoctorReport) -> None:
    """On macOS, surface Colima / DOCKER_HOST so Dev LIGHT install is diagnosable."""
    if os.uname().sysname != "Darwin":
        return

    docker_host = os.environ.get("DOCKER_HOST", "")
    colima_socks = [
        os.path.expanduser("~/.colima/default/docker.sock"),
        os.path.expanduser("~/.colima/docker.sock"),
    ]
    sock_found = next((s for s in colima_socks if os.path.exists(s)), None)

    if shutil.which("colima"):
        code, out = _run(["colima", "status"], timeout=15)
        running = code == 0 and "Running" in (out or "")
        if running:
            report.add(
                "docker.colima",
                CheckStatus.OK,
                f"Colima running; DOCKER_HOST={docker_host or 'default'}",
            )
        else:
            report.add(
                "docker.colima",
                CheckStatus.WARN,
                "Colima installed but not Running — colima start "
                "(or use Docker Desktop and unset DOCKER_HOST)",
            )
    elif sock_found or "colima" in docker_host:
        report.add(
            "docker.colima",
            CheckStatus.WARN,
            f"Colima CLI missing but socket/host looks like Colima "
            f"(sock={sock_found}, DOCKER_HOST={docker_host or 'unset'})",
        )
    elif not docker_host and not sock_found:
        report.add(
            "docker.colima",
            CheckStatus.SKIP,
            "no Colima socket — Docker Desktop or remote DOCKER_HOST OK for Dev LIGHT",
        )


def check_docker(report: DoctorReport) -> None:
    if not shutil.which("docker"):
        report.add("docker.cli", CheckStatus.FAIL, "docker not found in PATH")
        return
    report.add("docker.cli", CheckStatus.OK, "docker in PATH")

    docker_host = os.environ.get("DOCKER_HOST", "")
    # Help Mac users: if Colima socket exists and DOCKER_HOST is unset, hint it.
    if os.uname().sysname == "Darwin" and not docker_host:
        for sock in (
            os.path.expanduser("~/.colima/default/docker.sock"),
            os.path.expanduser("~/.colima/docker.sock"),
        ):
            if os.path.exists(sock):
                report.add(
                    "docker.host_hint",
                    CheckStatus.WARN,
                    f"Colima socket at {sock} but DOCKER_HOST unset — "
                    f"export DOCKER_HOST=unix://{sock} or source ~/.squadx/env.sh",
                )
                break

    code, _out = _run(["docker", "info"], timeout=20)
    if code != 0:
        report.add(
            "docker.daemon",
            CheckStatus.FAIL,
            f"docker info failed (is the daemon running? DOCKER_HOST={docker_host or 'default'}; "
            f"on Mac: colima start && source ~/.squadx/env.sh)",
        )
        return
    report.add(
        "docker.daemon",
        CheckStatus.OK,
        f"daemon reachable (DOCKER_HOST={docker_host or 'default'})",
    )


def check_images(report: DoctorReport) -> None:
    agent = settings.agent_image
    egress = settings.egress_sidecar_image
    for label, image in (("agent_image", agent), ("egress_image", egress)):
        code, _ = _run(["docker", "image", "inspect", image], timeout=10)
        if code == 0:
            report.add(label, CheckStatus.OK, image)
        else:
            # Missing image is fail if we intend to run sandboxes
            report.add(
                label,
                CheckStatus.FAIL,
                f"{image} not found locally — pull GHCR or run make build-sandbox-images",
            )


def check_api(report: DoctorReport, timeout: float = 5.0) -> None:
    base = (settings.api_url or "").rstrip("/")
    if not base:
        report.add("api.url", CheckStatus.FAIL, "SQUADX_API_URL empty")
        return
    parsed = urlparse(base)
    if parsed.scheme not in ("http", "https") or not parsed.netloc:
        report.add("api.url", CheckStatus.FAIL, f"invalid SQUADX_API_URL: {base}")
        return
    report.add("api.url", CheckStatus.OK, base)

    health_url = f"{base}/api/v1/health"
    try:
        with httpx.Client(timeout=timeout) as client:
            resp = client.get(health_url)
        if resp.status_code == 200:
            report.add("api.health", CheckStatus.OK, f"{health_url} → 200")
        else:
            report.add(
                "api.health",
                CheckStatus.FAIL,
                f"{health_url} → HTTP {resp.status_code}",
            )
    except httpx.HTTPError as e:
        report.add("api.health", CheckStatus.FAIL, f"{health_url}: {e}")


def check_token(report: DoctorReport) -> None:
    token = settings.api_token
    if not token or token in ("change-me", "your-api-token-here"):
        report.add(
            "api.token",
            CheckStatus.FAIL,
            "SQUADX_API_TOKEN missing or still a placeholder",
        )
    else:
        report.add("api.token", CheckStatus.OK, f"set (len={len(token)})")


def check_llm_keys(report: DoctorReport) -> None:
    has_or = bool(settings.openrouter_api_key or os.environ.get("OPENROUTER_API_KEY"))
    has_oai = bool(settings.openai_api_key or os.environ.get("OPENAI_API_KEY"))
    has_ant = bool(settings.anthropic_api_key or os.environ.get("ANTHROPIC_API_KEY"))
    has_goo = bool(settings.google_api_key or os.environ.get("GOOGLE_API_KEY"))
    if has_or or has_oai or has_ant or has_goo:
        providers = []
        if has_or:
            providers.append("openrouter")
        if has_oai:
            providers.append("openai")
        if has_ant:
            providers.append("anthropic")
        if has_goo:
            providers.append("google")
        report.add(
            "llm.keys",
            CheckStatus.OK,
            f"provider key(s): {', '.join(providers)}; model={settings.default_model}",
        )
    else:
        report.add(
            "llm.keys",
            CheckStatus.FAIL,
            "no LLM key (set OPENROUTER_API_KEY, OPENAI_API_KEY, ANTHROPIC_API_KEY, or GOOGLE_API_KEY)",
        )


def check_egress_modules(report: DoctorReport) -> None:
    if not settings.egress_sidecar_enabled:
        report.add(
            "egress.sidecar",
            CheckStatus.WARN,
            "SQUADX_EGRESS_SIDECAR=false — agent network unrestricted (except host metadata rules)",
        )
        return
    report.add("egress.sidecar", CheckStatus.OK, "enabled (default-deny + allowlist)")
    # xt_set only meaningful on Linux host (not macOS control plane for Colima)
    if os.uname().sysname != "Linux":
        report.add(
            "egress.kernel",
            CheckStatus.WARN,
            f"{os.uname().sysname}: full egress IT needs Linux host with xt_set; Colima uses Linux VM",
        )
        return
    code, out = _run(["sh", "-c", "lsmod 2>/dev/null | grep -E '^ip_set|^xt_set' || true"])
    if "ip_set" in out or "xt_set" in out:
        report.add("egress.kernel", CheckStatus.OK, "ip_set/xt_set modules visible")
    else:
        report.add(
            "egress.kernel",
            CheckStatus.WARN,
            "ip_set/xt_set not listed — egress may fail-closed without modules",
        )


def check_daemon_pid(report: DoctorReport) -> None:
    from squadx_client.daemon import SquadXDaemon

    pid_path = SquadXDaemon.pid_file_path()
    try:
        pid = int(pid_path.read_text().strip())
    except (FileNotFoundError, ValueError, OSError):
        report.add("daemon", CheckStatus.SKIP, "not running (no PID file)")
        return
    try:
        os.kill(pid, 0)
        report.add("daemon", CheckStatus.OK, f"running PID {pid}")
    except ProcessLookupError:
        report.add("daemon", CheckStatus.WARN, f"stale PID file ({pid})")
    except PermissionError:
        report.add("daemon", CheckStatus.OK, f"PID {pid} exists (no signal permission)")


def run_doctor(
    *,
    skip_docker: bool = False,
    skip_api: bool = False,
    checks: list[Callable[[DoctorReport], None]] | None = None,
) -> DoctorReport:
    """Run all doctor checks and return a report."""
    report = DoctorReport()
    if checks is not None:
        for fn in checks:
            fn(report)
        return report

    check_sandbox_backend(report)

    from squadx_client.sandbox import features_for

    feats = features_for()
    if skip_docker or not feats.requires_docker:
        reason = (
            "skipped"
            if skip_docker
            else f"backend={feats.kind.value} does not require Docker"
        )
        report.add("docker", CheckStatus.SKIP, reason)
    else:
        check_colima(report)
        check_docker(report)
        if not any(
            c.name == "docker.daemon" and c.status == CheckStatus.FAIL for c in report.checks
        ):
            check_images(report)

    check_token(report)
    check_llm_keys(report)
    if not skip_api:
        check_api(report)
    # Egress sidecar only meaningful when Docker backend advertises it
    if feats.egress_sidecar:
        check_egress_modules(report)
    else:
        report.add(
            "egress.sidecar",
            CheckStatus.SKIP,
            f"backend={feats.kind.value} has no Docker egress sidecar",
        )
    check_daemon_pid(report)
    return report
