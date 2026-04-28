"""Prompt interceptor that enriches LLM prompts with BrainSentry context."""

import structlog

from squadx_client.memory.client import BrainSentryClient
from squadx_client.memory.policy import MemoryScopeContext, format_policy_context
from squadx_client.memory.procedural import ProceduralMemoryManager

logger = structlog.get_logger()


class PromptInterceptor:
    """Hooks into the LangGraph pipeline to enrich prompts with agent memory.

    Before each LLM call, sends the prompt to BrainSentry's interception
    endpoint, which returns the prompt enriched with relevant memories,
    past decisions, patterns, and context.
    """

    def __init__(self, client: BrainSentryClient):
        self.client = client
        self.session_id: str | None = None
        self.user_id: str | None = None
        self.scope = MemoryScopeContext()
        self.procedural_memory = ProceduralMemoryManager(client)
        self._intercept_count = 0

    async def intercept(self, prompt: str) -> str:
        """Enrich a prompt with relevant memory context.

        Returns the original prompt if BrainSentry is disabled or unavailable.
        """
        if not self.client.enabled:
            return prompt

        enriched = await self.client.intercept_prompt(prompt, self.session_id, self.user_id)
        procedure_context = await self.procedural_memory.get_relevant_procedures(prompt, self.scope)
        policy_context = format_policy_context(self.scope)
        if procedure_context:
            enriched = f"{policy_context}\n\n{procedure_context}\n\n{enriched}"
        else:
            enriched = f"{policy_context}\n\n{enriched}"
        self._intercept_count += 1
        return enriched

    def set_session(self, session_id: str | None) -> None:
        self.session_id = session_id

    def set_user(self, user_id: str | None) -> None:
        self.user_id = user_id

    def set_scope(self, scope: MemoryScopeContext) -> None:
        self.scope = scope

    @property
    def intercept_count(self) -> int:
        return self._intercept_count
