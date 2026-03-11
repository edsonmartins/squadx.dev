"""Tests for BaseAgent class."""

import pytest
from unittest.mock import MagicMock, AsyncMock, patch
from types import SimpleNamespace

from squadx_client.agents.base import BaseAgent


class ConcreteAgent(BaseAgent):
    """Concrete implementation of BaseAgent for testing."""

    agent_type = "test_agent"
    system_prompt = "You are a test agent."

    def get_system_prompt(self) -> str:
        return self.system_prompt


@pytest.fixture
def mock_llm():
    llm = MagicMock()
    llm.ainvoke = AsyncMock()
    return llm


@pytest.fixture
def agent(mock_llm):
    with patch("squadx_client.agents.base.get_coding_llm", return_value=mock_llm):
        return ConcreteAgent()


class TestBaseAgentInit:
    def test_agent_type_set_correctly(self, agent):
        assert agent.agent_type == "test_agent"

    def test_sandbox_defaults_to_none(self, agent):
        assert agent.sandbox is None

    def test_tools_default_to_empty_list(self, agent):
        assert agent.tools == []

    def test_llm_assigned(self, agent, mock_llm):
        assert agent.llm is mock_llm


class TestGetSystemPrompt:
    def test_returns_non_empty_string(self, agent):
        result = agent.get_system_prompt()
        assert isinstance(result, str)
        assert len(result) > 0

    def test_returns_expected_prompt(self, agent):
        assert agent.get_system_prompt() == "You are a test agent."


class TestBuildContextString:
    def test_empty_context_returns_empty_string(self, agent):
        assert agent._build_context_string(None) == ""

    def test_empty_dict_returns_empty_string(self, agent):
        assert agent._build_context_string({}) == ""

    def test_main_task_included(self, agent):
        context = {"main_task": {"title": "Build API"}}
        result = agent._build_context_string(context)
        assert "Main task: Build API" in result

    def test_main_task_missing_title(self, agent):
        context = {"main_task": {}}
        result = agent._build_context_string(context)
        assert "Main task: N/A" in result

    def test_completed_subtasks_included(self, agent):
        subtask = SimpleNamespace(title="Setup DB", result="Done")
        context = {"completed_subtasks": [subtask]}
        result = agent._build_context_string(context)
        assert "Already completed:" in result
        assert "Setup DB: Done" in result

    def test_main_task_and_subtasks_combined(self, agent):
        subtask = SimpleNamespace(title="Init project", result="OK")
        context = {
            "main_task": {"title": "Full Stack App"},
            "completed_subtasks": [subtask],
        }
        result = agent._build_context_string(context)
        assert "Main task: Full Stack App" in result
        assert "Init project: OK" in result


class TestExtractFiles:
    def test_no_files_section_returns_empty(self, agent):
        output = "Some output without file sections."
        assert agent._extract_files(output) == []

    def test_extracts_from_files_modified(self, agent):
        output = (
            "## Implementation\nDid stuff\n"
            "## Files Modified\n"
            "- src/app.py\n"
            "- src/utils.py\n"
            "## Summary\nAll done"
        )
        result = agent._extract_files(output)
        assert result == ["src/app.py", "src/utils.py"]

    def test_extracts_from_files_created(self, agent):
        output = (
            "## Files Created\n"
            "- new_file.py\n"
        )
        result = agent._extract_files(output)
        assert result == ["new_file.py"]

    def test_stops_at_next_heading(self, agent):
        output = (
            "## Files Modified\n"
            "- file1.py\n"
            "## Summary\n"
            "- not_a_file.py\n"
        )
        result = agent._extract_files(output)
        assert result == ["file1.py"]

    def test_ignores_non_dash_lines(self, agent):
        output = (
            "## Files Modified\n"
            "some text\n"
            "- real_file.py\n"
        )
        result = agent._extract_files(output)
        assert result == ["real_file.py"]


class TestEstimateCost:
    def test_zero_tokens_zero_cost(self, agent):
        assert agent._estimate_cost(0, 0) == 0.0

    def test_cost_calculation(self, agent):
        cost = agent._estimate_cost(1000, 500)
        expected = 1000 * 0.00001 + 500 * 0.00003
        assert cost == pytest.approx(expected)

    def test_output_tokens_cost_more(self, agent):
        input_only = agent._estimate_cost(1000, 0)
        output_only = agent._estimate_cost(0, 1000)
        assert output_only > input_only


class TestExecuteSimple:
    @pytest.mark.asyncio
    async def test_execute_calls_llm(self, agent, mock_llm):
        mock_response = MagicMock()
        mock_response.content = (
            "## Implementation\nDone\n"
            "## Files Modified\n- app.py\n"
            "## Summary\nCreated app"
        )
        mock_llm.ainvoke = AsyncMock(return_value=mock_response)

        result = await agent.execute("Test Task", "Do something")

        assert mock_llm.ainvoke.called
        assert result["output"] == mock_response.content
        assert "app.py" in result["files_modified"]
        assert "input_tokens" in result
        assert "output_tokens" in result
        assert "cost" in result
