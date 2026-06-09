"""
Execution checkpoint/snapshot system.
Captures full state for backup/restore of agent work.
Inspired by ClawTeam's snapshot pattern.
"""
import gzip
import json
import logging
import os
import subprocess
import time
import uuid
from dataclasses import dataclass, field
from pathlib import Path
from typing import Optional

logger = logging.getLogger(__name__)


@dataclass
class CheckpointMetadata:
    checkpoint_id: str
    execution_id: str
    created_at: float = field(default_factory=time.time)
    agent_name: Optional[str] = None
    description: str = ""
    task_count: int = 0
    log_count: int = 0
    file_count: int = 0

    def to_dict(self) -> dict:
        return {
            "checkpointId": self.checkpoint_id,
            "executionId": self.execution_id,
            "createdAt": self.created_at,
            "agentName": self.agent_name,
            "description": self.description,
            "taskCount": self.task_count,
            "logCount": self.log_count,
            "fileCount": self.file_count,
        }


class CheckpointManager:
    """Manages execution checkpoints for backup and restore."""

    def __init__(self, data_dir: str = "~/.squadx/checkpoints"):
        self._data_dir = os.path.expanduser(data_dir)
        os.makedirs(self._data_dir, exist_ok=True)

    def save(self, execution_id: str, state: dict, workspace_path: Optional[str] = None, description: str = "") -> CheckpointMetadata:
        # Include a short random suffix so multiple checkpoints saved within the
        # same second for the same execution don't collide (and overwrite).
        checkpoint_id = f"ckpt-{int(time.time())}-{execution_id[:8]}-{uuid.uuid4().hex[:6]}"

        snapshot = {
            "metadata": {
                "checkpointId": checkpoint_id,
                "executionId": execution_id,
                "createdAt": time.time(),
                "description": description,
            },
            "state": state,
        }

        # Capture git diff if workspace provided
        if workspace_path and os.path.isdir(workspace_path):
            try:
                result = subprocess.run(
                    ["git", "diff", "HEAD"], cwd=workspace_path,
                    capture_output=True, text=True, timeout=30
                )
                snapshot["gitDiff"] = result.stdout

                result = subprocess.run(
                    ["git", "log", "--oneline", "-10"], cwd=workspace_path,
                    capture_output=True, text=True, timeout=10
                )
                snapshot["gitLog"] = result.stdout
            except (subprocess.CalledProcessError, subprocess.TimeoutExpired):
                pass

        # Save as compressed JSON
        filepath = os.path.join(self._data_dir, f"{checkpoint_id}.json.gz")
        with gzip.open(filepath, "wt", encoding="utf-8") as f:
            json.dump(snapshot, f, indent=2, default=str)

        metadata = CheckpointMetadata(
            checkpoint_id=checkpoint_id,
            execution_id=execution_id,
            description=description,
            task_count=len(state.get("tasks", [])),
            log_count=len(state.get("logs", [])),
        )

        logger.info(f"Saved checkpoint {checkpoint_id} for execution {execution_id}")
        return metadata

    def restore(self, checkpoint_id: str) -> Optional[dict]:
        filepath = os.path.join(self._data_dir, f"{checkpoint_id}.json.gz")
        if not os.path.exists(filepath):
            logger.error(f"Checkpoint {checkpoint_id} not found")
            return None

        with gzip.open(filepath, "rt", encoding="utf-8") as f:
            snapshot = json.load(f)

        logger.info(f"Restored checkpoint {checkpoint_id}")
        return snapshot.get("state")

    def list_checkpoints(self, execution_id: Optional[str] = None) -> list[CheckpointMetadata]:
        checkpoints = []
        for filename in sorted(Path(self._data_dir).glob("ckpt-*.json.gz")):
            try:
                with gzip.open(filename, "rt", encoding="utf-8") as f:
                    data = json.load(f)
                meta = data.get("metadata", {})
                if execution_id and meta.get("executionId") != execution_id:
                    continue
                checkpoints.append(CheckpointMetadata(
                    checkpoint_id=meta.get("checkpointId", filename.stem),
                    execution_id=meta.get("executionId", ""),
                    created_at=meta.get("createdAt", 0),
                    description=meta.get("description", ""),
                ))
            except Exception:
                continue
        return checkpoints

    def delete(self, checkpoint_id: str) -> bool:
        filepath = os.path.join(self._data_dir, f"{checkpoint_id}.json.gz")
        if os.path.exists(filepath):
            os.remove(filepath)
            return True
        return False
