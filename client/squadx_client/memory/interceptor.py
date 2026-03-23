"""Prompt interceptor that enriches LLM prompts with BrainSentry context."""

import structlog

from squadx_client.memory.client import BrainSentryClient

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
        self._intercept_count = 0

    async def intercept(self, prompt: str) -> str:
        """Enrich a prompt with relevant memory context.

        Returns the original prompt if BrainSentry is disabled or unavailable.
        """
        if not self.client.enabled:
            return prompt

        enriched = await self.client.intercept_prompt(prompt, self.session_id)
        self._intercept_count += 1
        return enriched

    def set_session(self, session_id: str | None) -> None:
        self.session_id = session_id

    @property
    def intercept_count(self) -> int:
        return self._intercept_count
