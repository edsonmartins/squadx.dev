"""Tests for the workspace MCP bridge tool layer."""

from unittest.mock import AsyncMock, patch

import pytest

from squadx_client.mcp import bridge


@pytest.fixture
def mock_client():
    client = AsyncMock()
    bridge.configure(client)
    yield client
    bridge.configure(None)  # type: ignore[arg-type]


class TestBridgeTools:
    async def test_get_change_delegates(self, mock_client):
        mock_client.get_change.return_value = {"id": 5}
        assert await bridge.get_change_impl() == {"id": 5}

    async def test_get_tasks_delegates_with_assignee(self, mock_client):
        mock_client.get_tasks.return_value = [{"id": 1}]
        assert await bridge.get_tasks_impl("Backend Agent") == [{"id": 1}]
        mock_client.get_tasks.assert_awaited_once_with("Backend Agent")

    async def test_update_task_status_delegates(self, mock_client):
        mock_client.update_task_status.return_value = {"ok": True}
        await bridge.update_task_status_impl(42, "implementado", "done")
        mock_client.update_task_status.assert_awaited_once_with(42, "implementado", "done")

    async def test_report_blocker_delegates(self, mock_client):
        await bridge.report_blocker_impl(42, "waiting on API")
        mock_client.report_blocker.assert_awaited_once_with(42, "waiting on API")

    async def test_scaffold_tests_delegates(self, mock_client):
        await bridge.scaffold_tests_impl(9)
        mock_client.scaffold_tests.assert_awaited_once_with(9)

    async def test_unconfigured_client_raises(self):
        bridge.configure(None)  # type: ignore[arg-type]
        with pytest.raises(RuntimeError, match="not configured"):
            await bridge.get_change_impl()


class TestMain:
    def test_main_exits_without_token(self):
        with patch.object(bridge.settings, "workspace_token", None):
            with patch("sys.argv", ["squadx-workspace-mcp"]):
                with pytest.raises(SystemExit) as excinfo:
                    bridge.main()
        assert excinfo.value.code == 1

    def test_mcp_server_registers_six_tools(self):
        # Tool registry is built at import time from the @mcp.tool decorators.
        import anyio

        tools = anyio.run(bridge.mcp.list_tools)
        names = {t.name for t in tools}
        assert names == {
            "get_change",
            "get_tasks",
            "update_task_status",
            "report_blocker",
            "materialize_change",
            "scaffold_tests",
        }
