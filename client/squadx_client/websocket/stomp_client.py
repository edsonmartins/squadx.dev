"""STOMP over SockJS client for Spring Boot WebSocket communication."""

import asyncio
import json
import random
import string
import uuid
from collections.abc import Callable, Coroutine
from typing import Any
from urllib.parse import urlparse

import aiohttp
import structlog
from tenacity import retry, stop_after_attempt, wait_exponential

from squadx_client.websocket.messages import StompCommand, StompFrame

logger = structlog.get_logger()

# Type alias for message handlers
MessageHandler = Callable[[dict[str, Any]], Coroutine[Any, Any, None]]


class StompClient:
    """STOMP over SockJS/WebSocket client for Spring Boot compatibility.

    Spring Boot uses STOMP (Simple Text Oriented Messaging Protocol) over
    SockJS for WebSocket communication. This client implements the STOMP
    protocol with SockJS framing.

    Usage:
        client = StompClient("ws://localhost:8080/ws", jwt_token)
        await client.connect()

        await client.subscribe("/user/queue/tasks", handle_task)
        await client.send("/app/tasks/status", {"status": "running"})

        await client.disconnect()
    """

    # STOMP protocol version
    STOMP_VERSION = "1.2"

    # Heartbeat settings (client-send, server-send in ms)
    HEARTBEAT_SEND = 10000
    HEARTBEAT_RECEIVE = 10000

    def __init__(
        self,
        ws_url: str,
        token: str | None = None,
        use_sockjs: bool = True,
    ):
        """Initialize STOMP client.

        Args:
            ws_url: WebSocket URL (e.g., ws://localhost:8080/ws)
            token: JWT token for authentication
            use_sockjs: Whether to use SockJS protocol framing
        """
        self.ws_url = ws_url
        self.token = token
        self.use_sockjs = use_sockjs

        self._session: aiohttp.ClientSession | None = None
        self._ws: aiohttp.ClientWebSocketResponse | None = None
        self._connected = False
        self._subscriptions: dict[str, MessageHandler] = {}
        self._subscription_ids: dict[str, str] = {}
        self._pending_receipts: dict[str, asyncio.Future] = {}
        self._heartbeat_task: asyncio.Task | None = None
        self._receive_task: asyncio.Task | None = None
        self._server_heartbeat: int = 0
        self._last_server_message: float = 0

    @property
    def connected(self) -> bool:
        """Check if client is connected."""
        return self._connected

    def _generate_sockjs_url(self) -> str:
        """Generate SockJS-compatible WebSocket URL.

        SockJS URLs follow the pattern: /ws/{server_id}/{session_id}/websocket
        """
        parsed = urlparse(self.ws_url)
        server_id = "".join(random.choices(string.digits, k=3))
        session_id = "".join(random.choices(string.ascii_lowercase + string.digits, k=8))

        sockjs_path = f"{parsed.path}/{server_id}/{session_id}/websocket"
        return f"{parsed.scheme}://{parsed.netloc}{sockjs_path}"

    async def connect(self) -> None:
        """Establish WebSocket connection and perform STOMP handshake."""
        if self._connected:
            logger.warning("stomp_already_connected")
            return

        # Create HTTP session with headers
        headers = {}
        if self.token:
            headers["Authorization"] = f"Bearer {self.token}"

        self._session = aiohttp.ClientSession(headers=headers)

        try:
            ws_url = self._generate_sockjs_url() if self.use_sockjs else self.ws_url
            logger.info("stomp_connecting", url=ws_url)

            self._ws = await self._session.ws_connect(
                ws_url,
                heartbeat=30.0,
                receive_timeout=60.0,
            )

            # SockJS sends an 'o' (open) frame first
            if self.use_sockjs:
                msg = await self._ws.receive()
                if msg.type == aiohttp.WSMsgType.TEXT and msg.data == "o":
                    logger.debug("sockjs_open_received")
                else:
                    raise ConnectionError(f"Expected SockJS open frame, got: {msg.data}")

            # Send STOMP CONNECT frame
            await self._stomp_connect()

            # Start background tasks
            self._receive_task = asyncio.create_task(self._receive_loop())
            self._heartbeat_task = asyncio.create_task(self._heartbeat_loop())

            logger.info("stomp_connected")

        except Exception as e:
            logger.error("stomp_connection_failed", error=str(e))
            await self._cleanup()
            raise

    async def _stomp_connect(self) -> None:
        """Send STOMP CONNECT frame and wait for CONNECTED response."""
        assert self._ws is not None  # the websocket is opened before _stomp_connect runs
        headers = {
            "accept-version": self.STOMP_VERSION,
            "heart-beat": f"{self.HEARTBEAT_SEND},{self.HEARTBEAT_RECEIVE}",
        }

        if self.token:
            headers["Authorization"] = f"Bearer {self.token}"

        connect_frame = StompFrame(
            command=StompCommand.CONNECT,
            headers=headers,
        )

        await self._send_frame(connect_frame)

        # Wait for CONNECTED frame
        msg = await self._ws.receive(timeout=10.0)
        if msg.type != aiohttp.WSMsgType.TEXT:
            raise ConnectionError(f"Expected text message, got: {msg.type}")

        data = self._unwrap_sockjs(msg.data) if self.use_sockjs else msg.data
        frame = StompFrame.decode(data)

        if frame.command != StompCommand.CONNECTED:
            raise ConnectionError(f"Expected CONNECTED, got: {frame.command}")

        # Parse server heartbeat
        if "heart-beat" in frame.headers:
            sx, sy = frame.headers["heart-beat"].split(",")
            self._server_heartbeat = int(sy)

        self._connected = True
        logger.info(
            "stomp_handshake_complete",
            version=frame.headers.get("version"),
            server=frame.headers.get("server"),
        )

    async def disconnect(self) -> None:
        """Gracefully disconnect from the server."""
        if not self._connected:
            return

        logger.info("stomp_disconnecting")

        try:
            # Send DISCONNECT frame
            receipt_id = str(uuid.uuid4())
            disconnect_frame = StompFrame(
                command=StompCommand.DISCONNECT,
                headers={"receipt": receipt_id},
            )
            await self._send_frame(disconnect_frame)

            # Wait briefly for receipt (best effort)
            await asyncio.sleep(0.5)

        except Exception as e:
            logger.warning("stomp_disconnect_error", error=str(e))

        finally:
            await self._cleanup()

    async def _cleanup(self) -> None:
        """Clean up resources."""
        self._connected = False

        if self._heartbeat_task:
            self._heartbeat_task.cancel()
            try:
                await self._heartbeat_task
            except asyncio.CancelledError:
                pass
            self._heartbeat_task = None

        if self._receive_task:
            self._receive_task.cancel()
            try:
                await self._receive_task
            except asyncio.CancelledError:
                pass
            self._receive_task = None

        if self._ws:
            await self._ws.close()
            self._ws = None

        if self._session:
            await self._session.close()
            self._session = None

        self._subscriptions.clear()
        self._subscription_ids.clear()
        self._pending_receipts.clear()

        logger.info("stomp_cleaned_up")

    async def subscribe(
        self,
        destination: str,
        handler: MessageHandler,
        ack_mode: str = "auto",
    ) -> str:
        """Subscribe to a destination.

        Args:
            destination: STOMP destination (e.g., /user/queue/tasks)
            handler: Async function to handle received messages
            ack_mode: Acknowledgment mode (auto, client, client-individual)

        Returns:
            Subscription ID
        """
        if not self._connected:
            raise RuntimeError("Not connected")

        sub_id = str(uuid.uuid4())

        subscribe_frame = StompFrame(
            command=StompCommand.SUBSCRIBE,
            headers={
                "id": sub_id,
                "destination": destination,
                "ack": ack_mode,
            },
        )

        await self._send_frame(subscribe_frame)

        self._subscriptions[sub_id] = handler
        self._subscription_ids[destination] = sub_id

        logger.info("stomp_subscribed", destination=destination, sub_id=sub_id)
        return sub_id

    async def unsubscribe(self, destination: str) -> None:
        """Unsubscribe from a destination.

        Args:
            destination: STOMP destination to unsubscribe from
        """
        if destination not in self._subscription_ids:
            logger.warning("stomp_subscription_not_found", destination=destination)
            return

        sub_id = self._subscription_ids[destination]

        unsubscribe_frame = StompFrame(
            command=StompCommand.UNSUBSCRIBE,
            headers={"id": sub_id},
        )

        await self._send_frame(unsubscribe_frame)

        del self._subscriptions[sub_id]
        del self._subscription_ids[destination]

        logger.info("stomp_unsubscribed", destination=destination)

    async def send(
        self,
        destination: str,
        body: dict[str, Any] | str,
        content_type: str = "application/json",
        headers: dict[str, str] | None = None,
    ) -> None:
        """Send a message to a destination.

        Args:
            destination: STOMP destination (e.g., /app/tasks/status)
            body: Message body (dict will be JSON-encoded)
            content_type: Content type header
            headers: Additional headers
        """
        if not self._connected:
            raise RuntimeError("Not connected")

        frame_headers = {
            "destination": destination,
            "content-type": content_type,
        }

        if headers:
            frame_headers.update(headers)

        body_str = json.dumps(body) if isinstance(body, dict) else body

        send_frame = StompFrame(
            command=StompCommand.SEND,
            headers=frame_headers,
            body=body_str,
        )

        await self._send_frame(send_frame)
        logger.debug("stomp_message_sent", destination=destination)

    async def _send_frame(self, frame: StompFrame) -> None:
        """Send a STOMP frame over WebSocket."""
        if not self._ws:
            raise RuntimeError("WebSocket not connected")

        data = frame.encode().decode("utf-8")

        if self.use_sockjs:
            # SockJS wraps messages in JSON array
            data = json.dumps([data])

        await self._ws.send_str(data)

    def _unwrap_sockjs(self, data: str) -> str:
        """Unwrap SockJS message framing.

        SockJS sends messages as:
        - 'a["message"]' for regular messages
        - 'h' for heartbeat
        - 'c[code, reason]' for close
        """
        if not data:
            return ""

        frame_type = data[0]

        if frame_type == "a":
            # Array message
            messages = json.loads(data[1:])
            return messages[0] if messages else ""
        elif frame_type == "h":
            # Heartbeat
            return ""
        elif frame_type == "c":
            # Close frame
            close_data = json.loads(data[1:])
            logger.warning("sockjs_close_frame", code=close_data[0], reason=close_data[1])
            return ""
        else:
            return data

    async def _receive_loop(self) -> None:
        """Background task to receive and process messages."""
        while self._connected and self._ws:
            try:
                msg = await self._ws.receive(timeout=30.0)

                if msg.type == aiohttp.WSMsgType.TEXT:
                    await self._handle_message(msg.data)
                elif msg.type == aiohttp.WSMsgType.CLOSED:
                    logger.warning("websocket_closed")
                    break
                elif msg.type == aiohttp.WSMsgType.ERROR:
                    logger.error("websocket_error", error=str(msg.data))
                    break

            except TimeoutError:
                # Timeout is fine, just continue
                continue
            except asyncio.CancelledError:
                raise
            except Exception as e:
                logger.error("receive_loop_error", error=str(e))
                break

        self._connected = False

    async def _handle_message(self, data: str) -> None:
        """Handle received WebSocket message."""
        data = self._unwrap_sockjs(data) if self.use_sockjs else data

        if not data:
            # Heartbeat or empty message
            return

        try:
            frame = StompFrame.decode(data)
        except Exception as e:
            logger.error("stomp_frame_decode_error", error=str(e), data=data[:100])
            return

        if frame.command == StompCommand.MESSAGE:
            await self._handle_stomp_message(frame)
        elif frame.command == StompCommand.RECEIPT:
            self._handle_receipt(frame)
        elif frame.command == StompCommand.ERROR:
            self._handle_error(frame)
        else:
            logger.debug("stomp_frame_received", command=frame.command.value)

    async def _handle_stomp_message(self, frame: StompFrame) -> None:
        """Handle incoming STOMP MESSAGE frame."""
        sub_id = frame.headers.get("subscription")
        destination = frame.headers.get("destination", "")
        message_id = frame.headers.get("message-id", "")

        if sub_id and sub_id in self._subscriptions:
            handler = self._subscriptions[sub_id]

            try:
                # Parse body as JSON
                body = json.loads(frame.body) if frame.body else {}
            except json.JSONDecodeError as e:
                logger.warning(
                    "stomp_message_json_decode_failed",
                    error=str(e),
                    destination=destination,
                    body_preview=frame.body[:200] if frame.body else "",
                )
                body = {"raw": frame.body}

            try:
                await handler(body)
            except Exception as e:
                logger.error(
                    "message_handler_error",
                    error=str(e),
                    destination=destination,
                    message_id=message_id,
                )
        else:
            logger.warning(
                "no_handler_for_subscription",
                sub_id=sub_id,
                destination=destination,
            )

    def _handle_receipt(self, frame: StompFrame) -> None:
        """Handle RECEIPT frame."""
        receipt_id = frame.headers.get("receipt-id")
        if receipt_id in self._pending_receipts:
            future = self._pending_receipts.pop(receipt_id)
            if not future.done():
                future.set_result(True)

    def _handle_error(self, frame: StompFrame) -> None:
        """Handle ERROR frame."""
        message = frame.headers.get("message", "Unknown error")
        logger.error("stomp_error", message=message, body=frame.body[:200] if frame.body else "")

    async def _heartbeat_loop(self) -> None:
        """Background task to send heartbeats."""
        interval = self.HEARTBEAT_SEND / 1000  # Convert to seconds

        while self._connected:
            try:
                await asyncio.sleep(interval)

                if self._connected and self._ws:
                    # Send heartbeat (just a newline in STOMP)
                    if self.use_sockjs:
                        await self._ws.send_str('["\\n"]')
                    else:
                        await self._ws.send_str("\n")

                    logger.debug("heartbeat_sent")

            except asyncio.CancelledError:
                raise
            except Exception as e:
                logger.error("heartbeat_error", error=str(e))


class StompClientManager:
    """Manages STOMP client connection with automatic reconnection."""

    def __init__(
        self,
        ws_url: str,
        token: str | None = None,
        use_sockjs: bool = True,
        max_reconnect_attempts: int = 5,
    ):
        self.ws_url = ws_url
        self.token = token
        self.use_sockjs = use_sockjs
        self.max_reconnect_attempts = max_reconnect_attempts

        self._client: StompClient | None = None
        self._subscriptions: dict[str, tuple[MessageHandler, str]] = {}
        self._running = False
        self._reconnect_task: asyncio.Task | None = None

    @property
    def client(self) -> StompClient | None:
        """Get the current STOMP client."""
        return self._client

    @property
    def connected(self) -> bool:
        """Check if client is connected."""
        return self._client is not None and self._client.connected

    @retry(
        stop=stop_after_attempt(5),
        wait=wait_exponential(multiplier=1, min=2, max=30),
    )
    async def connect(self) -> None:
        """Connect with automatic retry."""
        self._client = StompClient(
            ws_url=self.ws_url,
            token=self.token,
            use_sockjs=self.use_sockjs,
        )
        await self._client.connect()

        # Resubscribe to all destinations
        for destination, (handler, ack_mode) in self._subscriptions.items():
            await self._client.subscribe(destination, handler, ack_mode)

    async def disconnect(self) -> None:
        """Disconnect the client."""
        self._running = False

        if self._reconnect_task:
            self._reconnect_task.cancel()
            try:
                await self._reconnect_task
            except asyncio.CancelledError:
                pass

        if self._client:
            await self._client.disconnect()
            self._client = None

    async def subscribe(
        self,
        destination: str,
        handler: MessageHandler,
        ack_mode: str = "auto",
    ) -> None:
        """Subscribe to a destination (survives reconnection)."""
        self._subscriptions[destination] = (handler, ack_mode)

        if self._client and self._client.connected:
            await self._client.subscribe(destination, handler, ack_mode)

    async def send(
        self,
        destination: str,
        body: dict[str, Any] | str,
        content_type: str = "application/json",
    ) -> None:
        """Send a message."""
        if not self._client or not self._client.connected:
            raise RuntimeError("Not connected")

        await self._client.send(destination, body, content_type)

    async def run(self) -> None:
        """Run the client with automatic reconnection."""
        self._running = True

        while self._running:
            try:
                if not self.connected:
                    await self.connect()

                # Wait for disconnection
                while self._running and self.connected:
                    await asyncio.sleep(1)

                if self._running:
                    logger.info("stomp_reconnecting", delay=5)
                    await asyncio.sleep(5)

            except Exception as e:
                logger.error("stomp_manager_error", error=str(e))
                if self._running:
                    await asyncio.sleep(5)
