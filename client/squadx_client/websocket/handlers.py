"""WebSocket message handlers for STOMP communication."""

from typing import TYPE_CHECKING, Any

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
        Supported fields: max_concurrent_agents, agent_timeout, default_model,
        enable_sandbox, enable_vnc, enable_network, log_level.
        """
        from squadx_client.config import settings

        config_payload = data.get("config", data)
        updated_fields = []

        updatable_fields = {
            "max_concurrent_agents": int,
            "agent_timeout": int,
            "default_model": str,
            "enable_sandbox": bool,
            "enable_vnc": bool,
            "enable_network": bool,
            "log_level": str,
        }

        for field, expected_type in updatable_fields.items():
            if field in config_payload:
                value = config_payload[field]
                try:
                    casted = expected_type(value)
                    setattr(settings, field, casted)
                    updated_fields.append(field)
                except (ValueError, TypeError) as e:
                    logger.warning("config_update_invalid_value", field=field, value=value, error=str(e))

        logger.info("config_update_applied", updated_fields=updated_fields)


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

        # Store log locally
        await self._store_log(log_level, message, agent, timestamp)

    async def _store_log(self, level: str, message: str, agent: str, timestamp: str) -> None:
        """Append execution log to local file."""
        import os

        from squadx_client.config import settings

        log_dir = os.path.join(settings.expanded_data_dir, "logs")
        os.makedirs(log_dir, exist_ok=True)

        log_file = os.path.join(log_dir, f"execution_{self.execution_id}.jsonl")
        import json
        entry = json.dumps({
            "execution_id": self.execution_id,
            "level": level,
            "message": message,
            "agent": agent,
            "timestamp": timestamp,
        })

        try:
            with open(log_file, "a") as f:
                f.write(entry + "\n")
        except OSError as e:
            logger.warning("execution_log_write_failed", error=str(e))
