"""Memory scope and policy helpers for BrainSentry-backed agent learning."""

from dataclasses import dataclass
from typing import Any

from squadx_client.config import settings


@dataclass
class MemoryScopeContext:
    """Stable identifiers that define memory scope for a task execution."""

    organization_id: str | None = None
    project_id: str | None = None
    task_id: str | None = None
    agent_id: str | None = None
    execution_id: str | None = None
    agent_type: str | None = None

    @classmethod
    def from_context(cls, context: dict[str, Any] | None, agent_type: str | None = None) -> "MemoryScopeContext":
        if not context:
            return cls(agent_type=agent_type)

        main_task = context.get("main_task") or {}
        return cls(
            organization_id=_as_str(
                context.get("organization_id")
                or main_task.get("organization_id")
                or main_task.get("organizationId")
            ),
            project_id=_as_str(
                context.get("project_id")
                or main_task.get("project_id")
                or main_task.get("projectId")
            ),
            task_id=_as_str(
                context.get("task_id")
                or main_task.get("task_id")
                or main_task.get("id")
            ),
            agent_id=_as_str(
                context.get("assigned_agent_id")
                or context.get("agent_id")
                or main_task.get("assigned_agent_id")
                or main_task.get("agent_id")
            ),
            execution_id=_as_str(context.get("execution_id")),
            agent_type=agent_type or _as_str(context.get("agent_type")),
        )

    def to_tags(self) -> list[str]:
        tags = ["squadx", "memory-policy"]
        if self.organization_id:
            tags.append(f"organization:{self.organization_id}")
        if self.project_id:
            tags.append(f"project:{self.project_id}")
        if self.task_id:
            tags.append(f"task:{self.task_id}")
        if self.agent_id:
            tags.append(f"agent:{self.agent_id}")
        if self.execution_id:
            tags.append(f"execution:{self.execution_id}")
        if self.agent_type:
            tags.append(f"agent-type:{self.agent_type}")
        return tags

    def to_metadata(self) -> dict[str, str]:
        metadata: dict[str, str] = {}
        if self.organization_id:
            metadata["organizationId"] = self.organization_id
        if self.project_id:
            metadata["projectId"] = self.project_id
        if self.task_id:
            metadata["taskId"] = self.task_id
        if self.agent_id:
            metadata["agentId"] = self.agent_id
        if self.execution_id:
            metadata["executionId"] = self.execution_id
        if self.agent_type:
            metadata["agentType"] = self.agent_type
        metadata["scope"] = resolve_scope(self)
        return metadata


def resolve_scope(scope: MemoryScopeContext) -> str:
    configured = getattr(settings, "brainsentry_memory_scope", "adaptive")
    if configured != "adaptive":
        return configured
    if scope.organization_id and scope.project_id and scope.agent_id:
        return "project-agent"
    if scope.organization_id and scope.project_id:
        return "project"
    if scope.organization_id and scope.agent_id:
        return "organization-agent"
    if scope.organization_id:
        return "organization"
    if scope.agent_id:
        return "agent"
    return "execution"


def format_policy_context(scope: MemoryScopeContext) -> str:
    lines = ["<memory_policy>"]
    lines.append(f"scope={resolve_scope(scope)}")
    if scope.organization_id:
        lines.append(f"organization_id={scope.organization_id}")
    if scope.project_id:
        lines.append(f"project_id={scope.project_id}")
    if scope.task_id:
        lines.append(f"task_id={scope.task_id}")
    if scope.agent_id:
        lines.append(f"agent_id={scope.agent_id}")
    if scope.execution_id:
        lines.append(f"execution_id={scope.execution_id}")
    if scope.agent_type:
        lines.append(f"agent_type={scope.agent_type}")
    lines.append("Prefer procedures and learnings from the same scope when they are relevant.")
    lines.append("</memory_policy>")
    return "\n".join(lines)


def _as_str(value: Any) -> str | None:
    if value is None:
        return None
    text = str(value).strip()
    return text or None
