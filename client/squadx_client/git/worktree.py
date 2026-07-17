"""
Git worktree management for agent isolation.
Each agent gets its own git worktree/branch, preventing conflicts.
Inspired by ClawTeam's workspace isolation pattern.
"""
import logging
import os
import subprocess
from dataclasses import dataclass

logger = logging.getLogger(__name__)


@dataclass
class WorktreeInfo:
    path: str
    branch: str
    agent_name: str
    task_id: str | None = None

    @property
    def exists(self) -> bool:
        return os.path.isdir(self.path)


class WorktreeManager:
    """Manages git worktrees for agent isolation."""

    def __init__(self, repo_path: str):
        self._repo = repo_path
        self._worktrees_dir = os.path.join(repo_path, ".worktrees")
        self._registry: dict[str, WorktreeInfo] = {}

    def create(self, agent_name: str, task_id: str | None = None, base_branch: str = "main") -> WorktreeInfo:
        branch = f"squadx/{task_id or 'default'}/{agent_name}"
        worktree_path = os.path.join(self._worktrees_dir, agent_name)

        os.makedirs(self._worktrees_dir, exist_ok=True)

        # Clean up if exists
        if os.path.isdir(worktree_path):
            self.cleanup(agent_name)

        try:
            subprocess.run(
                ["git", "worktree", "add", worktree_path, "-b", branch, base_branch],
                cwd=self._repo, check=True, capture_output=True, text=True
            )
        except subprocess.CalledProcessError:
            # Branch may already exist
            subprocess.run(
                ["git", "worktree", "add", worktree_path, branch],
                cwd=self._repo, check=True, capture_output=True, text=True
            )

        info = WorktreeInfo(path=worktree_path, branch=branch, agent_name=agent_name, task_id=task_id)
        self._registry[agent_name] = info
        logger.info(f"Created worktree for {agent_name} at {worktree_path} (branch: {branch})")
        return info

    def checkpoint(self, agent_name: str, message: str | None = None) -> bool:
        info = self._registry.get(agent_name)
        if info is None or not info.exists:
            return False

        msg = message or f"checkpoint: {agent_name} auto-save"
        try:
            subprocess.run(["git", "add", "-A"], cwd=info.path, check=True, capture_output=True)
            result = subprocess.run(
                ["git", "diff", "--cached", "--quiet"], cwd=info.path, capture_output=True
            )
            if result.returncode != 0:  # There are staged changes
                subprocess.run(
                    ["git", "commit", "-m", msg], cwd=info.path, check=True, capture_output=True, text=True
                )
                logger.info(f"Checkpointed {agent_name}: {msg}")
                return True
        except subprocess.CalledProcessError as e:
            logger.error(f"Checkpoint failed for {agent_name}: {e}")
        return False

    def merge(self, agent_name: str, target_branch: str = "main") -> bool:
        info = self._registry.get(agent_name)
        if info is None:
            return False

        self.checkpoint(agent_name, f"pre-merge checkpoint: {agent_name}")

        try:
            subprocess.run(
                ["git", "merge", info.branch, "--no-ff", "-m", f"Merge {agent_name} work from {info.branch}"],
                cwd=self._repo, check=True, capture_output=True, text=True
            )
            logger.info(f"Merged {agent_name} ({info.branch}) into {target_branch}")
            return True
        except subprocess.CalledProcessError as e:
            logger.error(f"Merge failed for {agent_name}: {e.stderr}")
            return False

    def cleanup(self, agent_name: str) -> bool:
        info = self._registry.get(agent_name)
        worktree_path = info.path if info else os.path.join(self._worktrees_dir, agent_name)

        try:
            subprocess.run(
                ["git", "worktree", "remove", worktree_path, "--force"],
                cwd=self._repo, check=True, capture_output=True, text=True
            )
            if info:
                # Try to delete the branch
                subprocess.run(
                    ["git", "branch", "-D", info.branch],
                    cwd=self._repo, capture_output=True, text=True
                )
            self._registry.pop(agent_name, None)
            logger.info(f"Cleaned up worktree for {agent_name}")
            return True
        except subprocess.CalledProcessError as e:
            logger.error(f"Cleanup failed for {agent_name}: {e}")
            return False

    def list_worktrees(self) -> list[WorktreeInfo]:
        return list(self._registry.values())

    def get(self, agent_name: str) -> WorktreeInfo | None:
        return self._registry.get(agent_name)
