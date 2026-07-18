"""BrainSentry REST API client for agent memory operations."""

import httpx
import structlog

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
        last_error: Exception | None = None
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
        raise last_error or RuntimeError("request failed without a captured error")

    async def intercept_prompt(
        self,
        prompt: str,
        session_id: str | None = None,
        user_id: str | None = None,
    ) -> str:
        """Send prompt to BrainSentry for context enrichment.

        Returns the enriched prompt with relevant memories prepended.
        Falls back to original prompt if BrainSentry is unavailable.
        """
        if not self.enabled:
            return prompt

        try:
            prompt_with_profile = prompt
            if user_id:
                profile = await self.get_profile(user_id)
                profile_context = self.format_profile_context(profile)
                if profile_context:
                    prompt_with_profile = f"{profile_context}\n\n{prompt}"

            payload = {"prompt": prompt_with_profile}
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

    async def get_profile(self, user_id: str) -> dict | None:
        """Fetch a generated BrainSentry profile for an identity."""
        if not self.enabled or not user_id:
            return None

        try:
            response = await self._request_with_retry("get", f"/api/v1/profile/{user_id}")
            return response.json()
        except Exception as e:
            logger.warning("brainsentry_get_profile_failed", error=str(e), user_id=user_id)
            return None

    def format_profile_context(self, profile: dict | None) -> str:
        """Format BrainSentry profile data into a prompt block."""
        if not profile:
            return ""

        static_profile = profile.get("staticProfile") or {}
        dynamic_profile = profile.get("dynamicProfile") or {}
        lines: list[str] = ["<brainsentry_profile>"]

        summary = static_profile.get("summary")
        if summary:
            lines.extend(["## Summary", str(summary), ""])

        facts = static_profile.get("facts") or []
        if facts:
            lines.append("## Facts")
            for fact in facts:
                key = fact.get("key")
                value = fact.get("value")
                if key and value:
                    lines.append(f"- {key}: {value}")
            lines.append("")

        preferences = static_profile.get("preferences") or []
        if preferences:
            lines.append("## Preferences")
            for pref in preferences:
                key = pref.get("key")
                value = pref.get("value")
                if key and value:
                    lines.append(f"- {key}: {value}")
            lines.append("")

        expertise = static_profile.get("expertise") or []
        if expertise:
            lines.append("## Expertise")
            lines.extend(f"- {item}" for item in expertise if item)
            lines.append("")

        recent_topics = dynamic_profile.get("recentTopics") or []
        if recent_topics:
            lines.append(f"Recent topics: {', '.join(str(topic) for topic in recent_topics if topic)}")

        current_context = dynamic_profile.get("currentContext")
        if current_context:
            lines.append(f"Current focus: {current_context}")

        active_tasks = dynamic_profile.get("activeTasks") or []
        if active_tasks:
            lines.append("Active tasks:")
            lines.extend(f"- {task}" for task in active_tasks if task)

        lines.append("</brainsentry_profile>")
        return "\n".join(lines).strip()

    async def create_memory(
        self,
        content: str,
        summary: str | None = None,
        category: str = "KNOWLEDGE",
        importance: str = "MINOR",
        memory_type: str = "SEMANTIC",
        tags: list[str] | None = None,
        metadata: dict | None = None,
        source_type: str | None = None,
        source_reference: str | None = None,
        created_by: str | None = None,
    ) -> dict | None:
        """Create a new memory in BrainSentry."""
        if not self.enabled:
            return None

        try:
            payload = {
                "content": content,
                "category": category,
                "importance": importance,
                "memoryType": memory_type,
                "tags": tags or [],
                "metadata": metadata or {},
            }
            if summary:
                payload["summary"] = summary
            if source_type:
                payload["sourceType"] = source_type
            if source_reference:
                payload["sourceReference"] = source_reference
            if created_by:
                payload["createdBy"] = created_by

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
                "post",
                "/api/v1/sessions",
                json={"userId": agent_id or f"execution-{execution_id}"},
            )
            data = response.json()
            session_id = data.get("id")
            if session_id:
                await self.create_memory(
                    content=f"Execution {execution_id} started.",
                    summary="Execution started",
                    category="ACTION",
                    importance="IMPORTANT",
                    memory_type="EPISODIC",
                    tags=[
                        "squadx",
                        "execution",
                        f"execution:{execution_id}",
                        *( [f"task:{task_id}"] if task_id else []),
                        *( [f"agent:{agent_id}"] if agent_id else []),
                        f"session:{session_id}",
                    ],
                    metadata={
                        "executionId": execution_id,
                        "taskId": task_id,
                        "agentId": agent_id,
                        "sessionId": session_id,
                        "status": "STARTED",
                    },
                    source_type="squadx-execution",
                    source_reference=f"execution:{execution_id}",
                    created_by=agent_id or f"execution-{execution_id}",
                )
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
            if summary:
                await self.create_memory(
                    content=f"Session {session_id} ended with status {status}. Summary: {summary}",
                    summary="Execution completed",
                    category="INSIGHT",
                    importance="IMPORTANT",
                    memory_type="EPISODIC",
                    tags=["squadx", "execution", f"session:{session_id}", f"status:{status.lower()}"],
                    metadata={"sessionId": session_id, "status": status},
                    source_type="squadx-execution",
                    source_reference=f"session:{session_id}",
                )

            await self._request_with_retry("post", f"/api/v1/session-cache/{session_id}/cognify?clear=true")
            await self._request_with_retry("post", "/api/v1/semantic/consolidate?minMemories=3")
            await self._request_with_retry("post", f"/api/v1/sessions/{session_id}/end")
            logger.info("brainsentry_session_ended", session_id=session_id)
        except Exception as e:
            logger.warning("brainsentry_end_session_failed", error=str(e))

    async def push_session_interaction(
        self,
        session_id: str,
        query: str,
        response: str,
        memory_ids: list[str] | None = None,
        metadata: dict[str, str] | None = None,
    ) -> None:
        """Push a temporary interaction into BrainSentry session cache."""
        if not self.enabled or not session_id:
            return

        try:
            await self._request_with_retry(
                "post",
                f"/api/v1/session-cache/{session_id}",
                json={
                    "query": query,
                    "response": response,
                    "memoryIds": memory_ids or [],
                    "metadata": metadata or {},
                },
            )
        except Exception as e:
            logger.warning("brainsentry_session_cache_push_failed", error=str(e), session_id=session_id)

    async def close(self) -> None:
        if self._client:
            await self._client.aclose()
            self._client = None
