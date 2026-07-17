"""Procedural memory helpers that emulate reusable skills over BrainSentry."""

from typing import Any

from squadx_client.config import settings
from squadx_client.memory.client import BrainSentryClient
from squadx_client.memory.policy import MemoryScopeContext


class ProceduralMemoryManager:
    """Reads and writes structured procedural memories for repeatable workflows."""

    def __init__(self, client: BrainSentryClient):
        self.client = client

    async def get_relevant_procedures(
        self,
        prompt: str,
        scope: MemoryScopeContext,
        limit: int | None = None,
    ) -> str:
        if not self.client.enabled or not getattr(settings, "brainsentry_enable_procedural_memory", True):
            return ""

        query = self._build_query(prompt, scope)
        results = await self.client.search_memories(
            query,
            limit=int(limit or getattr(settings, "brainsentry_procedural_limit", 5) or 5),
        )
        procedures = [item for item in results if self._is_procedural(item) and self._matches_scope(item, scope)]
        if not procedures:
            return ""

        lines = ["<procedural_memory>", "Relevant procedures from prior executions:"]
        for item in procedures[: limit or getattr(settings, "brainsentry_procedural_limit", 5)]:
            summary = item.get("summary") or item.get("content") or ""
            summary_text = str(summary).strip()
            if summary_text:
                lines.append(f"- {summary_text}")
        lines.append("</procedural_memory>")
        return "\n".join(lines)

    async def record_procedure(
        self,
        title: str,
        summary: str,
        scope: MemoryScopeContext,
        steps: list[str] | None = None,
        files_modified: list[str] | None = None,
    ) -> dict | None:
        if not self.client.enabled or not getattr(settings, "brainsentry_enable_procedural_memory", True):
            return None

        procedure_lines = [f"Procedure: {title}", f"Summary: {summary}"]
        if steps:
            procedure_lines.append("Steps:")
            procedure_lines.extend(f"- {step}" for step in steps if step)
        if files_modified:
            procedure_lines.append(f"Files touched: {', '.join(files_modified[:20])}")

        return await self.client.create_memory(
            content="\n".join(procedure_lines),
            summary=f"{title}: {summary[:160]}",
            category="PATTERN",
            importance="IMPORTANT",
            memory_type="PROCEDURAL",
            tags=["procedure", *scope.to_tags()],
            metadata={
                **scope.to_metadata(),
                "steps": steps or [],
                "filesModified": files_modified or [],
            },
            source_type="squadx-procedure",
            source_reference=f"execution:{scope.execution_id}" if scope.execution_id else None,
            created_by=scope.agent_id or scope.execution_id,
        )

    def _build_query(self, prompt: str, scope: MemoryScopeContext) -> str:
        parts = [prompt]
        if scope.agent_type:
            parts.append(f"agent type {scope.agent_type}")
        if scope.project_id:
            parts.append(f"project {scope.project_id}")
        if scope.organization_id:
            parts.append(f"organization {scope.organization_id}")
        return " ".join(part for part in parts if part)

    def _is_procedural(self, item: dict[str, Any]) -> bool:
        memory_type = str(item.get("memoryType") or "").upper()
        category = str(item.get("category") or "").upper()
        tags = [str(tag).lower() for tag in item.get("tags") or []]
        return (
            memory_type == "PROCEDURAL"
            or category in {"PATTERN", "ANTIPATTERN"}
            or "procedure" in tags
        )

    def _matches_scope(self, item: dict[str, Any], scope: MemoryScopeContext) -> bool:
        metadata = item.get("metadata") or {}
        organization_id = _normalize(metadata.get("organizationId"))
        project_id = _normalize(metadata.get("projectId"))
        agent_id = _normalize(metadata.get("agentId"))
        if scope.organization_id and organization_id not in {None, scope.organization_id}:
            return False
        if scope.project_id and project_id not in {None, scope.project_id}:
            return False
        if scope.agent_id and agent_id not in {None, scope.agent_id}:
            return False
        return True


def _normalize(value: Any) -> str | None:
    if value is None:
        return None
    text = str(value).strip()
    return text or None
