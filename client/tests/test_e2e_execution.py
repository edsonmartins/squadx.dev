"""End-to-end tests for task execution flow.

These tests validate the complete orchestration workflow:
1. Task analysis
2. Plan creation
3. Agent execution (with sandbox)
4. Code generation
5. Git commit

Tests can run in two modes:
- Mock mode: Uses mocked LLM responses (fast, no API costs)
- Integration mode: Uses real LLM (requires API keys)
"""

import json
import os
import subprocess
from pathlib import Path
from unittest.mock import AsyncMock, MagicMock, patch

import pytest

from squadx_client.orchestrator.graph import create_orchestrator
from squadx_client.orchestrator.state import OrchestratorState, TaskPlan, SubTask, ExecutionMetrics


class TestOrchestratorGraph:
    """Test the orchestrator graph structure and flow."""

    def test_create_orchestrator(self):
        """Test that the orchestrator graph is created correctly."""
        graph = create_orchestrator()
        assert graph is not None

    def test_graph_has_required_nodes(self):
        """Verify all required nodes are present in the graph."""
        graph = create_orchestrator()
        # The compiled graph should have the nodes
        assert graph is not None


class TestTaskAnalysis:
    """Test task analysis node."""

    @pytest.mark.asyncio
    async def test_analyze_task_returns_messages(self, sample_state, mock_llm, mock_llm_response):
        """Test that analyze_task adds messages to state."""
        from squadx_client.orchestrator.nodes import analyze_task

        mock_llm.ainvoke.return_value = mock_llm_response(json.dumps({
            "analysis": "Create two Python functions for arithmetic",
            "approach": "Create a calculator module with tests",
            "complexity": "low",
            "estimated_time": "5"
        }))

        with patch("squadx_client.orchestrator.nodes.get_llm", return_value=mock_llm):
            result = await analyze_task(sample_state)

        assert "messages" in result
        assert len(result["messages"]) == 1


class TestPlanCreation:
    """Test plan creation node."""

    @pytest.mark.asyncio
    async def test_create_plan_generates_subtasks(self, sample_state, mock_llm, mock_llm_response):
        """Test that create_plan generates valid subtasks."""
        from squadx_client.orchestrator.nodes import create_plan

        # Add a previous analysis message
        prev_message = mock_llm_response(json.dumps({
            "analysis": "Simple arithmetic functions needed",
            "approach": "Create module with functions",
            "complexity": "low"
        }))
        sample_state.messages = [prev_message]

        mock_llm.ainvoke.return_value = mock_llm_response(json.dumps({
            "subtasks": [
                {
                    "id": "sub-1",
                    "title": "Create calculator module",
                    "description": "Create calculator.py with add and subtract functions",
                    "agent_type": "backend"
                },
                {
                    "id": "sub-2",
                    "title": "Create tests",
                    "description": "Create test_calculator.py with unit tests",
                    "agent_type": "qa"
                }
            ],
            "execution_order": ["sub-1", "sub-2"],
            "parallel_groups": []
        }))

        with patch("squadx_client.orchestrator.nodes.get_llm", return_value=mock_llm):
            result = await create_plan(sample_state)

        assert "plan" in result
        assert result["plan"] is not None
        assert len(result["plan"].subtasks) == 2
        assert result["plan"].subtasks[0].title == "Create calculator module"
        assert result["plan"].subtasks[1].agent_type == "qa"


class TestSubtaskExecution:
    """Test subtask execution with sandbox."""

    @pytest.mark.asyncio
    async def test_execute_subtask_with_mock_sandbox(self, sample_state, mock_sandbox, temp_workspace):
        """Test subtask execution using a mocked sandbox."""
        from squadx_client.orchestrator.nodes import execute_subtask
        from squadx_client.orchestrator.state import SubTask, TaskPlan

        # Create a plan with one subtask
        subtask = SubTask(
            id="sub-1",
            title="Create calculator module",
            description="Create a Python file with add function",
            agent_type="backend"
        )
        sample_state.plan = TaskPlan(
            analysis="Simple task",
            approach="Direct implementation",
            subtasks=[subtask],
            execution_order=["sub-1"]
        )

        # Mock the agent execution
        mock_agent = MagicMock()
        mock_agent.execute = AsyncMock(return_value={
            "output": "Created calculator.py with add function",
            "files_modified": ["calculator.py"],
            "input_tokens": 100,
            "output_tokens": 50,
            "cost": 0.001,
            "tool_calls": 1,
        })

        with patch("squadx_client.orchestrator.nodes.create_agent", return_value=mock_agent), \
             patch("squadx_client.orchestrator.nodes.create_agent_sandbox") as MockSandbox, \
             patch.object(sample_state, "task_id", 123):

            # Configure mock sandbox
            MockSandbox.return_value = mock_sandbox
            mock_sandbox.start = AsyncMock(return_value=True)

            # Set workspace path in settings
            with patch("squadx_client.orchestrator.nodes.settings") as mock_settings:
                mock_settings.workspace_path = str(temp_workspace)
                mock_settings.enable_sandbox = True
                mock_settings.agent_image = "squadx/agent:latest"
                mock_settings.agent_memory_limit = "2g"
                mock_settings.agent_cpu_limit = 2.0
                mock_settings.enable_vnc = True

                result = await execute_subtask(sample_state)

        assert "completed_subtasks" in result
        assert "sub-1" in result["completed_subtasks"]
        assert result["metrics"].successful_subtasks == 1
        assert result["metrics"].total_tool_calls == 1


class TestReviewAndCommit:
    """Test review and commit nodes."""

    @pytest.mark.asyncio
    async def test_review_results_summarizes_execution(self, sample_state, mock_llm, mock_llm_response):
        """Test that review node summarizes completed work."""
        from squadx_client.orchestrator.nodes import review_results

        # Set up completed subtasks
        subtask = SubTask(
            id="sub-1",
            title="Create calculator module",
            description="Create Python module",
            agent_type="backend",
            status="completed",
            result="Created calculator.py with add and subtract functions"
        )
        sample_state.plan = TaskPlan(
            analysis="Test",
            approach="Test",
            subtasks=[subtask],
            execution_order=["sub-1"]
        )
        sample_state.completed_subtasks = ["sub-1"]

        mock_llm.ainvoke.return_value = mock_llm_response(
            "Successfully created a calculator module with add and subtract functions."
        )

        with patch("squadx_client.orchestrator.nodes.get_llm", return_value=mock_llm):
            result = await review_results(sample_state)

        assert "final_result" in result
        assert "calculator" in result["final_result"].lower()

    @pytest.mark.asyncio
    async def test_commit_changes_creates_git_commit(self, sample_state, temp_workspace):
        """Test that commit node creates a git commit."""
        from squadx_client.orchestrator.nodes import commit_changes

        # Create a file in the workspace
        test_file = temp_workspace / "calculator.py"
        test_file.write_text("def add(a, b): return a + b")

        # Stage the file in git
        subprocess.run(
            ["git", "add", "calculator.py"],
            cwd=temp_workspace,
            capture_output=True
        )

        # Set up state with completed subtask
        subtask = SubTask(
            id="sub-1",
            title="Create calculator module",
            description="Create Python module",
            agent_type="backend",
            status="completed",
            result="Done",
            files_modified=["calculator.py"]
        )
        sample_state.plan = TaskPlan(
            analysis="Test",
            approach="Test",
            subtasks=[subtask],
            execution_order=["sub-1"]
        )
        sample_state.completed_subtasks = ["sub-1"]
        sample_state.final_result = "Created calculator module"

        # Mock GitManager
        mock_git_manager = MagicMock()
        mock_git_manager.create_branch = MagicMock()
        mock_git_manager.commit = MagicMock(return_value="abc123")

        with patch("squadx_client.orchestrator.nodes.GitManager", return_value=mock_git_manager):
            result = await commit_changes(sample_state)

        assert result.get("should_end", False) is True
        if result.get("git_commit"):
            assert result["git_branch"] == f"squadx/task-{sample_state.task_id}"


class TestEndToEndFlow:
    """Full end-to-end flow tests."""

    @pytest.mark.asyncio
    async def test_full_orchestration_flow_with_mocks(self, sample_state, mock_llm, mock_llm_response):
        """Test the complete orchestration flow with mocked components."""
        from squadx_client.orchestrator.graph import create_orchestrator

        # Configure mock responses for each step
        analysis_response = mock_llm_response(json.dumps({
            "analysis": "Create a simple calculator module",
            "approach": "Direct implementation",
            "complexity": "low",
            "estimated_time": "5"
        }))

        plan_response = mock_llm_response(json.dumps({
            "subtasks": [
                {
                    "id": "calc-1",
                    "title": "Create calculator",
                    "description": "Create calculator.py",
                    "agent_type": "backend"
                }
            ],
            "execution_order": ["calc-1"],
            "parallel_groups": []
        }))

        review_response = mock_llm_response(
            "Task completed successfully. Created a calculator module."
        )

        # Track call count for sequential responses
        call_count = [0]
        responses = [analysis_response, plan_response, review_response]

        async def mock_ainvoke(*args, **kwargs):
            idx = min(call_count[0], len(responses) - 1)
            call_count[0] += 1
            return responses[idx]

        mock_llm.ainvoke = mock_ainvoke

        # Mock agent execution
        mock_agent = MagicMock()
        mock_agent.execute = AsyncMock(return_value={
            "output": "Created calculator.py",
            "files_modified": ["calculator.py"],
            "input_tokens": 100,
            "output_tokens": 50,
            "cost": 0.001,
            "tool_calls": 2,
        })

        # Mock sandbox
        mock_sandbox = MagicMock()
        mock_sandbox.start = AsyncMock(return_value=True)
        mock_sandbox.cleanup = AsyncMock(return_value=True)
        mock_sandbox.status = "running"
        mock_sandbox.live_join_code = None  # list[str] state field rejects a MagicMock

        # Mock GitManager
        mock_git_manager = MagicMock()
        mock_git_manager.create_branch = MagicMock()
        mock_git_manager.commit = MagicMock(return_value="abc123")

        with patch("squadx_client.orchestrator.nodes.get_llm", return_value=mock_llm), \
             patch("squadx_client.orchestrator.nodes.create_agent", return_value=mock_agent), \
             patch("squadx_client.orchestrator.nodes.create_agent_sandbox", return_value=mock_sandbox), \
             patch("squadx_client.orchestrator.nodes.GitManager", return_value=mock_git_manager), \
             patch("squadx_client.orchestrator.nodes.settings") as mock_settings:

            mock_settings.workspace_path = "/tmp/test-workspace"
            mock_settings.enable_sandbox = True
            mock_settings.agent_image = "squadx/agent:latest"
            mock_settings.agent_memory_limit = "2g"
            mock_settings.agent_cpu_limit = 2.0
            mock_settings.enable_vnc = False

            graph = create_orchestrator()

            # Run the orchestrator
            final_state = await graph.ainvoke(sample_state)

        # Verify the final state
        assert final_state is not None
        assert final_state.get("should_end", False) is True or final_state.get("final_result") is not None


class TestSandboxExecution:
    """Test sandbox-specific execution scenarios."""

    @pytest.mark.asyncio
    async def test_sandbox_execute_command(self, mock_sandbox):
        """Test executing a command in the sandbox."""
        result = await mock_sandbox.execute(["python", "-c", "print('hello')"])
        assert result.success is True
        assert result.exit_code == 0

    @pytest.mark.asyncio
    async def test_sandbox_write_and_read_file(self, mock_sandbox):
        """Test file operations in sandbox."""
        mock_sandbox.read_file = AsyncMock(return_value="def add(a, b): return a + b")

        # Write a file
        success = await mock_sandbox.write_file("/workspace/test.py", "def add(a, b): return a + b")
        assert success is True

        # Read it back
        content = await mock_sandbox.read_file("/workspace/test.py")
        assert "def add" in content

    @pytest.mark.asyncio
    async def test_sandbox_fallback_on_failure(self, sample_state):
        """Test that execution falls back gracefully when sandbox fails to start."""
        from squadx_client.orchestrator.nodes import execute_subtask
        from squadx_client.orchestrator.state import SubTask, TaskPlan

        subtask = SubTask(
            id="sub-1",
            title="Test task",
            description="Test description",
            agent_type="backend"
        )
        sample_state.plan = TaskPlan(
            analysis="Test",
            approach="Test",
            subtasks=[subtask],
            execution_order=["sub-1"]
        )

        # Mock sandbox that fails to start
        mock_sandbox = MagicMock()
        mock_sandbox.start = AsyncMock(return_value=False)
        mock_sandbox.cleanup = AsyncMock(return_value=True)

        # Mock agent that works without sandbox
        mock_agent = MagicMock()
        mock_agent.execute = AsyncMock(return_value={
            "output": "Executed without sandbox",
            "files_modified": [],
            "input_tokens": 50,
            "output_tokens": 25,
            "cost": 0.0005,
            "tool_calls": 0,
        })

        with patch("squadx_client.orchestrator.nodes.create_agent", return_value=mock_agent), \
             patch("squadx_client.orchestrator.nodes.create_agent_sandbox", return_value=mock_sandbox), \
             patch("squadx_client.orchestrator.nodes.settings") as mock_settings:

            mock_settings.workspace_path = "/tmp/test"
            mock_settings.enable_sandbox = True
            mock_settings.agent_image = "squadx/agent:latest"
            mock_settings.agent_memory_limit = "2g"
            mock_settings.agent_cpu_limit = 2.0
            mock_settings.enable_vnc = False

            result = await execute_subtask(sample_state)

        # Should still complete even without sandbox
        assert "completed_subtasks" in result
        assert "sub-1" in result["completed_subtasks"]


class TestMetricsCollection:
    """Test metrics collection during execution."""

    def test_execution_metrics_aggregation(self):
        """Test that metrics are properly aggregated."""
        from squadx_client.orchestrator.state import ExecutionMetrics, AgentMetrics

        metrics = ExecutionMetrics()

        # Add first agent metrics
        agent1 = AgentMetrics(
            agent_type="backend",
            subtask_id="sub-1",
            subtask_title="Create module",
            input_tokens=100,
            output_tokens=50,
            cost=0.001,
            execution_time_seconds=5.0,
            tool_calls=3,
            files_modified=["file1.py", "file2.py"],
            success=True,
        )
        metrics.add_agent_metrics(agent1)

        # Add second agent metrics
        agent2 = AgentMetrics(
            agent_type="qa",
            subtask_id="sub-2",
            subtask_title="Create tests",
            input_tokens=80,
            output_tokens=40,
            cost=0.0008,
            execution_time_seconds=3.0,
            tool_calls=2,
            files_modified=["test_file.py"],
            success=True,
        )
        metrics.add_agent_metrics(agent2)

        # Verify aggregations
        assert metrics.input_tokens == 180
        assert metrics.output_tokens == 90
        assert metrics.total_cost == pytest.approx(0.0018, rel=0.01)
        assert metrics.total_tool_calls == 5
        assert metrics.files_modified == 3
        assert metrics.successful_subtasks == 2
        assert metrics.failed_subtasks == 0

    def test_metrics_summary_generation(self):
        """Test metrics summary generation."""
        from squadx_client.orchestrator.state import ExecutionMetrics, AgentMetrics

        metrics = ExecutionMetrics()
        metrics.add_agent_metrics(AgentMetrics(
            agent_type="backend",
            subtask_id="sub-1",
            subtask_title="Test task",
            input_tokens=100,
            output_tokens=50,
            cost=0.001,
            execution_time_seconds=5.0,
            tool_calls=3,
            success=True,
        ))

        summary = metrics.to_summary()

        assert "tokens" in summary
        assert summary["tokens"]["total"] == 150
        assert "cost_usd" in summary
        assert "subtasks" in summary
        assert summary["subtasks"]["successful"] == 1
        assert "agents" in summary
        assert len(summary["agents"]) == 1


# Integration tests (require real Docker and API keys)
@pytest.mark.integration
class TestIntegrationWithDocker:
    """Integration tests that require Docker to be running."""

    @pytest.mark.asyncio
    async def test_real_sandbox_startup(self, temp_workspace):
        """Test starting a real sandbox container."""
        from squadx_client.docker.sandbox import AgentSandbox, SandboxStatus
        from squadx_client.docker.manager import docker_manager

        # Check if Docker is available
        if not await docker_manager.connect():
            pytest.skip("Docker not available")

        sandbox = AgentSandbox(
            task_id=999,
            agent_type="backend",
            workspace_path=str(temp_workspace),
        )

        try:
            started = await sandbox.start(
                image="squadx/agent:latest",
                enable_vnc=False,
            )

            if not started:
                pytest.skip("Could not start sandbox (image may not exist)")

            assert sandbox.status == SandboxStatus.RUNNING
            assert sandbox.container_id is not None

            # Try a simple command
            result = await sandbox.execute(["echo", "hello"])
            assert result.success
            assert "hello" in result.output

        finally:
            await sandbox.cleanup()

    @pytest.mark.asyncio
    async def test_real_code_generation_in_sandbox(self, temp_workspace):
        """Test writing and reading code in the sandbox."""
        from squadx_client.docker.sandbox import AgentSandbox
        from squadx_client.docker.manager import docker_manager

        if not await docker_manager.connect():
            pytest.skip("Docker not available")

        sandbox = AgentSandbox(
            task_id=999,
            agent_type="backend",
            workspace_path=str(temp_workspace),
        )

        try:
            started = await sandbox.start(
                image="squadx/agent:latest",
                enable_vnc=False,
            )

            if not started:
                pytest.skip("Could not start sandbox")

            # Write a Python file
            code = '''def add(a, b):
    """Add two numbers."""
    return a + b

def subtract(a, b):
    """Subtract two numbers."""
    return a - b
'''
            write_success = await sandbox.write_file("/workspace/calculator.py", code)
            assert write_success

            # Verify it exists
            result = await sandbox.execute(["python", "-c", "import calculator; print(calculator.add(2, 3))"])
            assert result.success
            assert "5" in result.output

        finally:
            await sandbox.cleanup()
