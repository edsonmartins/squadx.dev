"""Health checks for a SquadX client host (ADR-0009 / install-vps)."""

from __future__ import annotations

import os
import shutil
import subprocess
from dataclasses import dataclass, field
from enum import Enum
from typing import Callable
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


def check_docker(report: DoctorReport) -> None:
    if not shutil.which("docker"):
        report.add("docker.cli", CheckStatus.FAIL, "docker not found in PATH")
        return
    code, out = _run(["docker", "info"], timeout=20)
    if code != 0:
        report.add(
            "docker.daemon",
            CheckStatus.FAIL,
            f"docker info failed (is the daemon running? DOCKER_HOST={os.environ.get('DOCKER_HOST', 'default')})",
        )
        return
    report.add("docker.cli", CheckStatus.OK, "docker in PATH")
    report.add("docker.daemon", CheckStatus.OK, "daemon reachable")


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

    if not skip_docker:
        check_docker(report)
        if not any(c.name == "docker.daemon" and c.status == CheckStatus.FAIL for c in report.checks):
            check_images(report)
    else:
        report.add("docker", CheckStatus.SKIP, "skipped")

    check_token(report)
    check_llm_keys(report)
    if not skip_api:
        check_api(report)
    check_egress_modules(report)
    check_daemon_pid(report)
    return report
