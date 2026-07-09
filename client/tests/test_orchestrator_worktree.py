"""Tests for worktree integration in orchestrator/nodes.py.

Verifies the three seams:
1. execute_subtask creates a per-subtask worktree when SQUADX_USE_WORKTREES=true
   and the workspace is a git repo.
2. The branch name lands in state.subtask_worktrees on a successful checkpoint.
3. commit_changes merges each worktree branch via GitManager.merge_branch().
"""

import subprocess
from unittest.mock import AsyncMock, MagicMock, patch

import pytest
from langchain_core.messages import AIMessage

from squadx_client.orchestrator.state import OrchestratorState, SubTask, TaskPlan


def _make_state(*, subtask_id="s1", agent_type="backend", task_id=42):
    subtask = SubTask(
        id=subtask_id,
        title=f"Subtask {subtask_id}",
        description="Do the thing",
        agent_type=agent_type,
    )
    plan = TaskPlan(
        analysis="",
        approach="",
        subtasks=[subtask],
        execution_order=[subtask_id],
    )
    return OrchestratorState(
        task_id=task_id,
        task={"title": "Build API", "description": "x"},
        plan=plan,
        messages=[AIMessage(content="init")],
    )


class TestIsGitRepo:
    def test_true_for_git_repo(self, temp_workspace):
        from squadx_client.orchestrator.nodes import _is_git_repo

        assert _is_git_repo(str(temp_workspace)) is True

    def test_false_for_non_git_dir(self, tmp_path):
        from squadx_client.orchestrator.nodes import _is_git_repo

        assert _is_git_repo(str(tmp_path)) is False

    def test_false_for_nonexistent_path(self, tmp_path):
        from squadx_client.orchestrator.nodes import _is_git_repo

        assert _is_git_repo(str(tmp_path / "nope")) is False


class TestExecuteSubtaskWorktree:
    @pytest.mark.asyncio
    async def test_creates_worktree_when_enabled(self, temp_workspace):
        """use_worktrees=True + git workspace → branch recorded in state."""
        # temp_workspace fixture only does `git init`; we need an initial commit
        # so `git worktree add ... -b branch main` has a base branch to fork from.
        subprocess.run(["git", "config", "user.email", "t@t.com"], cwd=temp_workspace, check=True)
        subprocess.run(["git", "config", "user.name", "t"], cwd=temp_workspace, check=True)
        subprocess.run(
            ["git", "commit", "--allow-empty", "-m", "init"],
            cwd=temp_workspace,
            check=True,
            capture_output=True,
        )

        state = _make_state(subtask_id="s1", agent_type="backend", task_id=99)

        mock_sandbox = MagicMock()
        mock_sandbox.start = AsyncMock(return_value=True)
        mock_sandbox.cleanup = AsyncMock(return_value=True)
        mock_sandbox.live_join_code = None
        mock_sandbox.vnc_port = None

        mock_agent = MagicMock()
        mock_agent.execute = AsyncMock(
            return_value={"output": "done", "files_modified": ["a.py"]}
        )

        with (
            patch("squadx_client.orchestrator.nodes.settings") as mock_settings,
            patch("squadx_client.orchestrator.nodes.create_agent", return_value=mock_agent),
            patch("squadx_client.orchestrator.nodes.AgentSandbox", return_value=mock_sandbox),
        ):
            mock_settings.workspace_path = str(temp_workspace)
            mock_settings.enable_sandbox = True
            mock_settings.use_worktrees = True
            mock_settings.agent_image = "squadx/agent:test"
            mock_settings.agent_memory_limit = "1g"
            mock_settings.agent_cpu_limit = 1.0
            mock_settings.enable_vnc = False

            from squadx_client.orchestrator.nodes import execute_subtask

            result = await execute_subtask(state)

        # Branch recorded; unique per subtask
        assert "s1" in state.subtask_worktrees
        branch = state.subtask_worktrees["s1"]
        assert branch == "squadx/99/backend-s1"

        # The worktree dir was actually created on disk
        wt_dir = temp_workspace / ".worktrees" / "backend-s1"
        assert wt_dir.is_dir()

        # And it has its own branch checked out
        out = subprocess.run(
            ["git", "-C", str(wt_dir), "rev-parse", "--abbrev-ref", "HEAD"],
            capture_output=True,
            text=True,
            check=True,
        )
        assert out.stdout.strip() == "squadx/99/backend-s1"

        # Subtask counted as completed
        assert "s1" in result["completed_subtasks"]

    @pytest.mark.asyncio
    async def test_fallback_when_workspace_not_git(self, tmp_path):
        """use_worktrees=True but workspace isn't git → graceful fallback, no branch."""
        state = _make_state()
        workspace = tmp_path / "no-git-here"
        workspace.mkdir()

        mock_sandbox = MagicMock()
        mock_sandbox.start = AsyncMock(return_value=True)
        mock_sandbox.cleanup = AsyncMock(return_value=True)
        mock_sandbox.live_join_code = None
        mock_sandbox.vnc_port = None
        mock_agent = MagicMock()
        mock_agent.execute = AsyncMock(
            return_value={"output": "done", "files_modified": []}
        )

        with (
            patch("squadx_client.orchestrator.nodes.settings") as mock_settings,
            patch("squadx_client.orchestrator.nodes.create_agent", return_value=mock_agent),
            patch("squadx_client.orchestrator.nodes.AgentSandbox", return_value=mock_sandbox),
        ):
            mock_settings.workspace_path = str(workspace)
            mock_settings.enable_sandbox = True
            mock_settings.use_worktrees = True
            mock_settings.agent_image = "squadx/agent:test"
            mock_settings.agent_memory_limit = "1g"
            mock_settings.agent_cpu_limit = 1.0
            mock_settings.enable_vnc = False

            from squadx_client.orchestrator.nodes import execute_subtask

            await execute_subtask(state)

        assert state.subtask_worktrees == {}

    @pytest.mark.asyncio
    async def test_no_worktree_when_disabled(self, temp_workspace):
        """use_worktrees=False → no worktree, no branch recorded."""
        state = _make_state()
        mock_sandbox = MagicMock()
        mock_sandbox.start = AsyncMock(return_value=True)
        mock_sandbox.cleanup = AsyncMock(return_value=True)
        mock_sandbox.live_join_code = None
        mock_sandbox.vnc_port = None
        mock_agent = MagicMock()
        mock_agent.execute = AsyncMock(
            return_value={"output": "done", "files_modified": []}
        )

        with (
            patch("squadx_client.orchestrator.nodes.settings") as mock_settings,
            patch("squadx_client.orchestrator.nodes.create_agent", return_value=mock_agent),
            patch("squadx_client.orchestrator.nodes.AgentSandbox", return_value=mock_sandbox),
        ):
            mock_settings.workspace_path = str(temp_workspace)
            mock_settings.enable_sandbox = True
            mock_settings.use_worktrees = False
            mock_settings.agent_image = "squadx/agent:test"
            mock_settings.agent_memory_limit = "1g"
            mock_settings.agent_cpu_limit = 1.0
            mock_settings.enable_vnc = False

            from squadx_client.orchestrator.nodes import execute_subtask

            await execute_subtask(state)

        assert state.subtask_worktrees == {}
        # Worktrees dir was never created
        assert not (temp_workspace / ".worktrees").exists()


class TestCommitChangesMergesWorktrees:
    @pytest.mark.asyncio
    async def test_merge_branch_called_per_subtask(self, temp_workspace):
        """commit_changes iterates state.subtask_worktrees and merges each branch."""
        state = _make_state()
        state.completed_subtasks = ["s1", "s2"]
        state.subtask_worktrees = {
            "s1": "squadx/42/backend-s1",
            "s2": "squadx/42/frontend-s2",
        }
        state.final_result = "All done"
        state.review_verdict = "approve"

        # Make at least one file appear modified so commit_changes has something to commit
        state.plan.subtasks[0].files_modified = ["a.py"]

        mock_git = MagicMock()
        mock_git.merge_branch = MagicMock(return_value=True)
        mock_git.create_branch = MagicMock(return_value=True)
        mock_git.commit = MagicMock(return_value="abc1234")

        with (
            patch("squadx_client.orchestrator.nodes.GitManager", return_value=mock_git),
            patch("squadx_client.orchestrator.nodes.settings") as mock_settings,
        ):
            mock_settings.git_user_name = "Test"
            mock_settings.git_user_email = "t@t.com"
            mock_settings.workspace_path = str(temp_workspace)
            mock_settings.use_worktrees = True

            from squadx_client.orchestrator.nodes import commit_changes

            await commit_changes(state)

        # Both branches got merged before the final commit
        merged = {call.args[0] for call in mock_git.merge_branch.call_args_list}
        assert merged == {"squadx/42/backend-s1", "squadx/42/frontend-s2"}

        # And each merge happened with --no-ff so the per-subtask history survives
        for call_obj in mock_git.merge_branch.call_args_list:
            assert call_obj.kwargs.get("no_ff") is True

        # create_branch + commit still run after the merges
        mock_git.create_branch.assert_called_once()
        mock_git.commit.assert_called_once()

    @pytest.mark.asyncio
    async def test_merge_failure_does_not_block_commit(self, temp_workspace):
        """A single merge returning False is logged but the commit still proceeds."""
        state = _make_state()
        state.completed_subtasks = ["s1"]
        state.subtask_worktrees = {"s1": "squadx/42/backend-s1"}
        state.final_result = "ok"
        state.review_verdict = "approve"
        state.plan.subtasks[0].files_modified = ["x.py"]

        mock_git = MagicMock()
        mock_git.merge_branch = MagicMock(return_value=False)
        mock_git.create_branch = MagicMock(return_value=True)
        mock_git.commit = MagicMock(return_value="abc")

        with (
            patch("squadx_client.orchestrator.nodes.GitManager", return_value=mock_git),
            patch("squadx_client.orchestrator.nodes.settings") as mock_settings,
        ):
            mock_settings.git_user_name = "Test"
            mock_settings.git_user_email = "t@t.com"
            mock_settings.workspace_path = str(temp_workspace)
            mock_settings.use_worktrees = True

            from squadx_client.orchestrator.nodes import commit_changes

            await commit_changes(state)

        mock_git.merge_branch.assert_called_once()
        mock_git.commit.assert_called_once()  # still commits

    @pytest.mark.asyncio
    async def test_no_worktrees_skips_merge(self, temp_workspace):
        """state.subtask_worktrees empty → no merge_branch calls."""
        state = _make_state()
        state.completed_subtasks = ["s1"]
        state.subtask_worktrees = {}
        state.final_result = "ok"
        state.review_verdict = "approve"
        state.plan.subtasks[0].files_modified = ["x.py"]

        mock_git = MagicMock()
        mock_git.merge_branch = MagicMock()
        mock_git.create_branch = MagicMock(return_value=True)
        mock_git.commit = MagicMock(return_value="abc")

        with (
            patch("squadx_client.orchestrator.nodes.GitManager", return_value=mock_git),
            patch("squadx_client.orchestrator.nodes.settings") as mock_settings,
        ):
            mock_settings.git_user_name = "Test"
            mock_settings.git_user_email = "t@t.com"
            mock_settings.workspace_path = str(temp_workspace)
            mock_settings.use_worktrees = True

            from squadx_client.orchestrator.nodes import commit_changes

            await commit_changes(state)

        mock_git.merge_branch.assert_not_called()
        mock_git.commit.assert_called_once()
