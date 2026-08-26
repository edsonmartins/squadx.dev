import json

import httpx
import pytest

from squadx_client.mcp.workspace_client import WorkspaceApiClient


@pytest.mark.asyncio
async def test_search_code_uses_scoped_workspace_endpoint():
    calls = []

    def handler(request: httpx.Request) -> httpx.Response:
        calls.append(request)
        assert request.method == "POST"
        assert request.url.path == "/api/v1/workspace/tools/search_code"
        assert request.headers["authorization"] == "Bearer session-token"
        # httpx.Request não expõe .json(); o payload está em request.content (bytes JSON).
        assert json.loads(request.content) == {"query": "Spring", "limit": 5}
        return httpx.Response(200, json={"success": True, "data": {
            "provider": "native", "snapshot_id": "native:1:abc1234",
            "revision": "abc1234", "hits": [], "has_more": False
        }})

    client = WorkspaceApiClient("http://squadx", "session-token",
                                transport=httpx.MockTransport(handler))
    try:
        result = await client.search_code("Spring", 5)
        assert result["provider"] == "native"
        assert len(calls) == 1
    finally:
        await client.close()


@pytest.mark.asyncio
async def test_search_code_surfaces_workspace_errors():
    def handler(_: httpx.Request) -> httpx.Response:
        return httpx.Response(409, json={"success": False,
                                        "message": "E_CONFLICT: snapshot is not ready"})

    client = WorkspaceApiClient("http://squadx", "session-token",
                                transport=httpx.MockTransport(handler))
    try:
        with pytest.raises(RuntimeError, match="snapshot is not ready"):
            await client.search_code("Spring")
    finally:
        await client.close()
