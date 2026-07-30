"""Pytest configuration and fixtures for SquadX Client tests."""

import os
import shutil
import subprocess
import tempfile
from pathlib import Path
from typing import Generator
from unittest.mock import AsyncMock, MagicMock, patch

import pytest
from langchain_core.messages import AIMessage

from squadx_client.orchestrator.state import OrchestratorState, TaskPlan, SubTask


@pytest.fixture
def temp_workspace() -> Generator[Path, None, None]:
    """Create a temporary workspace directory for testing."""
    workspace = tempfile.mkdtemp(prefix="squadx-test-")
    workspace_path = Path(workspace)

    # Initialize as a git repository
    subprocess.run(["git", "init", "-q"], cwd=workspace, capture_output=True)
    subprocess.run(["git", "config", "user.email", "test@test.com"], cwd=workspace, capture_output=True)
    subprocess.run(["git", "config", "user.name", "Test"], cwd=workspace, capture_output=True)

    yield workspace_path

    # Cleanup
    shutil.rmtree(workspace, ignore_errors=True)


@pytest.fixture
def sample_task() -> dict:
    """Create a sample task for testing."""
    return {
        "id": 123,
        "title": "Create a Python function to add two numbers",
        "description": """Create a simple Python module with:
1. A function called 'add' that takes two numbers and returns their sum
2. A function called 'subtract' that takes two numbers and returns their difference
3. Include docstrings for both functions
4. Add a simple test in a separate file""",
        "status": "IN_PROGRESS",
        "assignedAgentType": "backend",
    }


@pytest.fixture
def sample_state(sample_task: dict) -> OrchestratorState:
    """Create a sample orchestrator state."""
    return OrchestratorState(
        task_id=sample_task["id"],
        task=sample_task,
    )


@pytest.fixture
def mock_llm_response():
    """Create a mock LLM response factory that returns real AIMessage objects."""
    def _create_response(content: str):
        return AIMessage(content=content)
    return _create_response


@pytest.fixture
def mock_llm(mock_llm_response):
    """Create a mock LLM for testing without API calls."""
    mock = AsyncMock()
    mock.ainvoke = AsyncMock()
    return mock


@pytest.fixture
def mock_docker_manager():
    """Create a mock Docker manager."""
    manager = MagicMock()
    manager.client = MagicMock()
    manager.connect = AsyncMock(return_value=True)
    manager.create_container = AsyncMock(return_value="container-123")
    manager.start_container = AsyncMock(return_value=True)
    manager.stop_container = AsyncMock(return_value=True)
    manager.remove_container = AsyncMock(return_value=True)
    manager.exec_command = AsyncMock(return_value=(0, "Success"))
    manager.get_vnc_port = AsyncMock(return_value=5900)
    manager.get_container_status = AsyncMock(return_value="running")
    return manager


def make_docker_session(
    *,
    task_id: int = 1,
    agent_type: str = "backend",
    workspace_path: str = "/tmp/ws",
    manager=None,
    network_policy: str | None = None,
    enable_live_streaming: bool = False,
    runtime=None,
):
    """Build a real DockerSandboxSession (factory path) for unit tests."""
    from squadx_client.sandbox.docker_backend import DockerSandboxBackend

    kwargs = {
        "task_id": task_id,
        "agent_type": agent_type,
        "workspace_path": workspace_path,
        "network_policy": network_policy,
        "enable_live_streaming": enable_live_streaming,
    }
    backend = DockerSandboxBackend(manager=manager)
    session = backend.create_session(**kwargs)
    if runtime is not None:
        session.inner.runtime = runtime
    return session


@pytest.fixture
def mock_sandbox(mock_docker_manager):
    """Create a mock sandbox for testing."""
    from squadx_client.docker.sandbox import AgentSandbox, SandboxResult, SandboxStatus

    sandbox = MagicMock(spec=AgentSandbox)
    sandbox.status = SandboxStatus.RUNNING
    sandbox.container_id = "container-123"
    sandbox.vnc_port = 5900
    sandbox.live_join_code = None
    sandbox.manager = mock_docker_manager

    # Mock async methods
    sandbox.start = AsyncMock(return_value=True)
    sandbox.stop = AsyncMock(return_value=True)
    sandbox.cleanup = AsyncMock(return_value=True)
    sandbox.is_running = AsyncMock(return_value=True)

    # Mock execute to return success
    sandbox.execute = AsyncMock(return_value=SandboxResult(
        success=True,
        exit_code=0,
        output="Command executed successfully",
        duration_seconds=0.5,
    ))

    sandbox.write_file = AsyncMock(return_value=True)
    sandbox.read_file = AsyncMock(return_value="file content")

    return sandbox


@pytest.fixture
def env_without_api_keys(monkeypatch):
    """Ensure no real API keys are used in tests."""
    monkeypatch.delenv("OPENAI_API_KEY", raising=False)
    monkeypatch.delenv("ANTHROPIC_API_KEY", raising=False)
    monkeypatch.setenv("SQUADX_LLM_PROVIDER", "mock")
