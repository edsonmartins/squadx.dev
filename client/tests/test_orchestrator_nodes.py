"""Tests for squadx_client.orchestrator.nodes module."""

import json
from unittest.mock import AsyncMock, MagicMock, patch

import pytest
from langchain_core.messages import AIMessage

from squadx_client.orchestrator.state import (
    OrchestratorState,
    TaskPlan,
    SubTask,
    ExecutionMetrics,
)


@pytest.fixture
def base_state():
    """Create a base orchestrator state for testing."""
    return OrchestratorState(
        task_id=42,
        task={"title": "Build API", "description": "Create REST endpoints"},
    )


@pytest.fixture
def state_with_plan():
    """Create a state with a plan and subtasks."""
    subtask = SubTask(
        id="s1", title="Create endpoints",
        description="Build REST API", agent_type="backend",
    )
    plan = TaskPlan(
        analysis="Need REST API",
        approach="Use FastAPI",
        subtasks=[subtask],
        execution_order=["s1"],
    )
    state = OrchestratorState(
        task_id=42,
        task={"title": "Build API", "description": "Create REST endpoints"},
        plan=plan,
        messages=[AIMessage(content="Analysis done")],
    )
    return state


class TestAnalyzeTask:
    """Test analyze_task node."""

    @pytest.mark.asyncio
    async def test_analyze_returns_messages(self, base_state):
        mock_response = AIMessage(content=json.dumps({
            "analysis": "Simple task",
            "approach": "Direct implementation",
            "complexity": "low",
        }))
        mock_llm = AsyncMock()
        mock_llm.ainvoke = AsyncMock(return_value=mock_response)

        with patch("squadx_client.orchestrator.nodes.get_llm", return_value=mock_llm):
            from squadx_client.orchestrator.nodes import analyze_task

            result = await analyze_task(base_state)

        assert "messages" in result
        assert len(result["messages"]) == 1
        mock_llm.ainvoke.assert_awaited_once()

    @pytest.mark.asyncio
    async def test_analyze_handles_non_json_response(self, base_state):
        mock_response = AIMessage(content="This is a plain text analysis")
        mock_llm = AsyncMock()
        mock_llm.ainvoke = AsyncMock(return_value=mock_response)

        with patch("squadx_client.orchestrator.nodes.get_llm", return_value=mock_llm):
            from squadx_client.orchestrator.nodes import analyze_task

            result = await analyze_task(base_state)

        assert "messages" in result


class TestCreatePlan:
    """Test create_plan node."""

    @pytest.mark.asyncio
    async def test_creates_plan_from_json(self, base_state):
        base_state.messages = [AIMessage(content="Analysis result")]

        plan_json = json.dumps({
            "subtasks": [
                {"id": "s1", "title": "Build API", "description": "REST", "agent_type": "backend"}
            ],
            "execution_order": ["s1"],
            "parallel_groups": [],
        })
        mock_response = AIMessage(content=plan_json)
        mock_llm = AsyncMock()
        mock_llm.ainvoke = AsyncMock(return_value=mock_response)

        with patch("squadx_client.orchestrator.nodes.get_llm", return_value=mock_llm):
            from squadx_client.orchestrator.nodes import create_plan

            result = await create_plan(base_state)

        assert "plan" in result
        assert len(result["plan"].subtasks) == 1
        assert result["plan"].subtasks[0].agent_type == "backend"

    @pytest.mark.asyncio
    async def test_fallback_plan_on_invalid_json(self, base_state):
        base_state.messages = [AIMessage(content="Analysis result")]

        mock_response = AIMessage(content="Not valid JSON")
        mock_llm = AsyncMock()
        mock_llm.ainvoke = AsyncMock(return_value=mock_response)

        with patch("squadx_client.orchestrator.nodes.get_llm", return_value=mock_llm):
            from squadx_client.orchestrator.nodes import create_plan

            result = await create_plan(base_state)

        assert "plan" in result
        assert len(result["plan"].subtasks) == 1
        assert result["plan"].subtasks[0].agent_type == "fullstack"


class TestExecuteSubtask:
    """Test execute_subtask node."""

    @pytest.mark.asyncio
    async def test_returns_error_without_plan(self, base_state):
        from squadx_client.orchestrator.nodes import execute_subtask

        result = await execute_subtask(base_state)
        assert result == {"error": "No plan available"}

    @pytest.mark.asyncio
    async def test_returns_should_end_when_all_done(self, state_with_plan):
        state_with_plan.completed_subtasks = ["s1"]

        from squadx_client.orchestrator.nodes import execute_subtask

        result = await execute_subtask(state_with_plan)
        assert result == {"should_end": True}

    @pytest.mark.asyncio
    async def test_handles_execution_failure(self, state_with_plan):
        mock_agent = MagicMock()
        mock_agent.execute = AsyncMock(side_effect=RuntimeError("Agent crashed"))

        with (
            patch("squadx_client.orchestrator.nodes.create_agent", return_value=mock_agent),
            patch("squadx_client.orchestrator.nodes.create_agent_sandbox") as mock_sandbox_cls,
            patch("squadx_client.orchestrator.nodes.settings") as mock_settings,
        ):
            mock_settings.workspace_path = None
            mock_settings.enable_sandbox = False

            from squadx_client.orchestrator.nodes import execute_subtask

            result = await execute_subtask(state_with_plan)

        assert "s1" in result["failed_subtasks"]
        assert "metrics" in result


class TestReviewResults:
    """Test review_results node."""

    @pytest.mark.asyncio
    async def test_returns_error_without_plan(self, base_state):
        from squadx_client.orchestrator.nodes import review_results

        result = await review_results(base_state)
        assert result == {"error": "No plan available"}

    @pytest.mark.asyncio
    async def test_returns_final_result(self, state_with_plan):
        state_with_plan.completed_subtasks = ["s1"]
        state_with_plan.plan.subtasks[0].result = "Done"

        mock_response = AIMessage(content="All tasks completed successfully")
        mock_llm = AsyncMock()
        mock_llm.ainvoke = AsyncMock(return_value=mock_response)

        with patch("squadx_client.orchestrator.nodes.get_llm", return_value=mock_llm):
            from squadx_client.orchestrator.nodes import review_results

            result = await review_results(state_with_plan)

        assert result["final_result"] == "All tasks completed successfully"


class TestHandleError:
    """Test handle_error node."""

    @pytest.mark.asyncio
    async def test_sets_should_end_and_result(self, base_state):
        base_state.error = "Plan creation failed"

        from squadx_client.orchestrator.nodes import handle_error

        result = await handle_error(base_state)

        assert result["should_end"] is True
        assert "Plan creation failed" in result["final_result"]
