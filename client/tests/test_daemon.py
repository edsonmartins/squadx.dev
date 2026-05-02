"""Tests for SquadX daemon task execution flow."""

from unittest.mock import AsyncMock, MagicMock, patch

import pytest

from squadx_client.daemon import SquadXDaemon


@pytest.fixture
def daemon():
    """Create a daemon with mocked transport and orchestrator dependencies."""
    with patch("squadx_client.daemon.StompClientManager") as mock_stomp_cls, \
         patch("squadx_client.daemon.create_orchestrator") as mock_create_orchestrator:
        mock_stomp = MagicMock()
        mock_stomp.send = AsyncMock()
        mock_stomp_cls.return_value = mock_stomp

        orchestrator = MagicMock()
        orchestrator.ainvoke = AsyncMock(return_value={
            "final_result": "Execution finished successfully",
            "git_branch": "feat/daemon-test",
            "git_commit": "abc123",
            "live_session_codes": [],
            "total_input_tokens": 12,
            "total_output_tokens": 21,
            "total_cost": 0.34,
        })
        mock_create_orchestrator.return_value = orchestrator

        daemon = SquadXDaemon(api_url="http://localhost:8080", token="test-token")
        daemon._send_task_status = AsyncMock()
        daemon._send_task_completed = AsyncMock()
        daemon._send_task_failed = AsyncMock()
        daemon._send_task_rejected = AsyncMock()
        return daemon


class TestExecuteTask:
    """Test execution behavior for backend-dispatched tasks."""

    @pytest.mark.asyncio
    async def test_reuses_brainsentry_session_from_task_payload(self, daemon):
        task_data = {
            "task_id": 42,
            "title": "Implement feature",
            "description": "Build the feature end to end",
            "execution_id": 99,
            "brain_sentry_session_id": "bs-session-123",
            "assigned_agent_id": 7,
        }

        with patch("squadx_client.daemon.BrainSentryClient") as mock_brainsentry_cls:
            brainsentry = MagicMock()
            brainsentry.start_session = AsyncMock()
            brainsentry.end_session = AsyncMock()
            brainsentry.close = AsyncMock()
            mock_brainsentry_cls.return_value = brainsentry

            await daemon._execute_task(42, task_data)

        brainsentry.start_session.assert_not_awaited()
        brainsentry.end_session.assert_awaited_once_with(
            "bs-session-123",
            status="completed",
            summary="Execution finished successfully",
        )
        daemon.orchestrator.ainvoke.assert_awaited_once()
        orchestrator_payload = daemon.orchestrator.ainvoke.await_args.args[0]
        assert orchestrator_payload["execution_id"] == 99
        assert orchestrator_payload["brainsentry_session_id"] == "bs-session-123"
        daemon._send_task_completed.assert_awaited_once()

    @pytest.mark.asyncio
    async def test_starts_brainsentry_session_when_missing(self, daemon):
        task_data = {
            "task_id": 77,
            "title": "Implement fallback",
            "description": "Feature without pre-created session",
            "execution_id": 123,
            "assigned_agent_id": 8,
        }

        with patch("squadx_client.daemon.BrainSentryClient") as mock_brainsentry_cls:
            brainsentry = MagicMock()
            brainsentry.start_session = AsyncMock(return_value="bs-session-new")
            brainsentry.end_session = AsyncMock()
            brainsentry.close = AsyncMock()
            mock_brainsentry_cls.return_value = brainsentry

            await daemon._execute_task(77, task_data)

        brainsentry.start_session.assert_awaited_once_with(
            "123",
            task_id="77",
            agent_id="8",
        )
        brainsentry.end_session.assert_awaited_once_with(
            "bs-session-new",
            status="completed",
            summary="Execution finished successfully",
        )
        daemon._send_task_completed.assert_awaited_once()

    @pytest.mark.asyncio
    async def test_reports_failure_and_ends_brainsentry_session(self, daemon):
        task_data = {
            "task_id": 88,
            "title": "Failing task",
            "description": "This should fail",
            "execution_id": 456,
            "brain_sentry_session_id": "bs-session-fail",
        }
        daemon.orchestrator.ainvoke = AsyncMock(side_effect=RuntimeError("boom"))

        with patch("squadx_client.daemon.BrainSentryClient") as mock_brainsentry_cls:
            brainsentry = MagicMock()
            brainsentry.start_session = AsyncMock()
            brainsentry.end_session = AsyncMock()
            brainsentry.close = AsyncMock()
            mock_brainsentry_cls.return_value = brainsentry

            await daemon._execute_task(88, task_data)

        brainsentry.end_session.assert_awaited_once_with(
            "bs-session-fail",
            status="failed",
            summary="boom",
        )
        daemon._send_task_failed.assert_awaited_once_with(88, "boom")

    @pytest.mark.asyncio
    async def test_smoke_execution_mode_skips_orchestrator_but_completes_flow(self, daemon):
        task_data = {
            "task_id": 99,
            "title": "Smoke task",
            "description": "Deterministic execution",
            "execution_id": 500,
            "brain_sentry_session_id": "bs-session-smoke",
        }

        with patch("squadx_client.daemon.BrainSentryClient") as mock_brainsentry_cls, \
             patch("squadx_client.daemon.settings") as mock_settings:
            brainsentry = MagicMock()
            brainsentry.start_session = AsyncMock()
            brainsentry.end_session = AsyncMock()
            brainsentry.close = AsyncMock()
            mock_brainsentry_cls.return_value = brainsentry

            mock_settings.smoke_execution_mode = True
            mock_settings.smoke_execution_delay_seconds = 0
            mock_settings.smoke_execution_summary = "Smoke execution completed successfully."
            mock_settings.workspace_path = "/tmp"

            await daemon._execute_task(99, task_data)

        daemon.orchestrator.ainvoke.assert_not_awaited()
        brainsentry.end_session.assert_awaited_once()
        completed_payload = daemon._send_task_completed.await_args.args[1]
        assert completed_payload["final_result"].startswith("Smoke execution completed successfully.")
