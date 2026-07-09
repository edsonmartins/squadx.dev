"""Tests for squadx_client.agents.factory module."""

from unittest.mock import MagicMock, patch

import pytest

from squadx_client.agents.base import BaseAgent
from squadx_client.agents.external_cli_agent import ExternalCliAgent
from squadx_client.agents.factory import (
    BackendAgent,
    CoordinatorAgent,
    DatabaseAgent,
    DevOpsAgent,
    FrontendAgent,
    FullstackAgent,
    QAAgent,
    create_agent,
)


@pytest.fixture(autouse=True)
def _mock_llm():
    """Patch get_coding_llm so agents don't need real API keys."""
    with patch("squadx_client.agents.base.get_coding_llm", return_value=MagicMock()):
        yield


class TestCreateAgentTypes:
    """Test that create_agent returns the correct class for each type."""

    @pytest.mark.parametrize(
        "agent_type,expected_class",
        [
            ("frontend", FrontendAgent),
            ("backend", BackendAgent),
            ("fullstack", FullstackAgent),
            ("devops", DevOpsAgent),
            ("qa", QAAgent),
            ("coordinator", CoordinatorAgent),
            ("database", DatabaseAgent),
        ],
    )
    def test_create_agent_returns_correct_class(self, agent_type, expected_class):
        agent = create_agent(agent_type)
        assert isinstance(agent, expected_class)

    @pytest.mark.parametrize(
        "agent_type,expected_class",
        [
            ("frontend", FrontendAgent),
            ("backend", BackendAgent),
            ("fullstack", FullstackAgent),
            ("devops", DevOpsAgent),
            ("qa", QAAgent),
            ("coordinator", CoordinatorAgent),
            ("database", DatabaseAgent),
        ],
    )
    def test_agent_is_base_agent(self, agent_type, expected_class):
        agent = create_agent(agent_type)
        assert isinstance(agent, BaseAgent)

    def test_unknown_type_raises_error(self):
        with pytest.raises(ValueError, match="Unknown agent type"):
            create_agent("unknown_type")


class TestAgentAttributes:
    """Test that agents have correct attributes."""

    @pytest.mark.parametrize(
        "agent_type",
        ["frontend", "backend", "fullstack", "devops", "qa", "coordinator", "database"],
    )
    def test_agent_type_attribute(self, agent_type):
        agent = create_agent(agent_type)
        assert agent.agent_type == agent_type

    @pytest.mark.parametrize(
        "agent_type",
        ["frontend", "backend", "fullstack", "devops", "qa", "coordinator", "database"],
    )
    def test_agent_has_non_empty_system_prompt(self, agent_type):
        agent = create_agent(agent_type)
        prompt = agent.get_system_prompt()
        assert isinstance(prompt, str)
        assert len(prompt) > 50

    def test_agent_created_without_sandbox_has_no_tools(self):
        agent = create_agent("backend")
        assert agent.tools == []
        assert agent.sandbox is None

    def test_agent_created_with_sandbox(self, mock_sandbox):
        with patch("squadx_client.agents.tools.create_sandbox_tools", return_value=[MagicMock()]):
            agent = create_agent("backend", sandbox=mock_sandbox)
        assert agent.sandbox is mock_sandbox
        assert len(agent.tools) == 1


class TestAgentPromptContent:
    """Test that system prompts contain expected domain keywords."""

    def test_frontend_prompt_mentions_react(self):
        agent = create_agent("frontend")
        assert "React" in agent.get_system_prompt()

    def test_backend_prompt_mentions_api(self):
        agent = create_agent("backend")
        assert "API" in agent.get_system_prompt()

    def test_devops_prompt_mentions_docker(self):
        agent = create_agent("devops")
        assert "Docker" in agent.get_system_prompt()

    def test_qa_prompt_mentions_testing(self):
        agent = create_agent("qa")
        assert "test" in agent.get_system_prompt().lower()

    def test_database_prompt_mentions_postgresql(self):
        agent = create_agent("database")
        assert "PostgreSQL" in agent.get_system_prompt()

    def test_coordinator_prompt_mentions_subtasks(self):
        agent = create_agent("coordinator")
        assert "subtask" in agent.get_system_prompt().lower()


class TestExternalCliProviderRouting:
    """All EXTERNAL_CLI providers route to ExternalCliAgent; the new
    AIDER and OPENCODE providers follow the same path as the original three."""

    @pytest.mark.parametrize(
        "provider",
        ["CLAUDE_CODE", "CODEX", "GEMINI_CLI", "AIDER", "OPENCODE"],
    )
    def test_factory_routes_to_external_cli(self, provider):
        agent = create_agent(
            "external_cli", runtime_kind="EXTERNAL_CLI", cli_provider=provider
        )
        assert isinstance(agent, ExternalCliAgent)
        assert agent.provider == provider
