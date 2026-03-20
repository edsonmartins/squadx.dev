"""Tests for agent lifecycle protocol module."""

import time
from unittest.mock import MagicMock

import pytest

from squadx_client.docker.agent_lifecycle import (
    AgentLifecycleProtocol,
    AgentState,
    AgentStatus,
)


class TestAgentStateEnum:
    """Test AgentState enum values."""

    def test_all_values(self):
        assert AgentState.STARTING.value == "starting"
        assert AgentState.READY.value == "ready"
        assert AgentState.WORKING.value == "working"
        assert AgentState.IDLE.value == "idle"
        assert AgentState.SHUTTING_DOWN.value == "shutting_down"
        assert AgentState.DEAD.value == "dead"

    def test_is_string_enum(self):
        assert isinstance(AgentState.WORKING, str)
        assert AgentState.WORKING == "working"


class TestAgentStatus:
    """Test AgentStatus properties."""

    def test_default_state_is_starting(self):
        status = AgentStatus(agent_id="a1", agent_name="frontend")
        assert status.state == AgentState.STARTING

    def test_is_alive_true_for_active_states(self):
        for state in (AgentState.STARTING, AgentState.READY, AgentState.WORKING, AgentState.IDLE):
            status = AgentStatus(agent_id="a1", agent_name="test", state=state)
            assert status.is_alive is True

    def test_is_alive_false_for_dead_and_shutting_down(self):
        for state in (AgentState.DEAD, AgentState.SHUTTING_DOWN):
            status = AgentStatus(agent_id="a1", agent_name="test", state=state)
            assert status.is_alive is False

    def test_seconds_since_heartbeat(self):
        status = AgentStatus(agent_id="a1", agent_name="test", last_heartbeat=time.time() - 60)
        assert 59 < status.seconds_since_heartbeat < 62

    def test_to_dict_contains_expected_keys(self):
        status = AgentStatus(agent_id="a1", agent_name="frontend", current_task_id="t1")
        d = status.to_dict()
        assert d["agentId"] == "a1"
        assert d["agentName"] == "frontend"
        assert d["state"] == "starting"
        assert d["currentTaskId"] == "t1"
        assert "secondsSinceHeartbeat" in d
        assert "lastHeartbeat" in d
        assert d["error"] is None


class TestAgentLifecycleProtocol:
    """Test AgentLifecycleProtocol operations."""

    def _make_protocol(self, **kwargs):
        return AgentLifecycleProtocol(**kwargs)

    def test_register_creates_agent(self):
        proto = self._make_protocol()
        status = proto.register("a1", "frontend")
        assert status.agent_id == "a1"
        assert status.agent_name == "frontend"
        assert status.state == AgentState.STARTING
        assert proto.get_status("a1") is status

    def test_heartbeat_updates_timestamp(self):
        proto = self._make_protocol()
        proto.register("a1", "frontend")
        old_hb = proto.get_status("a1").last_heartbeat
        time.sleep(0.01)
        proto.heartbeat("a1")
        assert proto.get_status("a1").last_heartbeat > old_hb

    def test_heartbeat_noop_for_unknown_agent(self):
        proto = self._make_protocol()
        proto.heartbeat("nonexistent")  # Should not raise

    def test_transition_to_working(self):
        proto = self._make_protocol()
        proto.register("a1", "frontend")
        proto.transition("a1", AgentState.WORKING, task_id="t1")
        status = proto.get_status("a1")
        assert status.state == AgentState.WORKING
        assert status.current_task_id == "t1"
        assert status.idle_since is None

    def test_transition_to_idle(self):
        proto = self._make_protocol()
        proto.register("a1", "frontend")
        proto.transition("a1", AgentState.IDLE)
        status = proto.get_status("a1")
        assert status.state == AgentState.IDLE
        assert status.current_task_id is None
        assert status.idle_since is not None

    def test_transition_noop_for_unknown_agent(self):
        proto = self._make_protocol()
        proto.transition("nonexistent", AgentState.WORKING)  # Should not raise

    def test_detect_dead_agents(self):
        proto = self._make_protocol(dead_threshold=1)
        proto.register("a1", "frontend")
        # Force old heartbeat
        proto.get_status("a1").last_heartbeat = time.time() - 10
        dead = proto.detect_dead_agents()
        assert len(dead) == 1
        assert dead[0].agent_id == "a1"
        assert dead[0].state == AgentState.DEAD
        assert dead[0].error_message is not None

    def test_detect_dead_agents_skips_already_dead(self):
        proto = self._make_protocol(dead_threshold=1)
        proto.register("a1", "frontend")
        proto.get_status("a1").last_heartbeat = time.time() - 10
        proto.detect_dead_agents()  # marks dead
        dead_again = proto.detect_dead_agents()  # should not re-detect
        assert len(dead_again) == 0

    def test_detect_dead_agents_skips_healthy(self):
        proto = self._make_protocol(dead_threshold=120)
        proto.register("a1", "frontend")
        dead = proto.detect_dead_agents()
        assert len(dead) == 0

    def test_get_idle_agents(self):
        proto = self._make_protocol()
        proto.register("a1", "frontend")
        proto.register("a2", "backend")
        proto.transition("a1", AgentState.IDLE)
        proto.transition("a2", AgentState.WORKING, task_id="t1")
        idle = proto.get_idle_agents()
        assert len(idle) == 1
        assert idle[0].agent_id == "a1"

    def test_get_working_agents(self):
        proto = self._make_protocol()
        proto.register("a1", "frontend")
        proto.register("a2", "backend")
        proto.transition("a1", AgentState.IDLE)
        proto.transition("a2", AgentState.WORKING, task_id="t1")
        working = proto.get_working_agents()
        assert len(working) == 1
        assert working[0].agent_id == "a2"

    def test_get_all_statuses(self):
        proto = self._make_protocol()
        proto.register("a1", "frontend")
        proto.register("a2", "backend")
        assert len(proto.get_all_statuses()) == 2

    def test_remove(self):
        proto = self._make_protocol()
        proto.register("a1", "frontend")
        proto.remove("a1")
        assert proto.get_status("a1") is None
        assert len(proto.get_all_statuses()) == 0

    def test_remove_nonexistent(self):
        proto = self._make_protocol()
        proto.remove("nonexistent")  # Should not raise

    def test_dead_agent_callback_fires(self):
        callback = MagicMock()
        proto = self._make_protocol(dead_threshold=1)
        proto.on_agent_dead(callback)
        proto.register("a1", "frontend")
        proto.transition("a1", AgentState.WORKING, task_id="t1")
        proto.get_status("a1").last_heartbeat = time.time() - 10
        proto.detect_dead_agents()
        callback.assert_called_once_with("a1", "t1")

    def test_idle_callback_fires(self):
        callback = MagicMock()
        proto = self._make_protocol()
        proto.on_agent_idle(callback)
        proto.register("a1", "frontend")
        proto.transition("a1", AgentState.IDLE)
        callback.assert_called_once_with("a1")

    def test_idle_callback_error_is_logged(self):
        callback = MagicMock(side_effect=RuntimeError("boom"))
        proto = self._make_protocol()
        proto.on_agent_idle(callback)
        proto.register("a1", "frontend")
        # Should not raise
        proto.transition("a1", AgentState.IDLE)
        callback.assert_called_once()

    def test_dead_callback_error_is_logged(self):
        callback = MagicMock(side_effect=RuntimeError("boom"))
        proto = self._make_protocol(dead_threshold=1)
        proto.on_agent_dead(callback)
        proto.register("a1", "frontend")
        proto.get_status("a1").last_heartbeat = time.time() - 10
        # Should not raise
        proto.detect_dead_agents()
        callback.assert_called_once()
