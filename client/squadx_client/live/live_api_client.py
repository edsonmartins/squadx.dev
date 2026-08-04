"""SquadX Live REST/SSE client for the VNC→WebRTC publisher."""

from __future__ import annotations

import asyncio
import base64
import hashlib
import hmac
import json
import logging
import time
from collections.abc import Callable
from dataclasses import dataclass
from typing import Any

import httpx

from squadx_client.config import settings
logger = logging.getLogger(__name__)


@dataclass
class RealtimeMessage:
    """Signal received from the SquadX Live SSE stream."""

    event: str
    payload: dict[str, Any]
    channel: str


def _b64url(value: bytes) -> str:
    return base64.urlsafe_b64encode(value).rstrip(b"=").decode()


def _service_token(secret: str) -> str:
    now = int(time.time())
    header = _b64url(json.dumps({"alg": "HS256", "typ": "JWT"}).encode())
    payload = _b64url(
        json.dumps(
            {
                "iss": "squadx-client",
                "aud": "squadx-live",
                "sub": "service",
                "iat": now,
                "exp": now + 300,
            },
            separators=(",", ":"),
        ).encode()
    )
    signing_input = f"{header}.{payload}"
    signature = hmac.new(
        secret.encode(), signing_input.encode(), hashlib.sha256
    ).digest()
    return f"{signing_input}.{_b64url(signature)}"


class SquadxLiveApiClient:
    """Implements the subset of the legacy signaling client used by sessions."""

    def __init__(
        self,
        base_url: str | None = None,
        service_secret: str | None = None,
    ) -> None:
        self.base_url = (base_url or settings.squadx_live_url or "").rstrip("/")
        self.service_secret = (
            service_secret or settings.squadx_service_secret or ""
        )
        if not self.base_url:
            raise ValueError("SQUADX_LIVE_URL is required")
        if len(self.service_secret) < 32:
            raise ValueError("SQUADX_SERVICE_SECRET must contain at least 32 chars")
        self._http = httpx.AsyncClient(base_url=self.base_url, timeout=15)
        self._subscriptions: dict[str, asyncio.Task[None]] = {}
        self._session_ids_by_code: dict[str, str] = {}

    def _headers(self) -> dict[str, str]:
        return {"Authorization": f"Bearer {_service_token(self.service_secret)}"}

    async def get_session_by_task(self, task_id: int) -> dict[str, Any] | None:
        # POST creation is idempotent on the Live boundary, so no separate
        # lookup (and no unauthenticated task enumeration endpoint) is needed.
        return None

    async def create_session(
        self,
        task_id: int,
        host_user_id: str | None = None,
        mode: str = "p2p",
        max_viewers: int = 25,
    ) -> dict[str, Any]:
        response = await self._http.post(
            "/api/integration/sessions",
            headers=self._headers(),
            json={
                "taskId": task_id,
                "hostUserId": host_user_id,
                "mode": mode,
                "maxViewers": max_viewers,
            },
        )
        response.raise_for_status()
        body = response.json()
        result = {
            "id": body["sessionId"],
            "join_code": body["joinCode"],
            "mode": body["mode"],
            "status": body["status"],
        }
        self._session_ids_by_code[result["join_code"]] = result["id"]
        return result

    async def update_session_status(
        self, session_id: str, status: str
    ) -> dict[str, Any]:
        response = await self._http.patch(
            f"/api/integration/sessions/{session_id}",
            headers=self._headers(),
            json={"status": status},
        )
        response.raise_for_status()
        return response.json()

    async def end_session(self, session_id: str) -> bool:
        response = await self._http.delete(
            f"/api/integration/sessions/{session_id}",
            headers=self._headers(),
        )
        response.raise_for_status()
        return True

    async def subscribe_to_session(
        self,
        join_code: str,
        on_message: Callable[[RealtimeMessage], None],
    ) -> None:
        session_id = self._session_ids_by_code[join_code]
        task = asyncio.create_task(
            self._consume_signals(session_id, join_code, on_message)
        )
        self._subscriptions[join_code] = task

    async def _consume_signals(
        self,
        session_id: str,
        join_code: str,
        on_message: Callable[[RealtimeMessage], None],
    ) -> None:
        while True:
            try:
                async with self._http.stream(
                    "GET",
                    f"/api/sessions/{session_id}/signal/stream",
                    params={"participantId": "host"},
                    headers=self._headers(),
                    timeout=None,
                ) as response:
                    response.raise_for_status()
                    event = "message"
                    data_lines: list[str] = []
                    async for line in response.aiter_lines():
                        if line.startswith("event:"):
                            event = line[6:].strip()
                        elif line.startswith("data:"):
                            data_lines.append(line[5:].strip())
                        elif not line:
                            if event == "signal" and data_lines:
                                payload = json.loads("\n".join(data_lines))
                                normalized = {
                                    "type": payload.get("type"),
                                    "sender_id": payload.get("senderId"),
                                    "target_id": payload.get("targetId"),
                                    "sdp": payload.get("sdp"),
                                    "candidate": payload.get("candidate"),
                                }
                                on_message(
                                    RealtimeMessage(
                                        event="signal",
                                        payload=normalized,
                                        channel=f"session:{session_id}",
                                    )
                                )
                            event = "message"
                            data_lines = []
            except asyncio.CancelledError:
                raise
            except Exception as exc:  # noqa: BLE001 - reconnect loop
                logger.warning("live_signal_stream_reconnecting error=%s", exc)
                await asyncio.sleep(1)

    async def unsubscribe_from_session(self, join_code: str) -> None:
        task = self._subscriptions.pop(join_code, None)
        if task:
            task.cancel()
            try:
                await task
            except asyncio.CancelledError:
                pass

    async def send_signal(
        self,
        join_code: str,
        signal_type: str,
        data: dict[str, Any],
        sender_id: str = "host",
        target_id: str | None = None,
    ) -> bool:
        session_id = self._session_ids_by_code[join_code]
        payload: dict[str, Any] = {
            "type": signal_type,
            "senderId": sender_id,
            "timestamp": int(time.time() * 1000),
        }
        if target_id:
            payload["targetId"] = target_id
        payload.update(data)
        response = await self._http.post(
            f"/api/sessions/{session_id}/signal",
            headers=self._headers(),
            json=payload,
        )
        response.raise_for_status()
        return True

    async def send_offer(
        self, join_code: str, sdp: str, target_id: str | None = None
    ) -> bool:
        return await self.send_signal(
            join_code, "offer", {"sdp": sdp}, target_id=target_id
        )

    async def send_answer(
        self, join_code: str, sdp: str, target_id: str | None = None
    ) -> bool:
        return await self.send_signal(
            join_code, "answer", {"sdp": sdp}, target_id=target_id
        )

    async def send_ice_candidate(
        self,
        join_code: str,
        candidate: dict[str, Any],
        target_id: str | None = None,
    ) -> bool:
        return await self.send_signal(
            join_code,
            "ice-candidate",
            {"candidate": candidate},
            target_id=target_id,
        )

    async def close(self) -> None:
        for join_code in list(self._subscriptions):
            await self.unsubscribe_from_session(join_code)
        await self._http.aclose()
