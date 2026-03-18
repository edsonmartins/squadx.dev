"""Tests for sandbox lifecycle management module."""

from datetime import datetime, timedelta, timezone
from unittest.mock import MagicMock

import pytest

from squadx_client.docker.lifecycle import (
    VALID_TRANSITIONS,
    SandboxInfo,
    SandboxLifecycleManager,
    SandboxState,
)


class TestSandboxStateEnum:
    """Test SandboxState enum values."""

    def test_all_values(self):
        assert SandboxState.PENDING.value == "pending"
        assert SandboxState.CREATING.value == "creating"
        assert SandboxState.RUNNING.value == "running"
        assert SandboxState.PAUSED.value == "paused"
        assert SandboxState.STOPPING.value == "stopping"
        assert SandboxState.STOPPED.value == "stopped"
        assert SandboxState.EXPIRED.value == "expired"
        assert SandboxState.FAILED.value == "failed"

    def test_is_string_enum(self):
        assert isinstance(SandboxState.RUNNING, str)
        assert SandboxState.RUNNING == "running"


class TestValidTransitions:
    """Test that the VALID_TRANSITIONS map is correct."""

    def test_terminal_states_have_no_transitions(self):
        assert VALID_TRANSITIONS[SandboxState.STOPPED] == set()
        assert VALID_TRANSITIONS[SandboxState.EXPIRED] == set()
        assert VALID_TRANSITIONS[SandboxState.FAILED] == set()

    def test_pending_can_go_to_creating_or_failed(self):
        assert VALID_TRANSITIONS[SandboxState.PENDING] == {
            SandboxState.CREATING,
            SandboxState.FAILED,
        }

    def test_running_transitions(self):
        expected = {
            SandboxState.PAUSED,
            SandboxState.STOPPING,
            SandboxState.EXPIRED,
            SandboxState.FAILED,
        }
        assert VALID_TRANSITIONS[SandboxState.RUNNING] == expected

    def test_all_states_have_entries(self):
        for state in SandboxState:
            assert state in VALID_TRANSITIONS


class TestSandboxInfo:
    """Test SandboxInfo properties."""

    def test_is_terminal_for_terminal_states(self):
        for state in (SandboxState.STOPPED, SandboxState.EXPIRED, SandboxState.FAILED):
            info = SandboxInfo(sandbox_id="x", state=state)
            assert info.is_terminal is True

    def test_is_terminal_false_for_active(self):
        info = SandboxInfo(sandbox_id="x", state=SandboxState.RUNNING)
        assert info.is_terminal is False

    def test_is_active_for_running_and_paused(self):
        for state in (SandboxState.RUNNING, SandboxState.PAUSED):
            info = SandboxInfo(sandbox_id="x", state=state)
            assert info.is_active is True

    def test_is_active_false_for_pending(self):
        info = SandboxInfo(sandbox_id="x", state=SandboxState.PENDING)
        assert info.is_active is False

    def test_uptime_seconds_zero_when_not_started(self):
        info = SandboxInfo(sandbox_id="x")
        assert info.uptime_seconds == 0.0

    def test_uptime_seconds_computed_when_started(self):
        now = datetime.now(timezone.utc)
        info = SandboxInfo(
            sandbox_id="x",
            started_at=now - timedelta(seconds=120),
            stopped_at=now,
        )
        assert abs(info.uptime_seconds - 120.0) < 1.0

    def test_is_expired_false_when_no_expires_at(self):
        info = SandboxInfo(sandbox_id="x")
        assert info.is_expired is False

    def test_is_expired_true_when_past(self):
        info = SandboxInfo(
            sandbox_id="x",
            expires_at=datetime.now(timezone.utc) - timedelta(seconds=10),
        )
        assert info.is_expired is True

    def test_is_expired_false_when_future(self):
        info = SandboxInfo(
            sandbox_id="x",
            expires_at=datetime.now(timezone.utc) + timedelta(hours=1),
        )
        assert info.is_expired is False

    def test_to_dict_contains_expected_keys(self):
        info = SandboxInfo(sandbox_id="sb-1", image="python:3.12")
        d = info.to_dict()
        assert d["sandboxId"] == "sb-1"
        assert d["image"] == "python:3.12"
        assert d["state"] == "pending"
        assert "createdAt" in d
        assert "uptimeSeconds" in d
        assert d["startedAt"] is None
        assert d["stoppedAt"] is None


class TestSandboxLifecycleManager:
    """Test SandboxLifecycleManager operations."""

    def _make_manager(self, **kwargs):
        return SandboxLifecycleManager(**kwargs)

    def test_register_creates_sandbox(self):
        mgr = self._make_manager()
        info = mgr.register("sb-1", image="python:3.12")
        assert info.sandbox_id == "sb-1"
        assert info.state == SandboxState.PENDING
        assert info.expires_at is not None
        assert mgr.get("sb-1") is info

    def test_register_respects_max_ttl(self):
        mgr = self._make_manager(default_ttl_seconds=100, max_ttl_seconds=60)
        info = mgr.register("sb-1")
        # TTL should be clamped to max_ttl (60s)
        expected_max = datetime.now(timezone.utc) + timedelta(seconds=65)
        assert info.expires_at < expected_max

    def test_valid_transition(self):
        mgr = self._make_manager()
        mgr.register("sb-1")
        assert mgr.transition("sb-1", SandboxState.CREATING) is True
        assert mgr.get("sb-1").state == SandboxState.CREATING

    def test_invalid_transition_returns_false(self):
        mgr = self._make_manager()
        mgr.register("sb-1")
        # PENDING -> RUNNING is not valid (must go through CREATING first)
        assert mgr.transition("sb-1", SandboxState.RUNNING) is False
        assert mgr.get("sb-1").state == SandboxState.PENDING

    def test_transition_unknown_sandbox_returns_false(self):
        mgr = self._make_manager()
        assert mgr.transition("nonexistent", SandboxState.RUNNING) is False

    def test_transition_to_running_sets_started_at(self):
        mgr = self._make_manager()
        mgr.register("sb-1")
        mgr.transition("sb-1", SandboxState.CREATING)
        mgr.transition("sb-1", SandboxState.RUNNING)
        info = mgr.get("sb-1")
        assert info.started_at is not None
        assert info.last_activity is not None

    def test_transition_to_failed_sets_error_and_stopped_at(self):
        mgr = self._make_manager()
        mgr.register("sb-1")
        mgr.transition("sb-1", SandboxState.FAILED, error="OOM killed")
        info = mgr.get("sb-1")
        assert info.error_message == "OOM killed"
        assert info.stopped_at is not None

    def test_state_change_callback_invoked(self):
        callback = MagicMock()
        mgr = self._make_manager()
        mgr.on_state_change(callback)
        mgr.register("sb-1")
        mgr.transition("sb-1", SandboxState.CREATING)
        callback.assert_called_once_with("sb-1", SandboxState.PENDING, SandboxState.CREATING)

    def test_renew_extends_ttl(self):
        mgr = self._make_manager(default_ttl_seconds=60)
        mgr.register("sb-1")
        mgr.transition("sb-1", SandboxState.CREATING)
        mgr.transition("sb-1", SandboxState.RUNNING)
        new_exp = mgr.renew("sb-1", additional_seconds=300)
        assert new_exp is not None
        # New expiration should be ~300s from now
        delta = (new_exp - datetime.now(timezone.utc)).total_seconds()
        assert 295 < delta < 305

    def test_renew_returns_none_for_terminal(self):
        mgr = self._make_manager()
        mgr.register("sb-1")
        mgr.transition("sb-1", SandboxState.FAILED)
        assert mgr.renew("sb-1") is None

    def test_renew_returns_none_for_unknown(self):
        mgr = self._make_manager()
        assert mgr.renew("nonexistent") is None

    def test_record_activity_updates_last_activity(self):
        mgr = self._make_manager()
        mgr.register("sb-1")
        mgr.transition("sb-1", SandboxState.CREATING)
        mgr.transition("sb-1", SandboxState.RUNNING)
        before = mgr.get("sb-1").last_activity
        mgr.record_activity("sb-1")
        after = mgr.get("sb-1").last_activity
        assert after >= before

    def test_record_activity_noop_for_non_active(self):
        mgr = self._make_manager()
        mgr.register("sb-1")
        mgr.record_activity("sb-1")  # PENDING is not active
        assert mgr.get("sb-1").last_activity is None

    def test_list_active(self):
        mgr = self._make_manager()
        mgr.register("sb-1")
        mgr.register("sb-2")
        mgr.transition("sb-1", SandboxState.CREATING)
        mgr.transition("sb-1", SandboxState.RUNNING)
        # sb-2 is still PENDING (not active)
        active = mgr.list_active()
        assert len(active) == 1
        assert active[0].sandbox_id == "sb-1"

    def test_list_all(self):
        mgr = self._make_manager()
        mgr.register("sb-1")
        mgr.register("sb-2")
        assert len(mgr.list_all()) == 2

    def test_remove_deletes_sandbox(self):
        mgr = self._make_manager()
        mgr.register("sb-1")
        mgr.remove("sb-1")
        assert mgr.get("sb-1") is None
        assert len(mgr.list_all()) == 0

    def test_get_stats(self):
        mgr = self._make_manager()
        mgr.register("sb-1")
        mgr.register("sb-2")
        mgr.transition("sb-1", SandboxState.CREATING)
        mgr.transition("sb-1", SandboxState.RUNNING)
        stats = mgr.get_stats()
        assert stats["total"] == 2
        assert stats["active"] == 1
        assert stats["byState"]["running"] == 1
        assert stats["byState"]["pending"] == 1
        assert "avgUptimeSeconds" in stats
