"""Tests for squadx_client.docker.manager module."""

from unittest.mock import AsyncMock, MagicMock, patch, PropertyMock

import pytest
from docker.errors import DockerException, NotFound, APIError

from squadx_client.docker.manager import DockerManager, ContainerConfig


@pytest.fixture
def manager():
    """Create a DockerManager with mocked settings."""
    with patch("squadx_client.docker.manager.settings") as mock_settings:
        mock_settings.supabase_url = ""
        mock_settings.supabase_anon_key = ""
        mock_settings.api_url = "http://localhost:8080"
        mgr = DockerManager()
    return mgr


@pytest.fixture
def connected_manager(manager):
    """Create a DockerManager with a mocked Docker client."""
    mock_client = MagicMock()
    mock_client.ping.return_value = True
    manager.client = mock_client
    return manager


class TestConnect:
    """Test Docker daemon connection."""

    @pytest.mark.asyncio
    async def test_connect_success(self, manager):
        mock_client = MagicMock()
        mock_client.ping.return_value = True
        with patch("squadx_client.docker.manager.docker.from_env", return_value=mock_client):
            result = await manager.connect()
        assert result is True
        assert manager.client is mock_client

    @pytest.mark.asyncio
    async def test_connect_failure(self, manager):
        with patch("squadx_client.docker.manager.docker.from_env", side_effect=DockerException("No docker")):
            result = await manager.connect()
        assert result is False

    @pytest.mark.asyncio
    async def test_disconnect(self, connected_manager):
        await connected_manager.disconnect()
        assert connected_manager.client is None


class TestCreateContainer:
    """Test container creation."""

    @pytest.mark.asyncio
    async def test_create_container_success(self, connected_manager):
        mock_container = MagicMock()
        mock_container.id = "abc123"
        mock_container.short_id = "abc123"
        connected_manager.client.containers.get.side_effect = NotFound("not found")
        connected_manager.client.containers.create.return_value = mock_container

        config = ContainerConfig(enable_hardening=False)
        result = await connected_manager.create_container(config, task_id=1, agent_type="backend")

        assert result == "abc123"
        assert "abc123" in connected_manager.containers

    @pytest.mark.asyncio
    async def test_create_container_no_client(self, manager):
        config = ContainerConfig()
        result = await manager.create_container(config, task_id=1, agent_type="backend")
        assert result is None

    @pytest.mark.asyncio
    async def test_create_container_replaces_existing(self, connected_manager):
        existing = MagicMock()
        connected_manager.client.containers.get.return_value = existing
        new_container = MagicMock()
        new_container.id = "new123"
        new_container.short_id = "new123"
        connected_manager.client.containers.create.return_value = new_container

        config = ContainerConfig(enable_hardening=False)
        result = await connected_manager.create_container(config, task_id=1, agent_type="backend")

        existing.remove.assert_called_once_with(force=True)
        assert result == "new123"


class TestContainerLifecycle:
    """Test start, stop, remove container operations."""

    @pytest.mark.asyncio
    async def test_start_container_success(self, connected_manager):
        mock_container = MagicMock()
        mock_container.short_id = "abc"
        connected_manager.client.containers.get.return_value = mock_container
        result = await connected_manager.start_container("abc123")
        assert result is True
        mock_container.start.assert_called_once()

    @pytest.mark.asyncio
    async def test_start_container_not_found(self, connected_manager):
        connected_manager.client.containers.get.side_effect = NotFound("gone")
        result = await connected_manager.start_container("nonexistent")
        assert result is False

    @pytest.mark.asyncio
    async def test_stop_container_success(self, connected_manager):
        mock_container = MagicMock()
        mock_container.short_id = "abc"
        connected_manager.client.containers.get.return_value = mock_container
        result = await connected_manager.stop_container("abc123", timeout=5)
        assert result is True
        mock_container.stop.assert_called_once_with(timeout=5)

    @pytest.mark.asyncio
    async def test_remove_container_success(self, connected_manager):
        mock_container = MagicMock()
        mock_container.short_id = "abc"
        connected_manager.client.containers.get.return_value = mock_container
        connected_manager.containers["abc123"] = mock_container

        result = await connected_manager.remove_container("abc123", force=True)
        assert result is True
        mock_container.remove.assert_called_once_with(force=True)
        assert "abc123" not in connected_manager.containers

    @pytest.mark.asyncio
    async def test_remove_container_no_client(self, manager):
        result = await manager.remove_container("abc123")
        assert result is False


class TestExecCommand:
    """Test command execution inside containers."""

    @pytest.mark.asyncio
    async def test_exec_command_success(self, connected_manager):
        mock_container = MagicMock()
        mock_result = MagicMock()
        mock_result.exit_code = 0
        mock_result.output = (b"hello\n", b"")
        mock_container.exec_run.return_value = mock_result
        connected_manager.client.containers.get.return_value = mock_container

        code, output = await connected_manager.exec_command("abc123", ["echo", "hello"])
        assert code == 0
        assert "hello" in output

    @pytest.mark.asyncio
    async def test_exec_command_no_client(self, manager):
        code, output = await manager.exec_command("abc123", ["ls"])
        assert code == -1
        assert "not connected" in output


class TestListRunningAgents:
    """Test listing running agent containers."""

    @pytest.mark.asyncio
    async def test_list_running_agents(self, connected_manager):
        mock_container = MagicMock()
        mock_container.id = "c1"
        mock_container.name = "squadx-agent-1-backend"
        mock_container.status = "running"
        mock_container.attrs = {"Created": "2025-01-01T00:00:00Z"}
        connected_manager.client.containers.list.return_value = [mock_container]

        result = await connected_manager.list_running_agents()
        assert len(result) == 1
        assert result[0]["id"] == "c1"
        assert result[0]["status"] == "running"
        assert result[0]["live_session"] is None

    @pytest.mark.asyncio
    async def test_list_running_agents_no_client(self, manager):
        result = await manager.list_running_agents()
        assert result == []
