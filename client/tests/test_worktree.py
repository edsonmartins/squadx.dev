"""Tests for git worktree management module."""

import os
from unittest.mock import MagicMock, patch, call

import pytest

from squadx_client.git.worktree import WorktreeInfo, WorktreeManager


class TestWorktreeInfo:
    """Test WorktreeInfo dataclass."""

    def test_exists_returns_true_for_existing_dir(self, tmp_path):
        info = WorktreeInfo(path=str(tmp_path), branch="test", agent_name="a1")
        assert info.exists is True

    def test_exists_returns_false_for_missing_dir(self):
        info = WorktreeInfo(path="/nonexistent/path", branch="test", agent_name="a1")
        assert info.exists is False

    def test_default_task_id_is_none(self):
        info = WorktreeInfo(path="/tmp/wt", branch="b", agent_name="a")
        assert info.task_id is None

    def test_task_id_set(self):
        info = WorktreeInfo(path="/tmp/wt", branch="b", agent_name="a", task_id="t1")
        assert info.task_id == "t1"


class TestWorktreeManager:
    """Test WorktreeManager operations."""

    def _make_manager(self, repo_path="/fake/repo"):
        return WorktreeManager(repo_path)

    @patch("squadx_client.git.worktree.subprocess.run")
    @patch("squadx_client.git.worktree.os.makedirs")
    @patch("squadx_client.git.worktree.os.path.isdir", return_value=False)
    def test_create_worktree(self, mock_isdir, mock_makedirs, mock_run):
        mock_run.return_value = MagicMock(returncode=0)
        mgr = self._make_manager()
        info = mgr.create("frontend", task_id="t1", base_branch="main")

        assert info.agent_name == "frontend"
        assert info.branch == "squadx/t1/frontend"
        assert info.task_id == "t1"
        assert "frontend" in info.path
        mock_run.assert_called_once()
        assert mgr.get("frontend") is info

    @patch("squadx_client.git.worktree.subprocess.run")
    @patch("squadx_client.git.worktree.os.makedirs")
    @patch("squadx_client.git.worktree.os.path.isdir", return_value=False)
    def test_create_worktree_default_task_id(self, mock_isdir, mock_makedirs, mock_run):
        mock_run.return_value = MagicMock(returncode=0)
        mgr = self._make_manager()
        info = mgr.create("backend")
        assert info.branch == "squadx/default/backend"

    @patch("squadx_client.git.worktree.subprocess.run")
    @patch("squadx_client.git.worktree.os.makedirs")
    @patch("squadx_client.git.worktree.os.path.isdir", return_value=False)
    def test_create_worktree_fallback_on_existing_branch(self, mock_isdir, mock_makedirs, mock_run):
        """When creating with -b fails (branch exists), should try without -b."""
        from subprocess import CalledProcessError
        mock_run.side_effect = [
            CalledProcessError(1, "git"),  # first attempt fails
            MagicMock(returncode=0),        # fallback succeeds
        ]
        mgr = self._make_manager()
        info = mgr.create("frontend", task_id="t1")
        assert info.agent_name == "frontend"
        assert mock_run.call_count == 2

    @patch("squadx_client.git.worktree.subprocess.run")
    def test_checkpoint_with_changes(self, mock_run):
        mgr = self._make_manager()
        # Manually register a worktree
        info = WorktreeInfo(path="/fake/repo/.worktrees/frontend", branch="b", agent_name="frontend")
        mgr._registry["frontend"] = info

        # git add succeeds, git diff --cached returns 1 (changes exist), git commit succeeds
        mock_run.side_effect = [
            MagicMock(returncode=0),  # git add
            MagicMock(returncode=1),  # git diff --cached (changes exist)
            MagicMock(returncode=0),  # git commit
        ]

        with patch.object(info, "exists", new_callable=lambda: property(lambda self: True)):
            # Override exists to return True
            pass

        # Directly patch os.path.isdir for the exists check
        with patch("squadx_client.git.worktree.os.path.isdir", return_value=True):
            result = mgr.checkpoint("frontend", "test checkpoint")

        assert result is True
        assert mock_run.call_count == 3

    @patch("squadx_client.git.worktree.subprocess.run")
    def test_checkpoint_no_changes(self, mock_run):
        mgr = self._make_manager()
        info = WorktreeInfo(path="/fake/repo/.worktrees/frontend", branch="b", agent_name="frontend")
        mgr._registry["frontend"] = info

        mock_run.side_effect = [
            MagicMock(returncode=0),  # git add
            MagicMock(returncode=0),  # git diff --cached (no changes)
        ]

        with patch("squadx_client.git.worktree.os.path.isdir", return_value=True):
            result = mgr.checkpoint("frontend")

        assert result is False

    def test_checkpoint_unknown_agent(self):
        mgr = self._make_manager()
        assert mgr.checkpoint("nonexistent") is False

    @patch("squadx_client.git.worktree.subprocess.run")
    def test_merge(self, mock_run):
        mgr = self._make_manager()
        info = WorktreeInfo(path="/fake/repo/.worktrees/frontend", branch="squadx/t1/frontend", agent_name="frontend")
        mgr._registry["frontend"] = info

        # checkpoint calls (add, diff --cached returns 0 = no changes), then merge
        mock_run.side_effect = [
            MagicMock(returncode=0),  # git add (checkpoint)
            MagicMock(returncode=0),  # git diff --cached (no changes in checkpoint)
            MagicMock(returncode=0),  # git merge
        ]

        with patch("squadx_client.git.worktree.os.path.isdir", return_value=True):
            result = mgr.merge("frontend")

        assert result is True

    def test_merge_unknown_agent(self):
        mgr = self._make_manager()
        assert mgr.merge("nonexistent") is False

    @patch("squadx_client.git.worktree.subprocess.run")
    def test_cleanup(self, mock_run):
        mgr = self._make_manager()
        info = WorktreeInfo(path="/fake/repo/.worktrees/frontend", branch="squadx/t1/frontend", agent_name="frontend")
        mgr._registry["frontend"] = info

        mock_run.side_effect = [
            MagicMock(returncode=0),  # git worktree remove
            MagicMock(returncode=0),  # git branch -D
        ]

        result = mgr.cleanup("frontend")
        assert result is True
        assert mgr.get("frontend") is None

    @patch("squadx_client.git.worktree.subprocess.run")
    def test_cleanup_failure(self, mock_run):
        from subprocess import CalledProcessError
        mgr = self._make_manager()
        info = WorktreeInfo(path="/fake/repo/.worktrees/frontend", branch="b", agent_name="frontend")
        mgr._registry["frontend"] = info
        mock_run.side_effect = CalledProcessError(1, "git")
        result = mgr.cleanup("frontend")
        assert result is False

    def test_list_worktrees(self):
        mgr = self._make_manager()
        mgr._registry["a1"] = WorktreeInfo(path="/p1", branch="b1", agent_name="a1")
        mgr._registry["a2"] = WorktreeInfo(path="/p2", branch="b2", agent_name="a2")
        assert len(mgr.list_worktrees()) == 2

    def test_get_returns_none_for_unknown(self):
        mgr = self._make_manager()
        assert mgr.get("nonexistent") is None
