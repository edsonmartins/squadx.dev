"""Tests for sandbox file operations module."""

import io
import os
import tarfile
import tempfile
from unittest.mock import MagicMock, patch

import pytest

from squadx_client.docker.file_ops import SandboxFileOps


@pytest.fixture
def mock_container():
    """Create a mock Docker container."""
    container = MagicMock()
    return container


@pytest.fixture
def mock_docker_client(mock_container):
    """Create a mock Docker client that returns the mock container."""
    client = MagicMock()
    client.containers.get.return_value = mock_container
    return client


@pytest.fixture
def file_ops(mock_docker_client):
    """Create SandboxFileOps with mocked client."""
    return SandboxFileOps(mock_docker_client, "container-123")


def _make_tar_bytes(filename: str, content: bytes) -> bytes:
    """Helper: build an in-memory tar archive containing a single file."""
    buf = io.BytesIO()
    with tarfile.open(fileobj=buf, mode="w") as tar:
        info = tarfile.TarInfo(name=filename)
        info.size = len(content)
        tar.addfile(info, io.BytesIO(content))
    buf.seek(0)
    return buf.read()


class TestWriteFile:
    """Test SandboxFileOps.write_file."""

    def test_write_string_content(self, file_ops, mock_container):
        result = file_ops.write_file("/app/hello.py", "print('hi')")
        assert result is True
        mock_container.put_archive.assert_called_once()
        call_args = mock_container.put_archive.call_args
        assert call_args[0][0] == "/app"  # dir_path

    def test_write_bytes_content(self, file_ops, mock_container):
        result = file_ops.write_file("/data/binary.bin", b"\x00\x01\x02")
        assert result is True
        mock_container.put_archive.assert_called_once()

    def test_write_creates_valid_tar(self, file_ops, mock_container):
        content = "hello world"
        file_ops.write_file("/tmp/test.txt", content)
        # Inspect the tar sent to put_archive
        tar_stream = mock_container.put_archive.call_args[0][1]
        tar_stream.seek(0)
        with tarfile.open(fileobj=tar_stream, mode="r") as tar:
            members = tar.getmembers()
            assert len(members) == 1
            assert members[0].name == "test.txt"
            assert members[0].size == len(content.encode())

    def test_write_returns_false_on_exception(self, file_ops, mock_container):
        mock_container.put_archive.side_effect = Exception("API error")
        assert file_ops.write_file("/tmp/f.txt", "x") is False


class TestReadFile:
    """Test SandboxFileOps.read_file and read_file_text."""

    def test_read_file_extracts_from_tar(self, file_ops, mock_container):
        content = b"file content here"
        tar_bytes = _make_tar_bytes("test.txt", content)
        mock_container.get_archive.return_value = ([tar_bytes], {"size": len(content)})

        result = file_ops.read_file("/tmp/test.txt")
        assert result == content

    def test_read_file_returns_none_on_error(self, file_ops, mock_container):
        mock_container.get_archive.side_effect = Exception("not found")
        assert file_ops.read_file("/missing") is None

    def test_read_file_text(self, file_ops, mock_container):
        content = "hello text"
        tar_bytes = _make_tar_bytes("f.txt", content.encode())
        mock_container.get_archive.return_value = ([tar_bytes], {"size": len(content)})
        assert file_ops.read_file_text("/tmp/f.txt") == "hello text"

    def test_read_file_text_returns_none_on_error(self, file_ops, mock_container):
        mock_container.get_archive.side_effect = Exception("err")
        assert file_ops.read_file_text("/missing") is None


class TestFileExists:
    """Test SandboxFileOps.file_exists."""

    def test_file_exists_true(self, file_ops, mock_container):
        mock_container.exec_run.return_value = (0, b"")
        assert file_ops.file_exists("/app/main.py") is True

    def test_file_exists_false(self, file_ops, mock_container):
        mock_container.exec_run.return_value = (1, b"")
        assert file_ops.file_exists("/app/missing.py") is False

    def test_file_exists_false_on_exception(self, file_ops, mock_container):
        mock_container.exec_run.side_effect = Exception("container gone")
        assert file_ops.file_exists("/any") is False


class TestListDirectory:
    """Test SandboxFileOps.list_directory."""

    def test_list_directory(self, file_ops, mock_container):
        mock_container.exec_run.return_value = (0, b"file1.py\nfile2.py\n")
        result = file_ops.list_directory("/app")
        assert result == ["file1.py", "file2.py"]

    def test_list_directory_empty_on_error(self, file_ops, mock_container):
        mock_container.exec_run.return_value = (1, b"")
        assert file_ops.list_directory("/nonexistent") == []


class TestMkdir:
    """Test SandboxFileOps.mkdir."""

    def test_mkdir_with_parents(self, file_ops, mock_container):
        mock_container.exec_run.return_value = (0, b"")
        assert file_ops.mkdir("/app/deep/dir") is True
        cmd = mock_container.exec_run.call_args[0][0]
        assert "-p" in cmd

    def test_mkdir_without_parents(self, file_ops, mock_container):
        mock_container.exec_run.return_value = (0, b"")
        assert file_ops.mkdir("/app/dir", parents=False) is True
        cmd = mock_container.exec_run.call_args[0][0]
        assert "-p" not in cmd


class TestRemove:
    """Test SandboxFileOps.remove."""

    def test_remove_file(self, file_ops, mock_container):
        mock_container.exec_run.return_value = (0, b"")
        assert file_ops.remove("/tmp/old.txt") is True
        cmd = mock_container.exec_run.call_args[0][0]
        assert "-f" in cmd

    def test_remove_recursive(self, file_ops, mock_container):
        mock_container.exec_run.return_value = (0, b"")
        assert file_ops.remove("/tmp/dir", recursive=True) is True
        cmd = mock_container.exec_run.call_args[0][0]
        assert "-rf" in cmd


class TestCopyToContainer:
    """Test SandboxFileOps.copy_to_container."""

    def test_copy_to_container(self, file_ops, mock_container, tmp_path):
        local_file = tmp_path / "local.txt"
        local_file.write_text("data")
        assert file_ops.copy_to_container(str(local_file), "/app/local.txt") is True
        mock_container.put_archive.assert_called_once()

    def test_copy_to_container_returns_false_on_error(self, file_ops, mock_container):
        mock_container.put_archive.side_effect = Exception("fail")
        assert file_ops.copy_to_container("/no/such/file", "/app/x") is False


class TestCopyFromContainer:
    """Test SandboxFileOps.copy_from_container."""

    def test_copy_from_container(self, file_ops, mock_container, tmp_path):
        content = b"remote data"
        tar_bytes = _make_tar_bytes("remote.txt", content)
        mock_container.get_archive.return_value = ([tar_bytes], {"size": len(content)})

        local_dest = str(tmp_path / "subdir" / "out.txt")
        assert file_ops.copy_from_container("/app/remote.txt", local_dest) is True
        with open(local_dest, "rb") as f:
            assert f.read() == content

    def test_copy_from_container_returns_false_on_read_error(self, file_ops, mock_container, tmp_path):
        mock_container.get_archive.side_effect = Exception("gone")
        assert file_ops.copy_from_container("/missing", str(tmp_path / "out")) is False
