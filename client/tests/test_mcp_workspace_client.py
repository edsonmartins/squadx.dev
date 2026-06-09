"""Tests for the workspace MCP bridge HTTP client."""

import httpx
import pytest

from squadx_client.mcp.workspace_client import WorkspaceApiClient, WorkspaceApiError


def make_client(handler) -> WorkspaceApiClient:
    return WorkspaceApiClient(
        base_url="http://backend",
        session_token="tok123",
        transport=httpx.MockTransport(handler),
    )


class TestWorkspaceApiClient:
    async def test_get_change_unwraps_api_response_data(self):
        captured = {}

        def handler(request: httpx.Request) -> httpx.Response:
            captured["url"] = str(request.url)
            captured["auth"] = request.headers.get("Authorization")
            return httpx.Response(200, json={"success": True, "data": {"id": 5, "phase": "SPEC"}})

        client = make_client(handler)
        data = await client.get_change()

        assert data == {"id": 5, "phase": "SPEC"}
        assert captured["url"] == "http://backend/api/v1/workspace/tools/get_change"
        assert captured["auth"] == "Bearer tok123"

    async def test_get_tasks_passes_assignee_param(self):
        def handler(request: httpx.Request) -> httpx.Response:
            assert request.url.params.get("assignee") == "Backend Agent"
            return httpx.Response(200, json={"success": True, "data": []})

        client = make_client(handler)
        assert await client.get_tasks("Backend Agent") == []

    async def test_update_task_status_posts_body(self):
        def handler(request: httpx.Request) -> httpx.Response:
            assert b'"task_id": 42' in request.content or b'"task_id":42' in request.content
            assert b"em_curso" in request.content
            return httpx.Response(200, json={"success": True, "data": {"ok": True, "status": "EM_CURSO"}})

        client = make_client(handler)
        data = await client.update_task_status(42, "em_curso")
        assert data["status"] == "EM_CURSO"

    async def test_backend_error_raises_with_message(self):
        def handler(request: httpx.Request) -> httpx.Response:
            return httpx.Response(400, json={"success": False, "message": "E_SCOPE: task outside change"})

        client = make_client(handler)
        with pytest.raises(WorkspaceApiError, match="E_SCOPE"):
            await client.report_blocker(42, "waiting")

    async def test_http_error_without_body_raises(self):
        def handler(request: httpx.Request) -> httpx.Response:
            return httpx.Response(503, text="oops")

        client = make_client(handler)
        with pytest.raises(WorkspaceApiError, match="503"):
            await client.materialize_change()
