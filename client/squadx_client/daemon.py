"""SquadX Daemon - Background service for task execution."""

import asyncio
import os
from datetime import datetime
from pathlib import Path
from typing import Any

import aiohttp
import structlog

from squadx_client.agents.security import scrub_env
from squadx_client.config import settings
from squadx_client.docker.manager import docker_manager
from squadx_client.memory import BrainSentryClient
from squadx_client.messaging.run_event import default_run_event_metadata
from squadx_client.orchestrator.graph import create_orchestrator
from squadx_client.streaming.vnc_streamer import stream_manager
from squadx_client.websocket import MessageType, StompClientManager
from squadx_client.websocket.handlers import TaskMessageHandler

logger = structlog.get_logger()


def _validate_task_data(task_data: Any) -> str | None:
    """Validate task data received from STOMP.

    Returns:
        None if valid, or an error message string describing the problem.
    """
    if not isinstance(task_data, dict):
        return f"task_data must be a dict, got {type(task_data).__name__}"

    required_fields = {
        "task_id": (int, str),
        "title": (str,),
        "description": (str,),
    }

    for field, expected_types in required_fields.items():
        value = task_data.get(field)
        if value is None:
            return f"missing required field: {field}"
        if not isinstance(value, expected_types):
            return (
                f"field '{field}' must be {' or '.join(t.__name__ for t in expected_types)}, "
                f"got {type(value).__name__}"
            )

    if isinstance(task_data.get("title"), str) and not task_data["title"].strip():
        return "field 'title' must not be empty"

    if isinstance(task_data.get("description"), str) and not task_data["description"].strip():
        return "field 'description' must not be empty"

    return None


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
        self._bg_tasks: set[asyncio.Task] = set()
        self._task_handler = TaskMessageHandler(self)
        # Warm container pool — set in run() when SQUADX_SANDBOX_POOL_ENABLED
        self.warm_pool: Any = None

    @staticmethod
    def pid_file_path() -> Path:
        """Return the path to the PID file."""
        return Path(settings.expanded_data_dir) / "daemon.pid"

    def _write_pid_file(self) -> None:
        """Write the current process PID to the PID file."""
        pid_path = self.pid_file_path()
        pid_path.parent.mkdir(parents=True, exist_ok=True)
        pid_path.write_text(str(os.getpid()))
        logger.info("pid_file_written", path=str(pid_path), pid=os.getpid())

    def _remove_pid_file(self) -> None:
        """Remove the PID file if it exists."""
        pid_path = self.pid_file_path()
        try:
            pid_path.unlink(missing_ok=True)
            logger.info("pid_file_removed", path=str(pid_path))
        except OSError as e:
            logger.warning("pid_file_remove_failed", path=str(pid_path), error=str(e))

    async def run(self) -> None:
        """Main daemon loop."""
        self.running = True
        self._write_pid_file()
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

            # Optional HTTP polling fallback (resilience behind NAT/firewall)
            poll_task = None
            if settings.poll_fallback_interval_seconds > 0:
                poll_task = asyncio.create_task(self._poll_fallback_loop())

            # Warm container pool (opt-in via SQUADX_SANDBOX_POOL_ENABLED).
            # Trims the 10-20s Docker cold start to <1s by handing tasks a
            # pre-created container; safe to leave disabled in dev.
            if settings.sandbox_pool_enabled:
                from squadx_client.docker.pool import build_pool_from_settings

                self.warm_pool = build_pool_from_settings(docker_manager)
                docker_manager.warm_pool = self.warm_pool
                await self.warm_pool.initialize()

            try:
                # Run with automatic reconnection
                await self.stomp.run()
            finally:
                if poll_task is not None:
                    poll_task.cancel()

        except asyncio.CancelledError:
            logger.info("daemon_cancelled")
        except Exception as e:
            logger.error("daemon_error", error=str(e))
            raise
        finally:
            await self.stop()

    async def _poll_fallback_loop(self) -> None:
        """Periodically claim pending tasks over HTTP as a STOMP-push fallback."""
        interval = settings.poll_fallback_interval_seconds
        logger.info("poll_fallback_started", interval=interval)
        try:
            while self.running:
                await asyncio.sleep(interval)
                try:
                    await self._poll_pending_once()
                except Exception as e:  # noqa: BLE001 - keep the loop alive
                    logger.warning("poll_fallback_error", error=str(e))
        except asyncio.CancelledError:
            logger.info("poll_fallback_stopped")

    async def _poll_pending_once(self) -> None:
        """Fetch pending assignments and process any not already in flight.

        Each candidate is atomically claimed on the backend (PENDING -> RUNNING)
        before processing, so concurrent daemons / a racing STOMP push cannot
        double-dispatch the same task.
        """
        headers = {"Authorization": f"Bearer {self.token}"}
        async with aiohttp.ClientSession(headers=headers) as session:
            async with session.get(f"{self.api_url}/api/v1/executions/pending") as resp:
                if resp.status != 200:
                    logger.warning("poll_fallback_http_error", status=resp.status)
                    return
                body = await resp.json()

            items = body.get("data") or []
            for item in items:
                task_id = item.get("task_id")
                if task_id is None or task_id in self.current_tasks:
                    continue
                execution_id = (item.get("task") or {}).get("execution_id")
                if execution_id is not None and not await self._claim_execution(
                    session, execution_id
                ):
                    continue  # another client won the claim
                logger.info("poll_fallback_claiming_task", task_id=task_id)
                await self._handle_task_assigned(item)

    async def _claim_execution(self, session: aiohttp.ClientSession, execution_id: Any) -> bool:
        """Atomically claim a pending execution; returns True if this client won."""
        try:
            async with session.post(
                f"{self.api_url}/api/v1/executions/{execution_id}/claim"
            ) as resp:
                if resp.status != 200:
                    return False
                body = await resp.json()
                return bool(body.get("data"))
        except Exception as e:  # noqa: BLE001
            logger.warning("poll_fallback_claim_error", execution_id=execution_id, error=str(e))
            return False

    def _on_bg_task_done(self, task: "asyncio.Task") -> None:
        """Discard a finished background task and surface (not swallow) its error."""
        self._bg_tasks.discard(task)
        if not task.cancelled():
            exc = task.exception()
            if exc is not None:
                logger.warning("background_task_failed", error=str(exc))

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

        # Validate task data structure before processing
        validation_error = _validate_task_data(task_data)
        if validation_error:
            logger.error(
                "task_data_validation_failed",
                task_id=task_id,
                error=validation_error,
            )
            await self._send_task_rejected(task_id, f"Invalid task data: {validation_error}")
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

            brainsentry_client = BrainSentryClient()
            execution_id = task_data.get("execution_id") or task_data.get("executionId") or task_id
            brainsentry_session_id = (
                task_data.get("brain_sentry_session_id")
                or task_data.get("brainSentrySessionId")
            )
            if not brainsentry_session_id:
                brainsentry_session_id = await brainsentry_client.start_session(
                    str(execution_id),
                    task_id=str(task_id),
                    agent_id=str(task_data.get("assigned_agent_id") or task_data.get("agent_id") or ""),
                )

            runtime_kind = str(task_data.get("runtime_kind") or "NATIVE").upper()

            if settings.smoke_execution_mode:
                result = await self._run_smoke_execution(task_id, task_data, execution_id)
            elif runtime_kind == "EXTERNAL_CLI":
                # Runtime adapter: drive an external coding CLI in the sandbox
                # instead of the native LangGraph loop.
                result = await self._run_external_cli_task(
                    task_id, task_data, execution_id, brainsentry_session_id
                )
            else:
                # Run the orchestrator
                result = await self.orchestrator.ainvoke(
                    {
                        "task_id": task_id,
                        "task": task_data,
                        "execution_id": execution_id,
                        "brainsentry_session_id": brainsentry_session_id,
                        "project_path": task_data.get("project_path", settings.workspace_path),
                        "cost_budget_usd": settings.cost_budget_usd,
                        "messages": [],
                    }
                )

            logger.info("task_execution_completed", task_id=task_id)
            await brainsentry_client.end_session(
                brainsentry_session_id or "",
                status="completed",
                summary=result.get("final_result", ""),
            )
            await self._send_task_completed(task_id, result)

        except Exception as e:
            logger.error("task_execution_failed", task_id=task_id, error=str(e))
            try:
                await brainsentry_client.end_session(
                    brainsentry_session_id or "",
                    status="failed",
                    summary=str(e),
                )
            except Exception:
                logger.warning("brainsentry_session_end_failed", task_id=task_id)
            await self._send_task_failed(task_id, str(e))

        finally:
            if "brainsentry_client" in locals():
                await brainsentry_client.close()
            self.current_tasks.pop(task_id, None)

    async def _run_external_cli_task(
        self,
        task_id: int,
        task_data: dict[str, Any],
        execution_id: int | str,
        brainsentry_session_id: str | None,
    ) -> dict[str, Any]:
        """Drive an external coding CLI (Claude Code/Codex/Gemini) in the sandbox."""
        from squadx_client.agents.factory import create_agent
        from squadx_client.sandbox import create_sandbox_session, features_for

        title = task_data.get("title") or f"Task {task_id}"
        description = task_data.get("description") or title
        cli_provider = task_data.get("cli_provider") or "CLAUDE_CODE"
        workspace_path = task_data.get("project_path") or settings.workspace_path

        # External CLI is Docker-only (feature matrix — ADR-0009).
        feats = features_for()
        if not feats.external_cli:
            raise RuntimeError(
                f"External CLI requires a backend with external_cli support "
                f"(configured={feats.kind.value!r}: {feats.notes}). "
                f"Set SQUADX_SANDBOX_BACKEND=docker."
            )

        # Inject provider API keys for the CLI (BYO key). Scrub defensively so only these
        # explicitly-allowed secrets (and safe vars) ever reach it (ADR-0007). These go in
        # as *per-exec* env, not container-create env: keys must not be readable from the
        # container's metadata for its whole lifetime, and create-time env is fixed at
        # create — which is what stops a pre-created warm-pool container from carrying them.
        exec_env: dict[str, str] = {}
        if settings.anthropic_api_key:
            exec_env["ANTHROPIC_API_KEY"] = settings.anthropic_api_key
        if settings.openai_api_key:
            exec_env["OPENAI_API_KEY"] = settings.openai_api_key
        if settings.google_api_key:
            exec_env["GOOGLE_API_KEY"] = settings.google_api_key
        exec_env = scrub_env(
            exec_env, allow=("ANTHROPIC_API_KEY", "OPENAI_API_KEY", "GOOGLE_API_KEY")
        )

        # Per-squad egress policy from the backend (RFC-0006). None when this backend
        # does not send it or sends a value we do not know — AgentSandbox then falls
        # back to the daemon's own default rather than to no policy.
        from squadx_client.docker.network_policy import policy_name_from_backend

        network_policy = policy_name_from_backend(task_data.get("sandbox_egress_policy"))

        sandbox = create_sandbox_session(
            task_id=task_id,
            agent_type="external_cli",
            workspace_path=workspace_path,
            network_policy=network_policy,
        )
        # DockerSandboxSession fills image/memory/cpu/vnc from settings.
        started = await sandbox.start(exec_env=exec_env)
        if not started:
            raise RuntimeError("Failed to start sandbox for external CLI agent")

        try:
            agent = create_agent(
                "external_cli",
                sandbox=sandbox,
                brainsentry_session_id=brainsentry_session_id,
                runtime_kind="EXTERNAL_CLI",
                cli_provider=cli_provider,
            )

            def _progress(chunk: str) -> None:
                stripped = chunk.strip()
                if not stripped:
                    return
                step = stripped.splitlines()[-1][:200]
                # Retain a reference so the task isn't GC'd before it runs, and log
                # (not swallow) any send failure.
                t = asyncio.create_task(
                    self._send_task_status(
                        task_id, "running", progress=50, current_step=step
                    )
                )
                self._bg_tasks.add(t)
                t.add_done_callback(self._on_bg_task_done)

            result = await agent.execute(
                task_title=title,
                task_description=description,
                context={
                    "main_task": task_data,
                    "execution_id": execution_id,
                    "progress_callback": _progress,
                },
            )

            live_codes = [sandbox.live_join_code] if sandbox.live_join_code else []
            branch = await sandbox.execute(
                ["git", "rev-parse", "--abbrev-ref", "HEAD"], timeout=30
            )
            commit = await sandbox.execute(["git", "rev-parse", "HEAD"], timeout=30)

            return {
                "final_result": result.get("output", ""),
                "files_modified": result.get("files_modified", []),
                "git_branch": branch.output.strip() if branch.success else None,
                "git_commit": commit.output.strip() if commit.success else None,
                "live_session_codes": live_codes,
                # Usage the CLI reported back (Claude Code via --output-format json);
                # zero for providers with no machine-readable usage. Previously
                # hardcoded, so EXTERNAL_CLI runs never counted against the cost ceiling.
                "total_input_tokens": result.get("input_tokens", 0),
                "total_output_tokens": result.get("output_tokens", 0),
                "total_cost": result.get("cost", 0.0),
            }
        finally:
            await sandbox.stop()

    async def _run_smoke_execution(
        self,
        task_id: int,
        task_data: dict[str, Any],
        execution_id: int | str,
    ) -> dict[str, Any]:
        """Run a deterministic local execution for real smoke tests.

        This preserves the real backend/STOMP/BrainSentry flow without requiring
        an external LLM provider during E2E validation.
        """
        await asyncio.sleep(max(settings.smoke_execution_delay_seconds, 0))
        summary = settings.smoke_execution_summary
        title = str(task_data.get("title") or f"Task {task_id}")
        return {
            "final_result": f"{summary} {title}",
            "git_branch": f"smoke/execution-{execution_id}",
            "git_commit": "smoke-commit",
            "live_session_codes": [],
            "total_input_tokens": 0,
            "total_output_tokens": 0,
            "total_cost": 0.0,
        }

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
            data: Live view request payload containing:
                - task_id: Task ID
                - session_id: Live session ID
                - vnc_port: VNC port on the sandbox container
        """
        task_id = data.get("task_id")
        session_id = data.get("session_id")
        vnc_port = data.get("vnc_port")

        logger.info("starting_live_view", task_id=task_id, session_id=session_id, vnc_port=vnc_port)

        if not session_id or not vnc_port or task_id is None:
            logger.error("missing_live_view_params", data=data)
            return

        try:
            # Create streaming session
            streamer = await stream_manager.create_session(
                session_id=session_id,
                task_id=int(task_id),
                vnc_port=vnc_port,
            )

            # Set up frame callback to send to backend
            async def on_frame(frame_data: bytes):
                await self._send_live_view_frame(session_id, frame_data)

            streamer.on_frame(on_frame)

            # Start streaming
            started = await stream_manager.start_session(session_id)
            if started:
                logger.info("live_view_started", session_id=session_id)
                await self._send_live_view_status(session_id, "streaming")
            else:
                logger.error("live_view_start_failed", session_id=session_id)
                await self._send_live_view_status(session_id, "error")

        except Exception as e:
            logger.error("live_view_error", session_id=session_id, error=str(e))
            await self._send_live_view_status(session_id, "error", error=str(e))

    async def _handle_stop_live_view(self, data: dict[str, Any]) -> None:
        """Handle request to stop live view.

        Args:
            data: Stop request payload containing session_id
        """
        session_id = data.get("session_id")

        if not session_id:
            return

        logger.info("stopping_live_view", session_id=session_id)

        try:
            await stream_manager.stop_session(session_id)
            await self._send_live_view_status(session_id, "stopped")
        except Exception as e:
            logger.error("live_view_stop_error", session_id=session_id, error=str(e))

    async def _send_live_view_frame(self, session_id: str, frame_data: bytes) -> None:
        """Send a live view frame to viewers via backend.

        Args:
            session_id: Session ID
            frame_data: JPEG frame bytes (base64 encoded for transport)
        """
        import base64
        destination = f"/app/live/{session_id}/frame"
        await self.stomp.send(
            destination,
            {
                "type": "live_frame",
                "session_id": session_id,
                "frame": base64.b64encode(frame_data).decode("utf-8"),
                "timestamp": datetime.now().isoformat(),
            },
        )

    async def _send_live_view_status(
        self,
        session_id: str,
        status: str,
        error: str | None = None,
    ) -> None:
        """Send live view status update.

        Args:
            session_id: Session ID
            status: Current status (streaming, stopped, error)
            error: Optional error message
        """
        destination = f"/app/live/{session_id}/status"
        payload = {
            "type": "live_status",
            "session_id": session_id,
            "status": status,
            "timestamp": datetime.now().isoformat(),
        }
        if error:
            payload["error"] = error

        await self.stomp.send(destination, payload)

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
        live_session_codes: list[str] | None = None,
    ) -> None:
        """Send task status update.

        Args:
            task_id: Task ID
            status: Current status (running, paused, etc.)
            progress: Progress percentage (0-100)
            current_step: Description of current step
            live_session_codes: Active live streaming session codes
        """
        destination = self.DEST_TASK_STATUS.format(task_id=task_id)
        payload = {
            "type": MessageType.TASK_STATUS.value,
            "task_id": task_id,
            "status": status,
            "progress": progress,
            "current_step": current_step,
            "timestamp": datetime.now().isoformat(),
        }
        if live_session_codes:
            payload["live_session_codes"] = live_session_codes

        await self.stomp.send(destination, payload)

    async def _send_task_completed(self, task_id: int, result: dict[str, Any]) -> None:
        """Send task completion message.

        Args:
            task_id: Task ID
            result: Execution result from orchestrator
        """
        destination = self.DEST_TASK_STATUS.format(task_id=task_id)

        # Extract metrics if available
        metrics = result.get("metrics")
        metrics_summary = (
            metrics.to_summary()
            if metrics is not None and hasattr(metrics, "to_summary")
            else {}
        )

        await self.stomp.send(
            destination,
            {
                "type": MessageType.TASK_COMPLETED.value,
                "task_id": task_id,
                "result": {
                    "final_result": result.get("final_result"),
                    "git_branch": result.get("git_branch"),
                    "git_commit": result.get("git_commit"),
                    "live_session_codes": result.get("live_session_codes", []),
                    "metrics": metrics_summary,
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
        event_type: str | None = None,
        visibility: str | None = None,
        importance: str | None = None,
    ) -> None:
        """Send execution log to backend, tagged with Attention Budget metadata (RFC-0005 §1).

        Args:
            execution_id: Execution ID
            level: Log level (DEBUG, INFO, WARNING, ERROR)
            message: Log message
            agent: Agent name (if applicable)
            event_type: Optional structured event type (e.g. "tool.log", "run.completed")
            visibility: Explicit "human" | "audit" | "debug" (overrides the derived default)
            importance: Explicit "low" | "normal" | "high" | "blocking"
        """
        metadata = default_run_event_metadata(
            event_type=event_type, level=level, visibility=visibility, importance=importance
        )
        destination = self.DEST_EXECUTION_LOGS.format(execution_id=execution_id)
        await self.stomp.send(
            destination,
            {
                "type": MessageType.EXECUTION_LOG.value,
                "execution_id": execution_id,
                "level": level,
                "visibility": metadata.visibility,
                "importance": metadata.importance,
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

        # Tear down the warm container pool if it was started
        if self.warm_pool is not None:
            await self.warm_pool.shutdown()
            self.warm_pool = None

        # Stop all streaming sessions
        await stream_manager.stop_all()

        # Disconnect STOMP
        await self.stomp.disconnect()

        # Clean up PID file
        self._remove_pid_file()

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
