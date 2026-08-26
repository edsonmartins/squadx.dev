"""Async client for the scoped SquadX workspace tool API."""

from typing import Any

import httpx


class WorkspaceApiError(RuntimeError):
    """Backend rejected a workspace tool call."""


class WorkspaceApiClient:
    """Thin async proxy over the workspace tool endpoints."""

    def __init__(self, base_url: str, session_token: str, timeout: float = 30.0,
                 transport: httpx.AsyncBaseTransport | None = None):
        self.base_url = base_url.rstrip("/")
        self.session_token = session_token
        self.timeout = timeout
        self._transport = transport
        self._client: httpx.AsyncClient | None = None

    async def _get_client(self) -> httpx.AsyncClient:
        if self._client is None:
            self._client = httpx.AsyncClient(
                base_url=self.base_url,
                headers={"Authorization": f"Bearer {self.session_token}",
                         "Content-Type": "application/json"},
                timeout=self.timeout,
                transport=self._transport,
            )
        return self._client

    async def close(self) -> None:
        if self._client is not None:
            await self._client.aclose()
            self._client = None

    async def _call(self, method: str, path: str, **kwargs: Any) -> Any:
        response = await (await self._get_client()).request(method, path, **kwargs)
        try:
            payload = response.json()
        except ValueError:
            payload = {}
        if response.status_code >= 400 or not payload.get("success", True):
            raise WorkspaceApiError(payload.get("message") or f"HTTP {response.status_code}")
        return payload.get("data")

    async def tools_list(self) -> Any:
        return await self._call("GET", "/api/v1/workspace/tools")

    async def get_change(self) -> Any:
        return await self._call("POST", "/api/v1/workspace/tools/get_change")

    async def get_tasks(self, assignee: str | None = None) -> Any:
        return await self._call("POST", "/api/v1/workspace/tools/get_tasks",
                                params={"assignee": assignee} if assignee else None)

    async def update_task_status(self, task_id: int, status: str,
                                 note: str | None = None) -> Any:
        body: dict[str, Any] = {"task_id": task_id, "status": status}
        if note:
            body["note"] = note
        return await self._call("POST", "/api/v1/workspace/tools/update_task_status", json=body)

    async def report_blocker(self, task_id: int, reason: str) -> Any:
        return await self._call("POST", "/api/v1/workspace/tools/report_blocker",
                                json={"task_id": task_id, "reason": reason})

    async def materialize_change(self) -> Any:
        return await self._call("POST", "/api/v1/workspace/tools/materialize_change")

    async def scaffold_tests(self, requirement_id: int | None = None) -> Any:
        return await self._call("POST", "/api/v1/workspace/tools/scaffold_tests",
                                json={"requirement_id": requirement_id} if requirement_id else {})

    async def search_code(self, query: str, limit: int = 20) -> Any:
        """Search the session's active, revision-pinned code snapshot."""
        return await self._call("POST", "/api/v1/workspace/tools/search_code",
                                json={"query": query, "limit": limit})
