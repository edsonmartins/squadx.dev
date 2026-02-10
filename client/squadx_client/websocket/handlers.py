"""WebSocket message handlers for STOMP communication."""

from typing import Any, TYPE_CHECKING

import structlog

from squadx_client.websocket.messages import MessageType

if TYPE_CHECKING:
    from squadx_client.daemon import SquadXDaemon

logger = structlog.get_logger()


class WebSocketHandler:
    """Handles WebSocket messages from the backend via STOMP.

    This handler processes incoming messages and delegates them to
    the appropriate daemon methods based on message type.
    """

    def __init__(self, daemon: "SquadXDaemon"):
        self.daemon = daemon
        self.handlers: dict[str, Any] = {
            # Task messages
            MessageType.TASK_ASSIGNED.value: self.handle_task_assigned,
            MessageType.TASK_CANCELLED.value: self.handle_task_cancelled,
            # Live view messages
            MessageType.START_LIVE_VIEW.value: self.handle_start_live_view,
            MessageType.STOP_LIVE_VIEW.value: self.handle_stop_live_view,
            # System messages
            MessageType.PING.value: self.handle_ping,
            "config_update": self.handle_config_update,
        }

    async def handle(self, data: dict[str, Any]) -> None:
        """Route message to appropriate handler.

        Args:
            data: Message payload from STOMP message body
        """
        message_type = data.get("type")

        handler = self.handlers.get(message_type)
        if handler:
            await handler(data)
        else:
            logger.warning("unhandled_message_type", type=message_type, data_keys=list(data.keys()))

    async def handle_task_assigned(self, data: dict[str, Any]) -> None:
        """Handle task assignment message.

        Expected payload:
        {
            "type": "task_assigned",
            "task_id": 123,
            "task": { ... task data ... }
        }
        """
        task_id = data.get("task_id")
        task_data = data.get("task")

        logger.info(
            "task_assignment_received",
            task_id=task_id,
            title=task_data.get("title") if task_data else None,
        )

        # Delegate to daemon
        await self.daemon._handle_task_assigned(data)

    async def handle_task_cancelled(self, data: dict[str, Any]) -> None:
        """Handle task cancellation message.

        Expected payload:
        {
            "type": "task_cancelled",
            "task_id": 123,
            "reason": "User cancelled"
        }
        """
        task_id = data.get("task_id")
        reason = data.get("reason", "No reason provided")

        logger.info("task_cancellation_received", task_id=task_id, reason=reason)

        # Delegate to daemon
        await self.daemon._handle_task_cancelled(data)

    async def handle_start_live_view(self, data: dict[str, Any]) -> None:
        """Handle start live view request.

        Expected payload:
        {
            "type": "start_live_view",
            "task_id": 123,
            "container_id": "abc123"
        }
        """
        task_id = data.get("task_id")
        container_id = data.get("container_id")

        logger.info("live_view_start_requested", task_id=task_id, container_id=container_id)

        # Delegate to daemon
        await self.daemon._handle_start_live_view(data)

    async def handle_stop_live_view(self, data: dict[str, Any]) -> None:
        """Handle stop live view request.

        Expected payload:
        {
            "type": "stop_live_view",
            "session_id": "xyz789"
        }
        """
        session_id = data.get("session_id")

        logger.info("live_view_stop_requested", session_id=session_id)

        await self.daemon._handle_stop_live_view(data)

    async def handle_ping(self, data: dict[str, Any]) -> None:
        """Handle ping message."""
        await self.daemon._send_pong()

    async def handle_config_update(self, data: dict[str, Any]) -> None:
        """Handle configuration update message.

        This allows the backend to dynamically update client configuration.
        """
        logger.info("config_update_received", config=data)

        # TODO: Apply configuration updates dynamically
        # e.g., update max concurrent agents, agent timeouts, etc.


class TaskMessageHandler:
    """Specialized handler for /user/queue/tasks subscription."""

    def __init__(self, daemon: "SquadXDaemon"):
        self.daemon = daemon
        self.handler = WebSocketHandler(daemon)

    async def __call__(self, data: dict[str, Any]) -> None:
        """Handle incoming task queue message."""
        await self.handler.handle(data)


class ExecutionLogHandler:
    """Handler for execution log subscriptions."""

    def __init__(self, daemon: "SquadXDaemon", execution_id: int):
        self.daemon = daemon
        self.execution_id = execution_id

    async def __call__(self, data: dict[str, Any]) -> None:
        """Handle incoming execution log message."""
        log_level = data.get("level", "INFO")
        message = data.get("message", "")
        agent = data.get("agent", "")
        timestamp = data.get("timestamp", "")

        logger.debug(
            "execution_log_received",
            execution_id=self.execution_id,
            level=log_level,
            agent=agent,
            message=message[:100],
        )

        # TODO: Store log locally or forward to UI
