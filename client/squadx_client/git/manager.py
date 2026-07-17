"""Git operations manager."""

from pathlib import Path

import structlog
from git import InvalidGitRepositoryError, Repo

from squadx_client.config import settings

logger = structlog.get_logger()


class GitManager:
    """Manages Git operations for task execution."""

    def __init__(self, repo_path: str | Path | None = None):
        """Initialize the Git manager.

        Args:
            repo_path: Path to the git repository. Defaults to current directory.
        """
        self.repo_path = Path(repo_path) if repo_path else Path.cwd()
        self.repo: Repo | None = None
        self._init_repo()

    def _init_repo(self):
        """Initialize or open the git repository."""
        try:
            self.repo = Repo(self.repo_path)
            logger.info("git_repo_opened", path=str(self.repo_path))
        except InvalidGitRepositoryError:
            logger.warning("not_a_git_repo", path=str(self.repo_path))
            self.repo = None

    def is_valid_repo(self) -> bool:
        """Check if the repository is valid."""
        return self.repo is not None

    def get_current_branch(self) -> str | None:
        """Get the current branch name."""
        if not self.repo:
            return None
        return self.repo.active_branch.name

    def create_branch(self, branch_name: str, checkout: bool = True) -> bool:
        """Create a new branch.

        Args:
            branch_name: Name of the new branch
            checkout: Whether to checkout the new branch

        Returns:
            True if successful, False otherwise
        """
        if not self.repo:
            return False

        try:
            # Check if branch already exists
            if branch_name in [b.name for b in self.repo.branches]:
                if checkout:
                    self.repo.git.checkout(branch_name)
                logger.info("branch_exists", branch=branch_name)
                return True

            # Create new branch
            new_branch = self.repo.create_head(branch_name)
            if checkout:
                new_branch.checkout()

            logger.info("branch_created", branch=branch_name)
            return True

        except Exception as e:
            logger.error("branch_creation_failed", branch=branch_name, error=str(e))
            return False

    def stage_files(self, files: list[str]) -> bool:
        """Stage files for commit.

        Args:
            files: List of file paths to stage

        Returns:
            True if successful, False otherwise
        """
        if not self.repo:
            return False

        try:
            self.repo.index.add(files)
            logger.info("files_staged", count=len(files))
            return True
        except Exception as e:
            logger.error("staging_failed", error=str(e))
            return False

    def merge_branch(
        self,
        branch_name: str,
        no_ff: bool = True,
        message: str | None = None,
    ) -> bool:
        """Merge a branch into the currently checked-out branch.

        Used by the orchestrator's commit_changes to fold per-subtask worktree
        branches back into the integration branch. With no_ff=True the merge
        always produces a merge commit, preserving the per-subtask history that
        shows what each specialist did.

        Conflicts are reported as (False, log) via exceptions caught by the caller;
        we don't try to auto-resolve. Non-conflict failures (branch missing, not
        a repo) also return False.
        """
        if not self.repo:
            return False

        try:
            args = [branch_name]
            if no_ff:
                args.insert(0, "--no-ff")
            if message:
                args.extend(["-m", message])
            self.repo.git.merge(*args)
            logger.info(f"branch_merged branch={branch_name}")
            return True
        except Exception as e:
            logger.error(f"merge_failed branch={branch_name} error={e}")
            return False

    def commit(self, files: list[str], message: str) -> str | None:
        """Stage files and create a commit.

        Args:
            files: List of file paths to commit
            message: Commit message

        Returns:
            Commit hash if successful, None otherwise
        """
        if not self.repo:
            return None

        try:
            # Configure git user if needed
            with self.repo.config_writer() as config:
                if not config.has_option("user", "name"):
                    config.set_value("user", "name", settings.git_user_name)
                if not config.has_option("user", "email"):
                    config.set_value("user", "email", settings.git_user_email)

            # Stage files
            self.repo.index.add(files)

            # Create commit
            commit = self.repo.index.commit(message)
            commit_hash = commit.hexsha[:8]

            logger.info("commit_created", hash=commit_hash, files=len(files))
            return commit_hash

        except Exception as e:
            logger.error("commit_failed", error=str(e))
            return None

    def push(self, remote: str = "origin", branch: str | None = None) -> bool:
        """Push changes to remote.

        Args:
            remote: Remote name
            branch: Branch to push. Defaults to current branch.

        Returns:
            True if successful, False otherwise
        """
        if not self.repo:
            return False

        try:
            branch = branch or self.get_current_branch()
            if not branch:
                return False

            self.repo.remote(remote).push(branch)
            logger.info("pushed", remote=remote, branch=branch)
            return True

        except Exception as e:
            logger.error("push_failed", error=str(e))
            return False

    def get_diff(self, staged: bool = False) -> str:
        """Get the current diff.

        Args:
            staged: If True, get staged diff. Otherwise, get unstaged diff.

        Returns:
            Diff string
        """
        if not self.repo:
            return ""

        try:
            if staged:
                return self.repo.git.diff("--staged")
            return self.repo.git.diff()
        except Exception as e:
            logger.error("diff_failed", error=str(e))
            return ""

    def get_status(self) -> dict[str, list[str]]:
        """Get the current repository status.

        Returns:
            Dictionary with modified, added, deleted, and untracked files
        """
        if not self.repo:
            return {"modified": [], "added": [], "deleted": [], "untracked": []}

        try:
            status: dict[str, list[str]] = {
                "modified": [item.a_path for item in self.repo.index.diff(None) if item.a_path],
                "added": [item.a_path for item in self.repo.index.diff("HEAD") if item.change_type == "A" and item.a_path],
                "deleted": [item.a_path for item in self.repo.index.diff("HEAD") if item.change_type == "D" and item.a_path],
                "untracked": list(self.repo.untracked_files),
            }
            return status
        except Exception as e:
            logger.error("status_failed", error=str(e))
            return {"modified": [], "added": [], "deleted": [], "untracked": []}
