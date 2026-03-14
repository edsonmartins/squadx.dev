"""Tests for squadx_client.orchestrator.graph module."""

from unittest.mock import patch, MagicMock, AsyncMock

import pytest

from squadx_client.orchestrator.state import (
    OrchestratorState,
    TaskPlan,
    SubTask,
)
from squadx_client.orchestrator.graph import should_continue


class TestShouldContinue:
    """Test the should_continue routing function."""

    def _make_state(self, **overrides):
        defaults = {
            "task_id": 1,
            "task": {"title": "Test"},
            "error": None,
            "should_end": False,
            "plan": None,
            "completed_subtasks": [],
            "failed_subtasks": [],
        }
        defaults.update(overrides)
        return OrchestratorState(**defaults)

    def test_returns_error_when_error_set(self):
        state = self._make_state(error="Something broke")
        assert should_continue(state) == "error"

    def test_returns_end_when_should_end_true(self):
        state = self._make_state(should_end=True)
        assert should_continue(state) == "end"

    def test_returns_error_when_no_plan(self):
        state = self._make_state(plan=None)
        assert should_continue(state) == "error"

    def test_returns_review_when_all_subtasks_completed(self):
        plan = TaskPlan(
            analysis="A",
            approach="B",
            subtasks=[SubTask(id="s1", title="T", description="D", agent_type="backend")],
            execution_order=["s1"],
        )
        state = self._make_state(plan=plan, completed_subtasks=["s1"])
        assert should_continue(state) == "review"

    def test_returns_review_when_all_subtasks_completed_or_failed(self):
        plan = TaskPlan(
            analysis="A",
            approach="B",
            subtasks=[
                SubTask(id="s1", title="T1", description="D", agent_type="backend"),
                SubTask(id="s2", title="T2", description="D", agent_type="frontend"),
            ],
            execution_order=["s1", "s2"],
        )
        state = self._make_state(plan=plan, completed_subtasks=["s1"], failed_subtasks=["s2"])
        assert should_continue(state) == "review"

    def test_returns_execute_when_pending_subtasks_remain(self):
        plan = TaskPlan(
            analysis="A",
            approach="B",
            subtasks=[
                SubTask(id="s1", title="T1", description="D", agent_type="backend"),
                SubTask(id="s2", title="T2", description="D", agent_type="frontend"),
            ],
            execution_order=["s1", "s2"],
        )
        state = self._make_state(plan=plan, completed_subtasks=["s1"])
        assert should_continue(state) == "execute"


class TestShouldContinueWithDict:
    """Test should_continue with dict-style state (LangGraph compat)."""

    def test_dict_state_error(self):
        state = {"error": "boom", "should_end": False, "plan": None,
                 "completed_subtasks": [], "failed_subtasks": []}
        assert should_continue(state) == "error"

    def test_dict_state_end(self):
        state = {"error": None, "should_end": True, "plan": None,
                 "completed_subtasks": [], "failed_subtasks": []}
        assert should_continue(state) == "end"

    def test_dict_state_no_plan(self):
        state = {"error": None, "should_end": False, "plan": None,
                 "completed_subtasks": [], "failed_subtasks": []}
        assert should_continue(state) == "error"


class TestCreateOrchestrator:
    """Test orchestrator graph construction."""

    @patch("squadx_client.orchestrator.graph.analyze_task", new_callable=AsyncMock)
    @patch("squadx_client.orchestrator.graph.create_plan", new_callable=AsyncMock)
    @patch("squadx_client.orchestrator.graph.execute_subtask", new_callable=AsyncMock)
    @patch("squadx_client.orchestrator.graph.review_results", new_callable=AsyncMock)
    @patch("squadx_client.orchestrator.graph.commit_changes", new_callable=AsyncMock)
    @patch("squadx_client.orchestrator.graph.handle_error", new_callable=AsyncMock)
    def test_graph_compiles_without_error(
        self, mock_error, mock_commit, mock_review,
        mock_execute, mock_plan, mock_analyze,
    ):
        from squadx_client.orchestrator.graph import create_orchestrator

        graph = create_orchestrator()
        assert graph is not None
