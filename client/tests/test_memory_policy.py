"""Tests for memory scope policy and procedural memory helpers."""

import asyncio

from squadx_client.memory.policy import MemoryScopeContext, format_policy_context, resolve_scope
from squadx_client.memory.procedural import ProceduralMemoryManager


class DummyClient:
    enabled = True

    async def search_memories(self, query: str, limit: int = 5):
        return [
            {
                "memoryType": "PROCEDURAL",
                "summary": "Reuse the Java controller test harness for scoped WebMvc tests",
                "metadata": {"organizationId": "1", "projectId": "2"},
                "tags": ["procedure"],
            }
        ]


def test_memory_scope_prefers_project_agent_when_available():
    scope = MemoryScopeContext(
        organization_id="1",
        project_id="2",
        agent_id="7",
        execution_id="9",
        agent_type="backend",
    )

    assert resolve_scope(scope) == "project-agent"
    assert "project_id=2" in format_policy_context(scope)


def test_procedural_memory_filters_by_scope():
    manager = ProceduralMemoryManager(DummyClient())
    scope = MemoryScopeContext(organization_id="1", project_id="2")

    block = asyncio.run(manager.get_relevant_procedures("webmvc tests", scope))

    assert "Relevant procedures" in block
    assert "Java controller test harness" in block
