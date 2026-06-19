"""Tests for planning enrichment: acceptance criteria, REUSE map, depends_on, complexity."""

import json
from unittest.mock import AsyncMock, patch

import pytest
from langchain_core.messages import AIMessage

from squadx_client.agents.base import BaseAgent
from squadx_client.agents.factory import FrontendAgent, BackendAgent, CoordinatorAgent
from squadx_client.orchestrator.nodes import create_plan
from squadx_client.orchestrator.state import OrchestratorState


def _state() -> OrchestratorState:
    return OrchestratorState(
        task_id=42,
        task={"title": "Build API", "description": "Create REST endpoints"},
        messages=[AIMessage(content="analysis")],
    )


def _llm_returning(payload: dict) -> AsyncMock:
    llm = AsyncMock()
    llm.ainvoke = AsyncMock(return_value=AIMessage(content=json.dumps(payload)))
    return llm


class TestCreatePlanEnrichment:
    async def test_parses_acceptance_criteria_reuse_and_depends_on(self):
        payload = {
            "complexity": "high",
            "reuse": "extend services/UserService.java",
            "subtasks": [
                {"id": "s1", "title": "Endpoint", "description": "d", "agent_type": "backend",
                 "acceptance_criteria": ["AC1 returns 200", "AC2 validates input"], "depends_on": []},
                {"id": "s2", "title": "UI", "description": "d", "agent_type": "frontend",
                 "acceptance_criteria": ["AC1 renders list"], "depends_on": ["s1"]},
            ],
            "execution_order": ["s1", "s2"],
        }
        with patch("squadx_client.orchestrator.nodes.get_llm", return_value=_llm_returning(payload)):
            result = await create_plan(_state())

        plan = result["plan"]
        assert plan.reuse == "extend services/UserService.java"
        assert plan.subtasks[0].acceptance_criteria == ["AC1 returns 200", "AC2 validates input"]
        assert plan.subtasks[1].depends_on == ["s1"]
        # Planner's complexity wins over the analyzer's and is mapped to the gate scale.
        assert result["complexity"] == "risky"

    async def test_backward_compatible_without_new_fields(self):
        payload = {
            "subtasks": [{"id": "s1", "title": "T", "description": "d", "agent_type": "backend"}],
            "execution_order": ["s1"],
        }
        with patch("squadx_client.orchestrator.nodes.get_llm", return_value=_llm_returning(payload)):
            result = await create_plan(_state())

        plan = result["plan"]
        assert plan.subtasks[0].acceptance_criteria == []
        assert plan.subtasks[0].depends_on == []
        assert plan.reuse == ""
        assert "complexity" not in result  # nothing to refine → leave the analyzer's value


class TestContextRendering:
    def test_acceptance_criteria_and_reuse_reach_the_agent(self):
        # _build_context_string doesn't use self, so we avoid agent __init__ (LLM/clients).
        context = {
            "main_task": {"title": "Build API"},
            "acceptance_criteria": ["AC1 returns 200", "AC2 validates input"],
            "reuse_map": "extend UserService",
        }
        rendered = BaseAgent._build_context_string(object(), context)
        assert "AC1 returns 200" in rendered
        assert "AC2 validates input" in rendered
        assert "extend UserService" in rendered


class TestSpecialistDiscipline:
    @pytest.mark.parametrize("agent_cls", [FrontendAgent, BackendAgent])
    def test_prompt_carries_engineering_discipline(self, agent_cls):
        prompt = agent_cls.get_system_prompt(object())  # self unused
        assert "reproduce FIRST" in prompt
        assert "Verify before claiming done" in prompt
        assert "CURRENT library docs" in prompt          # library-docs-lookup guardrail
        assert "no AI self-attribution" in prompt        # no-self-attribution guardrail

    def test_coordinator_requires_acceptance_criteria_ids(self):
        prompt = CoordinatorAgent.get_system_prompt(object())
        assert "AC1" in prompt
        assert "trivial | standard | risky" in prompt
