"""Tests for seccomp profile application in Docker hardening (threat-model #3).

Regression: to_docker_kwargs() used to omit seccomp entirely, so hardened
containers ran with Docker's default (wider) syscall surface. The SDK needs the
profile JSON *content* inlined as `seccomp=<json>`, not a path.
"""

import json

from squadx_client.docker.hardening import (
    SecurityConfig,
    SecurityLevel,
    hardening_manager,
)


def _seccomp_opt(kwargs) -> str | None:
    for opt in kwargs.get("security_opt", []):
        if opt.startswith("seccomp="):
            return opt
    return None


def test_valid_profile_is_inlined_as_json(tmp_path):
    profile = {"defaultAction": "SCMP_ACT_ERRNO", "syscalls": []}
    path = tmp_path / "agent.json"
    path.write_text(json.dumps(profile, indent=2), encoding="utf-8")

    cfg = SecurityConfig(seccomp_profile=str(path))
    opt = _seccomp_opt(cfg.to_docker_kwargs())

    assert opt is not None
    # Content inlined (not a path) and compact.
    payload = opt[len("seccomp=") :]
    assert "/" not in payload.split('"')[0]  # not a filesystem path
    assert json.loads(payload)["defaultAction"] == "SCMP_ACT_ERRNO"
    assert opt == "seccomp=" + json.dumps(profile, separators=(",", ":"))


def test_no_profile_means_no_seccomp_opt():
    cfg = SecurityConfig(seccomp_profile=None)
    assert _seccomp_opt(cfg.to_docker_kwargs()) is None
    # ...but the other hardening opts still apply.
    assert "no-new-privileges:true" in cfg.to_docker_kwargs()["security_opt"]


def test_unconfined_is_passed_through():
    cfg = SecurityConfig(seccomp_profile="unconfined")
    assert _seccomp_opt(cfg.to_docker_kwargs()) == "seccomp=unconfined"


def test_missing_profile_degrades_without_crashing(caplog):
    cfg = SecurityConfig(seccomp_profile="/nonexistent/agent.json")
    kwargs = cfg.to_docker_kwargs()  # must not raise
    assert _seccomp_opt(kwargs) is None
    assert any("seccomp profile not applied" in r.message for r in caplog.records)


def test_invalid_json_degrades_without_crashing(tmp_path, caplog):
    path = tmp_path / "bad.json"
    path.write_text("{ not valid json", encoding="utf-8")
    cfg = SecurityConfig(seccomp_profile=str(path))
    assert _seccomp_opt(cfg.to_docker_kwargs()) is None
    assert any("seccomp profile not applied" in r.message for r in caplog.records)


def test_standard_config_applies_the_shipped_profile():
    # The real default profile (docker/seccomp/agent.json) must resolve and apply.
    cfg = hardening_manager.create_security_config(level=SecurityLevel.STANDARD)
    opt = _seccomp_opt(cfg.to_docker_kwargs())
    assert opt is not None
    profile = json.loads(opt[len("seccomp=") :])
    # The shipped profile is default-deny (errno) with an allowlist.
    assert profile["defaultAction"] == "SCMP_ACT_ERRNO"
    assert profile.get("syscalls")
