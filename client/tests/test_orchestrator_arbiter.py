"""Tests for the arbiter loop-breaker, escalate node, and commit gate."""

import sys
import types
from unittest.mock import AsyncMock, MagicMock, patch

import pytest
from langchain_core.messages import AIMessage

from squadx_client.orchestrator.nodes import (
    arbiter,
    escalate,
    commit_changes,
    review_results,
    _map_complexity,
)
from squadx_client.orchestrator.graph import route_after_arbiter
from squadx_client.orchestrator.state import OrchestratorState, TaskPlan, SubTask, ExecutionMetrics


def _plan(*subtasks: SubTask) -> TaskPlan:
    subs = list(subtasks) or [SubTask(id="s1", title="Create endpoints", description="d", agent_type="backend")]
    return TaskPlan(
        analysis="a", approach="b", subtasks=subs,
        execution_order=[s.id for s in subs],
    )


def _state(**overrides) -> OrchestratorState:
    defaults = dict(
        task_id=42,
        task={"title": "Build API"},
        plan=_plan(),
        completed_subtasks=["s1"],
        failed_subtasks=[],
        review_findings=[],
        final_result="summary",
    )
    defaults.update(overrides)
    return OrchestratorState(**defaults)


BLOCKER = {"severity": "blocker", "subtask": "Create endpoints", "file": "api.py:10", "what": "no authz"}


class TestMapComplexity:
    @pytest.mark.parametrize("value,expected", [
        ("low", "trivial"), ("trivial", "trivial"),
        ("medium", "standard"), ("standard", "standard"),
        ("high", "risky"), ("risky", "risky"),
        (None, "standard"), ("garbage", "standard"),
    ])
    def test_mapping(self, value, expected):
        assert _map_complexity(value) == expected


class TestArbiterDecision:
    async def test_approves_when_clean(self):
        state = _state(review_findings=[], failed_subtasks=[])
        result = await arbiter(state)
        assert result["review_verdict"] == "approve"
        assert result["cycle_count"] == 1

    async def test_continue_when_blockers_under_cap(self):
        state = _state(review_findings=[BLOCKER], complexity="standard", cycle_count=0, max_cycles=3)
        result = await arbiter(state)
        assert result["review_verdict"] == "continue"
        # A fix subtask is injected and routed back to execute.
        assert len(result["plan"].subtasks) == 2
        fix = result["plan"].subtasks[-1]
        assert fix.is_fix is True
        assert fix.agent_type == "backend"  # routed to the flagged subtask's specialist
        assert result["plan"].execution_order[-1] == fix.id
        # Prior failures are now owned by the fix subtask.
        assert result["failed_subtasks"] == []

    async def test_escalates_at_cycle_cap(self):
        state = _state(review_findings=[BLOCKER], complexity="standard", cycle_count=2, max_cycles=3)
        result = await arbiter(state)
        assert result["review_verdict"] == "escalate"
        assert result["cycle_count"] == 3
        assert "did not converge" in result["escalation_reason"].lower()

    async def test_trivial_escalates_instead_of_looping(self):
        state = _state(review_findings=[BLOCKER], complexity="trivial", cycle_count=0)
        result = await arbiter(state)
        assert result["review_verdict"] == "escalate"

    async def test_escalates_when_over_cost_budget(self):
        state = _state(review_findings=[BLOCKER], complexity="standard", cycle_count=0, max_cycles=3,
                       cost_budget_usd=1.0, metrics=ExecutionMetrics(total_cost=1.5))
        result = await arbiter(state)
        assert result["review_verdict"] == "escalate"
        assert "cost ceiling" in result["escalation_reason"].lower()

    async def test_continues_when_under_cost_budget(self):
        state = _state(review_findings=[BLOCKER], complexity="standard", cycle_count=0,
                       cost_budget_usd=10.0, metrics=ExecutionMetrics(total_cost=1.5))
        result = await arbiter(state)
        assert result["review_verdict"] == "continue"

    async def test_no_budget_means_no_cost_brake(self):
        state = _state(review_findings=[BLOCKER], complexity="standard", cycle_count=0,
                       metrics=ExecutionMetrics(total_cost=999.0))
        result = await arbiter(state)
        assert result["review_verdict"] == "continue"

    async def test_failed_subtask_forces_work_even_without_findings(self):
        state = _state(review_findings=[], failed_subtasks=["s1"], complexity="standard", cycle_count=0)
        result = await arbiter(state)
        assert result["review_verdict"] == "continue"

    async def test_loop_never_exceeds_max_cycles(self):
        # Simulate the loop: blockers never clear → must terminate by escalation at the cap.
        state = _state(review_findings=[BLOCKER], complexity="standard", cycle_count=0, max_cycles=3)
        verdicts = []
        for _ in range(10):
            result = await arbiter(state)
            verdicts.append(result["review_verdict"])
            state = state.model_copy(update={
                "cycle_count": result["cycle_count"],
                "review_findings": [BLOCKER],  # still failing
                "failed_subtasks": [],
            })
            if result["review_verdict"] in ("approve", "escalate"):
                break
        assert verdicts[-1] == "escalate"
        assert verdicts.count("continue") <= 3
        assert state.cycle_count <= 3


class TestRouteAfterArbiter:
    @pytest.mark.parametrize("verdict,route", [
        ("continue", "execute"), ("approve", "commit"), ("escalate", "escalate"),
    ])
    def test_routes(self, verdict, route):
        assert route_after_arbiter(_state(review_verdict=verdict)) == route


class TestEscalateNode:
    async def test_sets_should_end_and_result_when_bridge_absent(self):
        # mcp.bridge is not present on main; the node must degrade to log-only.
        state = _state(escalation_reason="2 blockers remain")
        result = await escalate(state)
        assert result["should_end"] is True
        assert "2 blockers remain" in result["final_result"]

    async def test_reports_blocker_when_bridge_present(self):
        fake = types.ModuleType("squadx_client.mcp.bridge")
        fake.report_blocker = AsyncMock()
        with patch.dict(sys.modules, {"squadx_client.mcp.bridge": fake}):
            state = _state(escalation_reason="needs human")
            result = await escalate(state)
        fake.report_blocker.assert_awaited_once_with(42, "needs human")
        assert result["should_end"] is True


class TestCommitGate:
    async def test_blocks_commit_when_not_approved(self):
        state = _state(review_verdict="continue")
        with patch("squadx_client.orchestrator.nodes.GitManager") as git:
            result = await commit_changes(state)
        git.assert_not_called()
        assert result == {"should_end": True}

    async def test_allows_commit_when_approved(self):
        sub = SubTask(id="s1", title="t", description="d", agent_type="backend", files_modified=["api.py"])
        state = _state(plan=_plan(sub), completed_subtasks=["s1"], review_verdict="approve")
        git = MagicMock()
        git.commit.return_value = "abc123"
        with patch("squadx_client.orchestrator.nodes.GitManager", return_value=git):
            result = await commit_changes(state)
        git.create_branch.assert_called_once()
        assert result["git_commit"] == "abc123"

    async def test_commit_message_has_no_ai_self_attribution(self):
        sub = SubTask(id="s1", title="t", description="d", agent_type="backend", files_modified=["api.py"])
        state = _state(plan=_plan(sub), completed_subtasks=["s1"], review_verdict="approve")
        git = MagicMock()
        git.commit.return_value = "abc123"
        with patch("squadx_client.orchestrator.nodes.GitManager", return_value=git):
            await commit_changes(state)
        message = git.commit.call_args.args[1]
        lowered = message.lower()
        assert "ai agents" not in lowered
        assert "generated with" not in lowered
        assert "co-authored-by" not in lowered


class TestReviewProducesFindings:
    async def test_failed_subtask_becomes_blocker(self, ):
        sub = SubTask(id="s1", title="Create endpoints", description="d", agent_type="backend", error="boom")
        state = OrchestratorState(
            task_id=42, task={"title": "Build API"}, plan=_plan(sub),
            completed_subtasks=[], failed_subtasks=["s1"],
            messages=[AIMessage(content="x")],
        )
        mock_llm = AsyncMock()
        mock_llm.ainvoke = AsyncMock(return_value=AIMessage(content='{"summary": "ok", "risk": "low", "findings": []}'))
        with patch("squadx_client.orchestrator.nodes.get_llm", return_value=mock_llm):
            result = await review_results(state)
        sevs = [f["severity"] for f in result["review_findings"]]
        assert "blocker" in sevs
