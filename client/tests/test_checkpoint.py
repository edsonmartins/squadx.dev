"""Tests for checkpoint/snapshot management module."""

import gzip
import json
import os
import tempfile
from unittest.mock import MagicMock, patch

import pytest

from squadx_client.checkpoint.manager import CheckpointManager, CheckpointMetadata


class TestCheckpointMetadata:
    """Test CheckpointMetadata dataclass."""

    def test_to_dict_contains_expected_keys(self):
        meta = CheckpointMetadata(
            checkpoint_id="ckpt-123",
            execution_id="exec-456",
            description="test save",
            task_count=3,
            log_count=10,
        )
        d = meta.to_dict()
        assert d["checkpointId"] == "ckpt-123"
        assert d["executionId"] == "exec-456"
        assert d["description"] == "test save"
        assert d["taskCount"] == 3
        assert d["logCount"] == 10
        assert "createdAt" in d

    def test_default_values(self):
        meta = CheckpointMetadata(checkpoint_id="c1", execution_id="e1")
        assert meta.agent_name is None
        assert meta.description == ""
        assert meta.task_count == 0
        assert meta.file_count == 0


class TestCheckpointManager:
    """Test CheckpointManager operations."""

    @pytest.fixture
    def ckpt_dir(self, tmp_path):
        return str(tmp_path / "checkpoints")

    @pytest.fixture
    def manager(self, ckpt_dir):
        return CheckpointManager(data_dir=ckpt_dir)

    def test_save_creates_compressed_file(self, manager, ckpt_dir):
        state = {"tasks": [{"id": "t1"}], "logs": ["log1", "log2"]}
        meta = manager.save("exec-001", state, description="first save")

        assert meta.checkpoint_id.startswith("ckpt-")
        assert meta.execution_id == "exec-001"
        assert meta.description == "first save"
        assert meta.task_count == 1
        assert meta.log_count == 2

        # Verify file exists and is valid gzip
        filepath = os.path.join(ckpt_dir, f"{meta.checkpoint_id}.json.gz")
        assert os.path.exists(filepath)

        with gzip.open(filepath, "rt", encoding="utf-8") as f:
            data = json.load(f)
        assert "state" in data
        assert "metadata" in data
        assert data["state"]["tasks"][0]["id"] == "t1"

    @patch("squadx_client.checkpoint.manager.subprocess.run")
    def test_save_with_workspace_captures_git(self, mock_run, manager, tmp_path):
        workspace = str(tmp_path / "workspace")
        os.makedirs(workspace)

        mock_run.side_effect = [
            MagicMock(stdout="diff output", returncode=0),  # git diff
            MagicMock(stdout="abc1234 commit msg", returncode=0),  # git log
        ]

        state = {"tasks": []}
        meta = manager.save("exec-002", state, workspace_path=workspace)

        assert meta.checkpoint_id.startswith("ckpt-")
        assert mock_run.call_count == 2

    def test_save_without_workspace(self, manager):
        state = {"tasks": []}
        meta = manager.save("exec-003", state)
        assert meta.checkpoint_id.startswith("ckpt-")

    def test_restore_existing_checkpoint(self, manager, ckpt_dir):
        state = {"tasks": [{"id": "t1"}], "config": {"key": "val"}}
        meta = manager.save("exec-004", state)

        restored = manager.restore(meta.checkpoint_id)
        assert restored is not None
        assert restored["tasks"][0]["id"] == "t1"
        assert restored["config"]["key"] == "val"

    def test_restore_nonexistent_returns_none(self, manager):
        assert manager.restore("ckpt-nonexistent") is None

    def test_list_checkpoints_all(self, manager):
        manager.save("exec-a", {"tasks": []}, description="save 1")
        manager.save("exec-b", {"tasks": []}, description="save 2")

        checkpoints = manager.list_checkpoints()
        assert len(checkpoints) == 2

    def test_list_checkpoints_filtered_by_execution_id(self, manager):
        manager.save("exec-a", {"tasks": []})
        manager.save("exec-b", {"tasks": []})
        manager.save("exec-a", {"tasks": [{"id": "t2"}]})

        filtered = manager.list_checkpoints(execution_id="exec-a")
        assert len(filtered) == 2
        for meta in filtered:
            assert meta.execution_id == "exec-a"

    def test_delete_existing(self, manager, ckpt_dir):
        meta = manager.save("exec-del", {"tasks": []})
        filepath = os.path.join(ckpt_dir, f"{meta.checkpoint_id}.json.gz")
        assert os.path.exists(filepath)

        result = manager.delete(meta.checkpoint_id)
        assert result is True
        assert not os.path.exists(filepath)

    def test_delete_nonexistent_returns_false(self, manager):
        assert manager.delete("ckpt-nonexistent") is False

    def test_list_checkpoints_empty(self, manager):
        assert manager.list_checkpoints() == []
