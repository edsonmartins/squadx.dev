"""Tests for squadx_client.websocket.stomp_client module."""

import json
from unittest.mock import AsyncMock, MagicMock, patch

import pytest

from squadx_client.websocket.stomp_client import StompClient, StompClientManager
from squadx_client.websocket.messages import StompCommand, StompFrame


class TestStompClientInit:
    """Test StompClient initialization."""

    def test_default_state(self):
        client = StompClient("ws://localhost:8080/ws")
        assert client.ws_url == "ws://localhost:8080/ws"
        assert client.token is None
        assert client.use_sockjs is True
        assert client.connected is False

    def test_with_token(self):
        client = StompClient("ws://localhost:8080/ws", token="jwt-token")
        assert client.token == "jwt-token"

    def test_sockjs_url_generation(self):
        client = StompClient("ws://localhost:8080/ws")
        url = client._generate_sockjs_url()
        assert url.startswith("ws://localhost:8080/ws/")
        assert "/websocket" in url
        # Pattern: /ws/{3 digits}/{8 chars}/websocket
        parts = url.replace("ws://localhost:8080/ws/", "").split("/")
        assert len(parts) == 3
        assert parts[2] == "websocket"


class TestSockJSUnwrap:
    """Test SockJS message unwrapping."""

    def test_unwrap_array_message(self):
        client = StompClient("ws://localhost:8080/ws")
        data = 'a["CONNECTED\\nversion:1.2\\n\\n\\x00"]'
        result = client._unwrap_sockjs(data)
        assert "CONNECTED" in result

    def test_unwrap_heartbeat(self):
        client = StompClient("ws://localhost:8080/ws")
        result = client._unwrap_sockjs("h")
        assert result == ""

    def test_unwrap_close_frame(self):
        client = StompClient("ws://localhost:8080/ws")
        result = client._unwrap_sockjs('c[1000,"Normal closure"]')
        assert result == ""

    def test_unwrap_empty(self):
        client = StompClient("ws://localhost:8080/ws")
        result = client._unwrap_sockjs("")
        assert result == ""

    def test_unwrap_passthrough(self):
        client = StompClient("ws://localhost:8080/ws")
        result = client._unwrap_sockjs("CONNECTED\nversion:1.2\n\n\x00")
        assert "CONNECTED" in result


class TestSubscribeAndSend:
    """Test subscribe and send with mocked connection."""

    @pytest.mark.asyncio
    async def test_subscribe_raises_when_not_connected(self):
        client = StompClient("ws://localhost:8080/ws")
        handler = AsyncMock()

        with pytest.raises(RuntimeError, match="Not connected"):
            await client.subscribe("/user/queue/tasks", handler)

    @pytest.mark.asyncio
    async def test_send_raises_when_not_connected(self):
        client = StompClient("ws://localhost:8080/ws")

        with pytest.raises(RuntimeError, match="Not connected"):
            await client.send("/app/tasks/status", {"status": "ok"})

    @pytest.mark.asyncio
    async def test_subscribe_stores_handler(self):
        client = StompClient("ws://localhost:8080/ws")
        client._connected = True
        client._ws = MagicMock()
        client._ws.send_str = AsyncMock()

        handler = AsyncMock()
        sub_id = await client.subscribe("/user/queue/tasks", handler)

        assert sub_id in client._subscriptions
        assert client._subscriptions[sub_id] is handler
        assert client._subscription_ids["/user/queue/tasks"] == sub_id

    @pytest.mark.asyncio
    async def test_send_json_body(self):
        client = StompClient("ws://localhost:8080/ws")
        client._connected = True
        client._ws = MagicMock()
        client._ws.send_str = AsyncMock()

        await client.send("/app/status", {"task_id": 1, "status": "done"})

        client._ws.send_str.assert_awaited_once()
        sent_data = client._ws.send_str.call_args[0][0]
        # SockJS wraps in JSON array
        unwrapped = json.loads(sent_data)[0]
        assert "SEND" in unwrapped
        assert "/app/status" in unwrapped

    @pytest.mark.asyncio
    async def test_unsubscribe(self):
        client = StompClient("ws://localhost:8080/ws")
        client._connected = True
        client._ws = MagicMock()
        client._ws.send_str = AsyncMock()

        handler = AsyncMock()
        sub_id = await client.subscribe("/user/queue/tasks", handler)
        await client.unsubscribe("/user/queue/tasks")

        assert sub_id not in client._subscriptions
        assert "/user/queue/tasks" not in client._subscription_ids


class TestStompClientManager:
    """Test StompClientManager reconnection logic."""

    def test_default_state(self):
        mgr = StompClientManager("ws://localhost:8080/ws")
        assert mgr.connected is False
        assert mgr.client is None

    @pytest.mark.asyncio
    async def test_subscribe_stores_for_reconnect(self):
        mgr = StompClientManager("ws://localhost:8080/ws")
        handler = AsyncMock()

        await mgr.subscribe("/user/queue/tasks", handler, ack_mode="auto")

        assert "/user/queue/tasks" in mgr._subscriptions
        assert mgr._subscriptions["/user/queue/tasks"] == (handler, "auto")

    @pytest.mark.asyncio
    async def test_send_raises_when_not_connected(self):
        mgr = StompClientManager("ws://localhost:8080/ws")

        with pytest.raises(RuntimeError, match="Not connected"):
            await mgr.send("/app/status", {"ok": True})

    @pytest.mark.asyncio
    async def test_disconnect_cleans_up(self):
        mgr = StompClientManager("ws://localhost:8080/ws")
        mock_client = MagicMock()
        mock_client.disconnect = AsyncMock()
        mock_client.connected = True
        mgr._client = mock_client

        await mgr.disconnect()

        mock_client.disconnect.assert_awaited_once()
        assert mgr._client is None


class TestHandleStompMessage:
    """Test STOMP MESSAGE frame handling."""

    @pytest.mark.asyncio
    async def test_dispatches_to_subscription_handler(self):
        client = StompClient("ws://localhost:8080/ws")
        handler = AsyncMock()
        client._subscriptions["sub-1"] = handler

        frame = StompFrame(
            command=StompCommand.MESSAGE,
            headers={
                "subscription": "sub-1",
                "destination": "/user/queue/tasks",
                "message-id": "msg-1",
            },
            body='{"task_id": 1}',
        )

        await client._handle_stomp_message(frame)
        handler.assert_awaited_once_with({"task_id": 1})

    @pytest.mark.asyncio
    async def test_handles_invalid_json_body(self):
        client = StompClient("ws://localhost:8080/ws")
        handler = AsyncMock()
        client._subscriptions["sub-1"] = handler

        frame = StompFrame(
            command=StompCommand.MESSAGE,
            headers={"subscription": "sub-1", "destination": "/queue"},
            body="not json!",
        )

        await client._handle_stomp_message(frame)
        handler.assert_awaited_once()
        call_arg = handler.call_args[0][0]
        assert "raw" in call_arg

    def test_handle_receipt(self):
        import asyncio

        client = StompClient("ws://localhost:8080/ws")
        loop = asyncio.new_event_loop()
        future = loop.create_future()
        client._pending_receipts["r-1"] = future

        frame = StompFrame(
            command=StompCommand.RECEIPT,
            headers={"receipt-id": "r-1"},
        )

        client._handle_receipt(frame)
        assert future.done()
        assert future.result() is True
        loop.close()
