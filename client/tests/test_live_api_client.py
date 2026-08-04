"""SquadX Live REST/SSE publisher client contract."""

from __future__ import annotations

import json

import httpx
import pytest

from squadx_client.live.live_api_client import SquadxLiveApiClient

SECRET = "s" * 48


@pytest.mark.asyncio
async def test_create_and_signal_use_service_auth() -> None:
    requests: list[httpx.Request] = []

    def handler(request: httpx.Request) -> httpx.Response:
        requests.append(request)
        if request.url.path == "/api/integration/sessions":
            return httpx.Response(
                201,
                json={
                    "sessionId": "session-1",
                    "joinCode": "JOIN1234",
                    "mode": "p2p",
                    "status": "created",
                },
            )
        return httpx.Response(200, json={"data": {"sent": True}})

    client = SquadxLiveApiClient("https://live.example", SECRET)
    await client._http.aclose()
    client._http = httpx.AsyncClient(
        base_url="https://live.example",
        transport=httpx.MockTransport(handler),
    )

    session = await client.create_session(task_id=42)
    assert session["id"] == "session-1"
    assert session["join_code"] == "JOIN1234"

    assert await client.send_answer(
        "JOIN1234", "answer-sdp", target_id="viewer-1"
    )
    signal = json.loads(requests[1].content)
    assert signal == {
        "type": "answer",
        "senderId": "host",
        "timestamp": signal["timestamp"],
        "targetId": "viewer-1",
        "sdp": "answer-sdp",
    }
    assert all(
        request.headers["Authorization"].startswith("Bearer ")
        for request in requests
    )
    await client.close()


def test_requires_live_url_and_strong_secret() -> None:
    with pytest.raises(ValueError, match="SQUADX_LIVE_URL"):
        SquadxLiveApiClient("", SECRET)
    with pytest.raises(ValueError, match="at least 32"):
        SquadxLiveApiClient("https://live.example", "short")
