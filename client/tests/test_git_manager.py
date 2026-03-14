"""Tests for squadx_client.git.manager module."""

from pathlib import Path
from unittest.mock import MagicMock, patch, PropertyMock

import pytest

from squadx_client.git.manager import GitManager


@pytest.fixture
def mock_repo():
    """Create a mock git.Repo."""
    repo = MagicMock()
    repo.active_branch.name = "main"
    repo.branches = []
    repo.index = MagicMock()
    repo.git = MagicMock()
    repo.untracked_files = []
    return repo


@pytest.fixture
def git_manager(mock_repo):
    """Create a GitManager with a mocked repo."""
    with patch("squadx_client.git.manager.Repo", return_value=mock_repo):
        mgr = GitManager(repo_path="/fake/repo")
    return mgr


class TestInitialization:
    """Test GitManager initialization."""

    def test_valid_repo(self, git_manager, mock_repo):
        assert git_manager.is_valid_repo() is True
        assert git_manager.repo is mock_repo

    def test_invalid_repo(self):
        from git import InvalidGitRepositoryError

        with patch("squadx_client.git.manager.Repo", side_effect=InvalidGitRepositoryError("nope")):
            mgr = GitManager(repo_path="/not/a/repo")
        assert mgr.is_valid_repo() is False
        assert mgr.repo is None

    def test_get_current_branch(self, git_manager):
        assert git_manager.get_current_branch() == "main"

    def test_get_current_branch_no_repo(self):
        from git import InvalidGitRepositoryError

        with patch("squadx_client.git.manager.Repo", side_effect=InvalidGitRepositoryError("nope")):
            mgr = GitManager(repo_path="/bad")
        assert mgr.get_current_branch() is None


class TestCreateBranch:
    """Test branch creation."""

    def test_create_new_branch(self, git_manager, mock_repo):
        mock_repo.branches = []
        mock_new_branch = MagicMock()
        mock_repo.create_head.return_value = mock_new_branch

        result = git_manager.create_branch("feature/test")

        assert result is True
        mock_repo.create_head.assert_called_once_with("feature/test")
        mock_new_branch.checkout.assert_called_once()

    def test_checkout_existing_branch(self, git_manager, mock_repo):
        existing_branch = MagicMock()
        existing_branch.name = "feature/test"
        mock_repo.branches = [existing_branch]

        result = git_manager.create_branch("feature/test", checkout=True)

        assert result is True
        mock_repo.git.checkout.assert_called_once_with("feature/test")

    def test_create_branch_no_repo(self):
        from git import InvalidGitRepositoryError

        with patch("squadx_client.git.manager.Repo", side_effect=InvalidGitRepositoryError("nope")):
            mgr = GitManager(repo_path="/bad")
        assert mgr.create_branch("feature/x") is False


class TestCommit:
    """Test commit operations."""

    def test_commit_success(self, git_manager, mock_repo):
        mock_commit = MagicMock()
        mock_commit.hexsha = "abcdef1234567890"
        mock_repo.index.commit.return_value = mock_commit
        mock_config = MagicMock()
        mock_config.has_option.return_value = True
        mock_repo.config_writer.return_value.__enter__ = MagicMock(return_value=mock_config)
        mock_repo.config_writer.return_value.__exit__ = MagicMock(return_value=False)

        result = git_manager.commit(["file.py"], "Add file")

        assert result == "abcdef12"
        mock_repo.index.add.assert_called_once_with(["file.py"])

    def test_commit_no_repo(self):
        from git import InvalidGitRepositoryError

        with patch("squadx_client.git.manager.Repo", side_effect=InvalidGitRepositoryError("nope")):
            mgr = GitManager(repo_path="/bad")
        assert mgr.commit(["file.py"], "msg") is None


class TestDiffAndStatus:
    """Test diff and status operations."""

    def test_get_diff_unstaged(self, git_manager, mock_repo):
        mock_repo.git.diff.return_value = "diff --git a/file.py"
        result = git_manager.get_diff(staged=False)
        assert "file.py" in result
        mock_repo.git.diff.assert_called_once_with()

    def test_get_diff_staged(self, git_manager, mock_repo):
        mock_repo.git.diff.return_value = "staged changes"
        result = git_manager.get_diff(staged=True)
        assert result == "staged changes"
        mock_repo.git.diff.assert_called_once_with("--staged")

    def test_get_diff_no_repo(self):
        from git import InvalidGitRepositoryError

        with patch("squadx_client.git.manager.Repo", side_effect=InvalidGitRepositoryError("nope")):
            mgr = GitManager(repo_path="/bad")
        assert mgr.get_diff() == ""

    def test_get_status_no_repo(self):
        from git import InvalidGitRepositoryError

        with patch("squadx_client.git.manager.Repo", side_effect=InvalidGitRepositoryError("nope")):
            mgr = GitManager(repo_path="/bad")
        status = mgr.get_status()
        assert status == {"modified": [], "added": [], "deleted": [], "untracked": []}


class TestPush:
    """Test push operations."""

    def test_push_success(self, git_manager, mock_repo):
        mock_remote = MagicMock()
        mock_repo.remote.return_value = mock_remote

        result = git_manager.push(remote="origin", branch="main")

        assert result is True
        mock_remote.push.assert_called_once_with("main")

    def test_push_no_repo(self):
        from git import InvalidGitRepositoryError

        with patch("squadx_client.git.manager.Repo", side_effect=InvalidGitRepositoryError("nope")):
            mgr = GitManager(repo_path="/bad")
        assert mgr.push() is False
