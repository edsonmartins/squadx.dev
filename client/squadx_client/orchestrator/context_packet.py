"""Curated, auditable executor input (Context Packet — ADR-0007, RFC-0005).

Instead of dumping loosely-concatenated context into an agent prompt, the orchestrator assembles a
bounded ``ContextPacket`` (collect → classify → filter → preserve) that both the native agents and
the external CLI prefer when building their prompts. Mirrors opentag thread-runtime Delta 5.
"""

from __future__ import annotations

from dataclasses import asdict, dataclass, field
from typing import Any


@dataclass
class ContextFact:
    text: str
    source: str | None = None


@dataclass
class ContextSource:
    pointer: str
    role: str = "supporting"  # primary | supporting | background
    included: bool = True
    reason: str = ""


@dataclass
class ContextPacket:
    summary: str = ""
    intent: str = ""
    facts: list[ContextFact] = field(default_factory=list)
    sources: list[ContextSource] = field(default_factory=list)
    risks: list[str] = field(default_factory=list)
    exclusions: list[str] = field(default_factory=list)
    must_preserve: list[str] = field(default_factory=list)
    budget_tokens: int | None = None

    def render(self) -> str:
        """Render a bounded, prompt-ready block. Empty packets render to an empty string."""
        lines: list[str] = []
        if self.summary:
            lines += ["## Context summary", self.summary, ""]
        if self.intent:
            lines += ["## Intent", self.intent, ""]
        if self.facts:
            lines.append("## Facts")
            for fact in self.facts:
                suffix = f" ({fact.source})" if fact.source else ""
                lines.append(f"- {fact.text}{suffix}")
            lines.append("")
        if self.must_preserve:
            lines.append("## Must preserve")
            for item in self.must_preserve:
                lines.append(f"- {item}")
            lines.append("")
        if self.exclusions:
            lines.append("## Out of scope")
            for item in self.exclusions:
                lines.append(f"- {item}")
            lines.append("")
        return "\n".join(lines).strip()

    def to_dict(self) -> dict[str, Any]:
        return asdict(self)


def _subtask_field(subtask: Any, name: str) -> Any:
    if isinstance(subtask, dict):
        return subtask.get(name)
    return getattr(subtask, name, None)


def build_context_packet(context: dict[str, Any] | None) -> ContextPacket | None:
    """Assemble a ``ContextPacket`` from the orchestrator context dict.

    - ``main_task`` → summary + primary source;
    - ``acceptance_criteria`` → intent (the work must satisfy all of them);
    - ``reuse_map`` → a must-preserve directive (build on existing patterns);
    - ``completed_subtasks`` → supporting facts/sources;
    - ``exclusions`` / ``budget_tokens`` passed through when present.
    """
    if not context:
        return None

    main_task = context.get("main_task") or {}
    title = main_task.get("title") if isinstance(main_task, dict) else None
    description = main_task.get("description") if isinstance(main_task, dict) else None

    facts: list[ContextFact] = []
    sources: list[ContextSource] = []
    must_preserve: list[str] = []
    intent_parts: list[str] = []

    if title:
        sources.append(ContextSource(pointer=f"task:{title}", role="primary", reason="root task"))

    acceptance = context.get("acceptance_criteria") or []
    if acceptance:
        intent_parts.append("Satisfy ALL acceptance criteria:")
        intent_parts.extend(f"- {ac}" for ac in acceptance)

    reuse = context.get("reuse_map")
    if reuse:
        must_preserve.append(f"Build on existing patterns, do not reinvent: {reuse}")

    for subtask in context.get("completed_subtasks") or []:
        st_title = _subtask_field(subtask, "title")
        st_result = _subtask_field(subtask, "result")
        if st_title:
            facts.append(ContextFact(text=f"{st_title}: {st_result or 'done'}", source="completed_subtask"))
            sources.append(ContextSource(pointer=f"subtask:{st_title}", role="supporting", reason="prior work"))

    return ContextPacket(
        summary=description or title or "",
        intent="\n".join(intent_parts),
        facts=facts,
        sources=sources,
        must_preserve=must_preserve,
        exclusions=list(context.get("exclusions") or []),
        budget_tokens=context.get("budget_tokens"),
    )
