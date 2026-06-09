"""Async HTTP client for the SquadX workspace tool API (RFC-0001).

Calls /api/v1/workspace/tools/* with the scoped session token (Bearer) and unwraps the
backend's ApiResponse envelope, raising WorkspaceApiError with the backend message on failure.
"""

import logging
from typing import Any, Optional

import httpx

logger = logging.getLogger(__name__)


class WorkspaceApiError(RuntimeError):
    """Backend rejected a workspace tool call (scope, validation, transition...)."""


class WorkspaceApiClient:
    """Thin async proxy over the workspace tool endpoints."""

    def __init__(
        self,
        base_url: str,
        session_token: str,
        timeout: float = 30.0,
        transport: Optional[httpx.AsyncBaseTransport] = None,
    ):
        self.base_url = base_url.rstrip("/")
        self.session_token = session_token
        self.timeout = timeout
        self._transport = transport  # injectable for tests (httpx.MockTransport)
        self._client: Optional[httpx.AsyncClient] = None

    async def _get_client(self) -> httpx.AsyncClient:
        if self._client is None:
            self._client = httpx.AsyncClient(
                base_url=self.base_url,
                headers={
                    "Authorization": f"Bearer {self.session_token}",
                    "Content-Type": "application/json",
                },
                timeout=self.timeout,
                transport=self._transport,
            )
        return self._client

    async def close(self) -> None:
        if self._client is not None:
            await self._client.aclose()
            self._client = None

    async def _call(self, method: str, path: str, **kwargs: Any) -> Any:
        client = await self._get_client()
        response = await client.request(method, path, **kwargs)
        try:
            payload = response.json()
        except ValueError:
            payload = {}
        if response.status_code >= 400 or not payload.get("success", True):
            message = payload.get("message") or f"HTTP {response.status_code}"
            raise WorkspaceApiError(message)
        return payload.get("data")

    # -- Tools (RFC-0001 §4) ------------------------------------------------

    async def tools_list(self) -> Any:
        return await self._call("GET", "/api/v1/workspace/tools")

    async def get_change(self) -> Any:
        return await self._call("POST", "/api/v1/workspace/tools/get_change")

    async def get_tasks(self, assignee: Optional[str] = None) -> Any:
        params = {"assignee": assignee} if assignee else None
        return await self._call("POST", "/api/v1/workspace/tools/get_tasks", params=params)

    async def update_task_status(
        self, task_id: int, status: str, note: Optional[str] = None
    ) -> Any:
        body: dict[str, Any] = {"task_id": task_id, "status": status}
        if note:
            body["note"] = note
        return await self._call("POST", "/api/v1/workspace/tools/update_task_status", json=body)

    async def report_blocker(self, task_id: int, reason: str) -> Any:
        return await self._call(
            "POST",
            "/api/v1/workspace/tools/report_blocker",
            json={"task_id": task_id, "reason": reason},
        )

    async def materialize_change(self) -> Any:
        return await self._call("POST", "/api/v1/workspace/tools/materialize_change")

    async def scaffold_tests(self, requirement_id: Optional[int] = None) -> Any:
        body = {"requirement_id": requirement_id} if requirement_id else {}
        return await self._call("POST", "/api/v1/workspace/tools/scaffold_tests", json=body)
