"""Tests for the host-side cloud-metadata egress block (ADR-0008, Phase 0)."""

from squadx_client.docker.egress_guard import (
    CLOUD_METADATA_TARGETS,
    ensure_cloud_metadata_blocked,
)


class _RecordingRunner:
    """Fake command runner: scripts exit codes by argv and records the calls."""

    def __init__(self, responder):
        self._responder = responder
        self.calls: list[list[str]] = []

    def __call__(self, argv):
        self.calls.append(argv)
        return self._responder(argv)


def test_inserts_drop_rule_when_absent():
    # -C (check) fails => rule missing; -I (insert) succeeds.
    def responder(argv):
        return (0, "") if "-I" in argv else (1, "no such rule")

    runner = _RecordingRunner(responder)
    result = ensure_cloud_metadata_blocked(
        runner=runner, iptables_bin="/sbin/iptables"
    )

    assert result.enforced is True
    assert result.blocked_targets == list(CLOUD_METADATA_TARGETS)
    inserts = [c for c in runner.calls if "-I" in c]
    assert len(inserts) == len(CLOUD_METADATA_TARGETS)
    # Inserted at head of DOCKER-USER as a DROP for each metadata IP.
    for target in CLOUD_METADATA_TARGETS:
        assert any(
            c[:3] == ["/sbin/iptables", "-I", "DOCKER-USER"]
            and "-d" in c
            and target in c
            and c[-2:] == ["-j", "DROP"]
            for c in inserts
        )


def test_idempotent_when_rule_already_present():
    # -C succeeds for every target => nothing inserted.
    runner = _RecordingRunner(lambda argv: (0, ""))
    result = ensure_cloud_metadata_blocked(
        runner=runner, iptables_bin="/sbin/iptables"
    )

    assert result.enforced is True
    assert result.blocked_targets == list(CLOUD_METADATA_TARGETS)
    assert all("-I" not in c for c in runner.calls)


def test_not_enforced_when_iptables_missing(monkeypatch):
    # Force iptables-not-on-PATH regardless of the test host.
    monkeypatch.setattr(
        "squadx_client.docker.egress_guard.shutil.which", lambda _: None
    )
    runner = _RecordingRunner(lambda argv: (0, ""))
    result = ensure_cloud_metadata_blocked(targets=CLOUD_METADATA_TARGETS, runner=runner)

    assert result.enforced is False
    assert result.blocked_targets == []
    assert "NOT blocked" in result.detail
    assert runner.calls == []  # never attempted a rule


def test_not_enforced_when_insert_denied():
    # -C fails (missing) then -I fails (no privilege) => loud, not-enforced.
    def responder(argv):
        if "-I" in argv:
            return (1, "iptables: Permission denied (you must be root)")
        return (1, "no such rule")

    runner = _RecordingRunner(responder)
    result = ensure_cloud_metadata_blocked(
        runner=runner, iptables_bin="/sbin/iptables"
    )

    assert result.enforced is False
    # Stops at the first target it cannot enforce.
    assert result.blocked_targets == []
    assert "NOT blocked" in result.detail


def test_targets_cover_imds_and_ecs_credentials():
    assert "169.254.169.254" in CLOUD_METADATA_TARGETS  # AWS/Azure/GCP IMDS
    assert "169.254.170.2" in CLOUD_METADATA_TARGETS  # AWS ECS task creds
