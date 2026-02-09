"""SquadX Daemon - Background service for task execution."""

import asyncio
import json
from datetime import datetime
from typing import Any

import structlog

from squadx_client.config import settings
from squadx_client.orchestrator.graph import create_orchestrator
from squadx_client.websocket import StompClientManager, MessageType
from squadx_client.websocket.handlers import TaskMessageHandler

logger = structlog.get_logger()


class SquadXDaemon:
    """Background daemon that connects to the backend and executes tasks.

    This daemon uses STOMP over SockJS to communicate with the Spring Boot
    backend. It subscribes to task assignments and sends status updates.

    STOMP Destinations:
        Subscriptions:
            - /user/queue/tasks: Task assignments for this client
            - /topic/executions/{id}/control: Execution control messages

        Send destinations:
            - /app/tasks/{taskId}/status: Task status updates
            - /app/executions/{id}/logs: Execution logs
    """

    # STOMP destinations
    DEST_USER_TASKS = "/user/queue/tasks"
    DEST_TASK_STATUS = "/app/tasks/{task_id}/status"
    DEST_EXECUTION_LOGS = "/app/executions/{execution_id}/logs"
    DEST_CLIENT_REGISTER = "/app/client/register"
    DEST_CLIENT_HEARTBEAT = "/app/client/heartbeat"

    def __init__(self, api_url: str, token: str):
        """Initialize the daemon.

        Args:
            api_url: Backend API URL (e.g., http://localhost:8080)
            token: JWT authentication token
        """
        self.api_url = api_url
        self.token = token
        self.ws_url = settings.ws_url or api_url.replace("http", "ws") + "/ws"

        self.stomp = StompClientManager(
            ws_url=self.ws_url,
            token=token,
            use_sockjs=True,
        )

        self.orchestrator = create_orchestrator()
        self.running = False
        self.current_tasks: dict[int, asyncio.Task] = {}
        self._task_handler = TaskMessageHandler(self)

    async def run(self) -> None:
        """Main daemon loop."""
        self.running = True
        logger.info("daemon_starting", api_url=self.api_url, ws_url=self.ws_url)

        try:
            # Connect to STOMP
            await self.stomp.connect()

            # Subscribe to task queue
            await self.stomp.subscribe(
                self.DEST_USER_TASKS,
                self._task_handler,
            )

            # Register client
            await self._register_client()

            # Run with automatic reconnection
            await self.stomp.run()

        except asyncio.CancelledError:
            logger.info("daemon_cancelled")
        except Exception as e:
            logger.error("daemon_error", error=str(e))
            raise
        finally:
            await self.stop()

    async def _register_client(self) -> None:
        """Register this client with the backend."""
        await self.stomp.send(
            self.DEST_CLIENT_REGISTER,
            {
                "type": MessageType.CLIENT_REGISTER.value,
                "timestamp": datetime.now().isoformat(),
                "capabilities": ["execute_task", "live_view"],
                "max_concurrent_tasks": settings.max_concurrent_agents,
                "version": "0.1.0",
            },
        )
        logger.info("client_registered")

    async def _handle_task_assigned(self, data: dict[str, Any]) -> None:
        """Handle a new task assignment.

        Args:
            data: Task assignment message payload
        """
        task_id = data.get("task_id")
        task_data = data.get("task")

        if not task_id or not task_data:
            logger.error("invalid_task_data", data=data)
            return

        logger.info("task_assigned", task_id=task_id, title=task_data.get("title"))

        # Check concurrent task limit
        if len(self.current_tasks) >= settings.max_concurrent_agents:
            logger.warning("max_concurrent_tasks_reached", limit=settings.max_concurrent_agents)
            await self._send_task_rejected(task_id, "Max concurrent tasks reached")
            return

        # Start task execution in background
        task = asyncio.create_task(self._execute_task(task_id, task_data))
        self.current_tasks[task_id] = task

    async def _execute_task(self, task_id: int, task_data: dict[str, Any]) -> None:
        """Execute a task using the orchestrator.

        Args:
            task_id: Task ID
            task_data: Task details from backend
        """
        try:
            logger.info("task_execution_started", task_id=task_id)
            await self._send_task_status(task_id, "running", progress=0)

            # Run the orchestrator
            result = await self.orchestrator.ainvoke(
                {
                    "task_id": task_id,
                    "task": task_data,
                    "project_path": task_data.get("project_path", settings.workspace_path),
                    "messages": [],
                }
            )

            logger.info("task_execution_completed", task_id=task_id)
            await self._send_task_completed(task_id, result)

        except Exception as e:
            logger.error("task_execution_failed", task_id=task_id, error=str(e))
            await self._send_task_failed(task_id, str(e))

        finally:
            self.current_tasks.pop(task_id, None)

    async def _handle_task_cancelled(self, data: dict[str, Any]) -> None:
        """Handle task cancellation.

        Args:
            data: Cancellation message payload
        """
        task_id = data.get("task_id")

        if task_id in self.current_tasks:
            self.current_tasks[task_id].cancel()
            self.current_tasks.pop(task_id, None)
            logger.info("task_cancelled", task_id=task_id)

    async def _handle_start_live_view(self, data: dict[str, Any]) -> None:
        """Handle request to start live view for a task.

        Args:
            data: Live view request payload
        """
        task_id = data.get("task_id")
        container_id = data.get("container_id")

        logger.info("starting_live_view", task_id=task_id, container_id=container_id)
        # TODO: Implement live view integration with SquadX Live

    async def _send_pong(self) -> None:
        """Send pong response to ping."""
        await self.stomp.send(
            self.DEST_CLIENT_HEARTBEAT,
            {"type": MessageType.PONG.value, "timestamp": datetime.now().isoformat()},
        )

    async def _send_task_status(
        self,
        task_id: int,
        status: str,
        progress: int = 0,
        current_step: str = "",
    ) -> None:
        """Send task status update.

        Args:
            task_id: Task ID
            status: Current status (running, paused, etc.)
            progress: Progress percentage (0-100)
            current_step: Description of current step
        """
        destination = self.DEST_TASK_STATUS.format(task_id=task_id)
        await self.stomp.send(
            destination,
            {
                "type": MessageType.TASK_STATUS.value,
                "task_id": task_id,
                "status": status,
                "progress": progress,
                "current_step": current_step,
                "timestamp": datetime.now().isoformat(),
            },
        )

    async def _send_task_completed(self, task_id: int, result: dict[str, Any]) -> None:
        """Send task completion message.

        Args:
            task_id: Task ID
            result: Execution result from orchestrator
        """
        destination = self.DEST_TASK_STATUS.format(task_id=task_id)
        await self.stomp.send(
            destination,
            {
                "type": MessageType.TASK_COMPLETED.value,
                "task_id": task_id,
                "result": {
                    "final_result": result.get("final_result"),
                    "git_branch": result.get("git_branch"),
                    "git_commit": result.get("git_commit"),
                    "total_input_tokens": result.get("total_input_tokens", 0),
                    "total_output_tokens": result.get("total_output_tokens", 0),
                    "total_cost": result.get("total_cost", 0.0),
                },
                "timestamp": datetime.now().isoformat(),
            },
        )

    async def _send_task_failed(self, task_id: int, error: str) -> None:
        """Send task failure message.

        Args:
            task_id: Task ID
            error: Error message
        """
        destination = self.DEST_TASK_STATUS.format(task_id=task_id)
        await self.stomp.send(
            destination,
            {
                "type": MessageType.TASK_FAILED.value,
                "task_id": task_id,
                "error": error,
                "timestamp": datetime.now().isoformat(),
            },
        )

    async def _send_task_rejected(self, task_id: int, reason: str) -> None:
        """Send task rejection message.

        Args:
            task_id: Task ID
            reason: Rejection reason
        """
        destination = self.DEST_TASK_STATUS.format(task_id=task_id)
        await self.stomp.send(
            destination,
            {
                "type": MessageType.TASK_REJECTED.value,
                "task_id": task_id,
                "reason": reason,
                "timestamp": datetime.now().isoformat(),
            },
        )

    async def send_execution_log(
        self,
        execution_id: int,
        level: str,
        message: str,
        agent: str = "",
    ) -> None:
        """Send execution log to backend.

        Args:
            execution_id: Execution ID
            level: Log level (DEBUG, INFO, WARNING, ERROR)
            message: Log message
            agent: Agent name (if applicable)
        """
        destination = self.DEST_EXECUTION_LOGS.format(execution_id=execution_id)
        await self.stomp.send(
            destination,
            {
                "type": MessageType.EXECUTION_LOG.value,
                "execution_id": execution_id,
                "level": level,
                "message": message,
                "agent": agent,
                "timestamp": datetime.now().isoformat(),
            },
        )

    async def stop(self) -> None:
        """Stop the daemon gracefully."""
        self.running = False

        # Cancel all running tasks
        for task_id, task in list(self.current_tasks.items()):
            task.cancel()
            logger.info("task_cancelled_on_shutdown", task_id=task_id)

        self.current_tasks.clear()

        # Disconnect STOMP
        await self.stomp.disconnect()

        logger.info("daemon_stopped")


async def run_daemon(api_url: str | None = None, token: str | None = None) -> None:
    """Run the SquadX daemon.

    Args:
        api_url: Backend API URL (defaults to settings.api_url)
        token: JWT token (defaults to settings.api_token)
    """
    api_url = api_url or settings.api_url
    token = token or settings.api_token

    if not token:
        raise ValueError("API token is required. Set SQUADX_API_TOKEN environment variable.")

    daemon = SquadXDaemon(api_url=api_url, token=token)
    await daemon.run()
