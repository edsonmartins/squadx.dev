"""Sandbox hardening for the external-CLI path (RFC-0005 / ADR-0007).

Mirrors opentag's ``packages/runner/src/security.ts`` and ``git.ts``:

- an environment allowlist + scrub so secrets that the run does not need are not handed to the CLI;
- prompt-injection detection (instruction override, secret exfiltration, sensitive-file access);
- cleanup of internal agent artifacts (``.claude`` / ``.codex`` / ``.omx``) so they never pollute commits.
"""

from __future__ import annotations

import re
from dataclasses import dataclass
from typing import Any

# ── Environment hygiene ────────────────────────────────────────────────────────

DEFAULT_SAFE_ENV: frozenset[str] = frozenset({
    "CI", "COLORTERM", "CODEX_HOME", "FORCE_COLOR", "HOME", "LANG", "LOGNAME",
    "NO_COLOR", "PATH", "PWD", "SHELL", "SSL_CERT_DIR", "SSL_CERT_FILE", "TERM",
    "TMP", "TMPDIR", "TEMP", "USER", "XDG_CACHE_HOME", "XDG_CONFIG_HOME", "XDG_DATA_HOME",
})

_SAFE_ENV_PREFIXES = ("LC_",)

_SENSITIVE_ENV_PATTERNS = [
    re.compile(p) for p in (
        r"TOKEN", r"SECRET", r"PASSWORD", r"PASS$", r"API[_-]?KEY", r"CREDENTIAL",
        r"COOKIE", r"SESSION", r"AUTH", r"^AWS_", r"^AZURE_", r"^GCP_", r"^GOOGLE_",
        r"^OPENAI_", r"^ANTHROPIC_", r"^SLACK_", r"^GH_TOKEN$", r"^SSH_",
    )
]


# High-confidence secret *value* shapes. These catch a credential stored in a
# benignly-named variable (e.g. MY_CONFIG=sk-live-...), which name-based filtering
# alone would leak. Kept deliberately narrow (known token prefixes / key formats)
# to avoid stripping legitimate config values.
_SENSITIVE_ENV_VALUE_PATTERNS = [
    re.compile(p) for p in (
        r"\bsk-[A-Za-z0-9_-]{16,}",                 # OpenAI/Anthropic-style API keys
        r"\bgh[pousr]_[A-Za-z0-9]{20,}",            # GitHub tokens (ghp_/gho_/...)
        r"\bgithub_pat_[A-Za-z0-9_]{20,}",          # GitHub fine-grained PAT
        r"\bxox[baprs]-[A-Za-z0-9-]{10,}",          # Slack tokens
        r"\bAKIA[0-9A-Z]{16}\b",                     # AWS access key id
        r"\bAIza[0-9A-Za-z_-]{35}\b",               # Google API key
        r"-----BEGIN [A-Z ]*PRIVATE KEY-----",       # PEM private key
    )
]


def _is_safe_env_name(name: str) -> bool:
    return name in DEFAULT_SAFE_ENV or any(name.startswith(p) for p in _SAFE_ENV_PREFIXES)


def _is_sensitive_env_name(name: str) -> bool:
    return any(p.search(name) for p in _SENSITIVE_ENV_PATTERNS)


def _has_sensitive_env_value(value: str) -> bool:
    return bool(value) and any(p.search(value) for p in _SENSITIVE_ENV_VALUE_PATTERNS)


def scrub_env(env: dict[str, str], allow: tuple[str, ...] = ()) -> dict[str, str]:
    """Return a copy of ``env`` keeping only safe or explicitly-allowed variables.

    A variable is kept if it is in the safe allowlist, explicitly allowed (e.g. the provider key
    the run needs), or not sensitive. Sensitive variables that are not explicitly allowed are
    dropped so they never reach the CLI process.
    """
    allowed = set(allow)
    scrubbed: dict[str, str] = {}
    for name, value in env.items():
        if name in allowed or _is_safe_env_name(name):
            scrubbed[name] = value
        elif _is_sensitive_env_name(name):
            continue  # drop secrets the run did not explicitly ask for
        elif _has_sensitive_env_value(value):
            continue  # benign name but the value is secret-shaped (e.g. sk-…) — drop it
        else:
            scrubbed[name] = value
    return scrubbed


# ── Prompt-injection detection ─────────────────────────────────────────────────

SecurityMode = str  # "enforce" | "audit" | "off"


@dataclass(frozen=True)
class SecurityFinding:
    code: str
    severity: str  # "block" | "warn"
    message: str


_HIGH_RISK_PATTERNS: list[tuple[str, re.Pattern[str], str]] = [
    (
        "prompt.instruction_override",
        re.compile(
            r"\b(ignore|disregard|forget)\s+(all\s+)?(previous|prior|system|developer|safety)\s+instructions\b",
            re.IGNORECASE,
        ),
        "Request contains an instruction-override pattern common in prompt injection.",
    ),
    (
        "prompt.secret_exfiltration",
        re.compile(
            r"\b(print|dump|show|reveal|exfiltrate|send|upload|post|copy)\b[\s\S]{0,100}"
            r"\b(secret|token|password|api[\s_-]?key|credential|environment variables?|env vars?)\b",
            re.IGNORECASE,
        ),
        "Request appears to ask the runner to expose secrets or environment variables.",
    ),
    (
        "prompt.sensitive_file_access",
        re.compile(
            r"\b(cat|open|read|print|dump)\b[\s\S]{0,80}(~/\.ssh|~/\.aws|\.env\b|id_rsa|known_hosts|credentials\b)",
            re.IGNORECASE,
        ),
        "Request appears to ask the runner to read sensitive local credential files.",
    ),
]


def assess_prompt(text: str) -> list[SecurityFinding]:
    """Scan a prompt for prompt-injection / exfiltration patterns. All findings are blocking."""
    if not text:
        return []
    findings: list[SecurityFinding] = []
    for code, pattern, message in _HIGH_RISK_PATTERNS:
        if pattern.search(text):
            findings.append(SecurityFinding(code=code, severity="block", message=message))
    return findings


def enforce_prompt_security(text: str, *, mode: SecurityMode, logger: Any) -> None:
    """Assess ``text`` and, in ``enforce`` mode, raise on a block-severity finding.

    Shared by the External-CLI path and the native orchestrator so both apply the
    same policy (previously only the External-CLI path assessed prompts). ``mode``
    is ``settings.cli_security_mode`` (``off`` skips, ``audit`` logs, ``enforce``
    aborts). ``logger`` needs a structlog-style ``warning(event, **fields)``.
    """
    if mode == "off":
        return
    findings = assess_prompt(text)
    if not findings:
        return
    codes = [f.code for f in findings]
    logger.warning("prompt_security_findings", mode=mode, findings=codes)
    if mode == "enforce" and any(f.severity == "block" for f in findings):
        raise RuntimeError("Prompt blocked by security policy: " + ", ".join(codes))


# ── Internal-artifact cleanup ──────────────────────────────────────────────────

INTERNAL_ARTIFACT_ROOTS: tuple[str, ...] = (
    ".claude",
    ".codex",
    ".omx",
    ".aider",
    ".aider.chat.history.md",
    ".aider.input.history",
    ".opencode",
)


def is_internal_artifact_path(path: str) -> bool:
    """True if ``path`` lives under an internal agent-CLI artifact root."""
    normalized = path.strip()
    if normalized.startswith("./"):
        normalized = normalized[2:]  # strip a leading "./" prefix only (not all dots)
    return any(normalized == root or normalized.startswith(f"{root}/") for root in INTERNAL_ARTIFACT_ROOTS)


def filter_internal_artifacts(paths: list[str]) -> list[str]:
    """Drop internal agent-CLI artifacts so they never enter a commit."""
    return [p for p in paths if not is_internal_artifact_path(p)]
