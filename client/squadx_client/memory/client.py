"""BrainSentry REST API client for agent memory operations."""

import structlog
import httpx

from squadx_client.config import settings

logger = structlog.get_logger()


class BrainSentryClient:
    """Client for BrainSentry agent memory system.

    Provides methods for prompt interception, memory CRUD,
    and execution session lifecycle management.
    """

    def __init__(self, base_url: str | None = None, api_key: str | None = None, tenant_id: str | None = None):
        self.base_url = (base_url or settings.brainsentry_url or "").rstrip("/")
        self.api_key = api_key or settings.brainsentry_api_key
        self.tenant_id = tenant_id or settings.brainsentry_tenant_id
        self._client: httpx.AsyncClient | None = None

    @property
    def enabled(self) -> bool:
        return bool(self.base_url and self.api_key)

    async def __aenter__(self):
        return self

    async def __aexit__(self, exc_type, exc_val, exc_tb):
        await self.close()
        return False

    async def _get_client(self) -> httpx.AsyncClient:
        if self._client is None:
            self._client = httpx.AsyncClient(
                base_url=self.base_url,
                headers={
                    "Authorization": f"Bearer {self.api_key}",
                    "X-Tenant-ID": self.tenant_id,
                    "Content-Type": "application/json",
                },
                timeout=30.0,
            )
        return self._client

    async def _request_with_retry(self, method: str, url: str, **kwargs):
        """Make HTTP request with retry on transient errors."""
        client = await self._get_client()
        last_error = None
        for attempt in range(3):
            try:
                if method == "post":
                    response = await client.post(url, **kwargs)
                elif method == "get":
                    response = await client.get(url, **kwargs)
                else:
                    raise ValueError(f"Unsupported method: {method}")
                response.raise_for_status()
                return response
            except httpx.HTTPStatusError:
                raise  # Don't retry client errors (4xx)
            except Exception as e:
                last_error = e
                if attempt < 2:
                    import asyncio
                    await asyncio.sleep(0.5 * (attempt + 1))
                    logger.debug("retrying_request", url=url, attempt=attempt + 1)
        raise last_error

    async def intercept_prompt(self, prompt: str, session_id: str | None = None) -> str:
        """Send prompt to BrainSentry for context enrichment.

        Returns the enriched prompt with relevant memories prepended.
        Falls back to original prompt if BrainSentry is unavailable.
        """
        if not self.enabled:
            return prompt

        try:
            payload = {"prompt": prompt}
            if session_id:
                payload["sessionId"] = session_id

            response = await self._request_with_retry("post", "/api/v1/intercept", json=payload)

            data = response.json()
            enriched = data.get("enrichedPrompt", prompt)
            memories_used = data.get("memoriesUsed", 0)

            if memories_used > 0:
                logger.info("prompt_enriched", memories_used=memories_used)

            return enriched
        except Exception as e:
            logger.warning("brainsentry_intercept_failed", error=str(e))
            return prompt  # graceful fallback

    async def create_memory(
        self,
        content: str,
        category: str = "KNOWLEDGE",
        importance: str = "MINOR",
        memory_type: str = "semantic",
        tags: list[str] | None = None,
        metadata: dict | None = None,
    ) -> dict | None:
        """Create a new memory in BrainSentry."""
        if not self.enabled:
            return None

        try:
            payload = {
                "content": content,
                "category": category,
                "importance": importance,
                "type": memory_type,
                "tags": tags or [],
                "metadata": metadata or {},
            }

            response = await self._request_with_retry("post", "/api/v1/memories", json=payload)
            return response.json()
        except Exception as e:
            logger.warning("brainsentry_create_memory_failed", error=str(e))
            return None

    async def search_memories(self, query: str, limit: int = 10) -> list[dict]:
        """Search for relevant memories."""
        if not self.enabled:
            return []

        try:
            response = await self._request_with_retry(
                "post", "/api/v1/memories/search",
                json={"query": query, "limit": limit},
            )
            data = response.json()
            return data.get("memories", [])
        except Exception as e:
            logger.warning("brainsentry_search_failed", error=str(e))
            return []

    async def start_session(self, execution_id: str, task_id: str | None = None, agent_id: str | None = None) -> str | None:
        """Start a BrainSentry session for an execution."""
        if not self.enabled:
            return None

        try:
            response = await self._request_with_retry(
                "post", "/api/v1/integration/execution/start",
                json={
                    "executionId": execution_id,
                    "taskId": task_id,
                    "agentId": agent_id,
                },
            )
            data = response.json()
            session_id = data.get("sessionId")
            logger.info("brainsentry_session_started", session_id=session_id)
            return session_id
        except Exception as e:
            logger.warning("brainsentry_start_session_failed", error=str(e))
            return None

    async def end_session(self, session_id: str, status: str = "completed", summary: str = "") -> None:
        """End a BrainSentry session, triggering cross-session analysis."""
        if not self.enabled or not session_id:
            return

        try:
            await self._request_with_retry(
                "post", "/api/v1/integration/execution/end",
                json={
                    "sessionId": session_id,
                    "status": status,
                    "summary": summary,
                },
            )
            logger.info("brainsentry_session_ended", session_id=session_id)
        except Exception as e:
            logger.warning("brainsentry_end_session_failed", error=str(e))

    async def close(self) -> None:
        if self._client:
            await self._client.aclose()
            self._client = None
