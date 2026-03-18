"""
Enhanced file operations for sandboxed containers.
Replaces echo/cat-based file I/O with robust tar-based operations.
Inspired by OpenSandbox's execd file API.
"""
import io
import os
import tarfile
import logging
from dataclasses import dataclass
from typing import Optional

logger = logging.getLogger(__name__)


@dataclass
class FileInfo:
    """Metadata about a file in the sandbox."""
    path: str
    size: int
    mode: int
    is_directory: bool
    modified_at: Optional[float] = None


class SandboxFileOps:
    """Robust file operations using Docker's tar-based put_archive/get_archive APIs.

    This replaces the echo/cat approach with proper binary-safe operations
    that handle special characters, large files, and binary content correctly.
    """

    def __init__(self, docker_client, container_id: str):
        self._client = docker_client
        self._container_id = container_id

    def write_file(self, path: str, content: str | bytes, mode: int = 0o644) -> bool:
        """Write file to container using put_archive (binary-safe)."""
        try:
            container = self._client.containers.get(self._container_id)

            # Determine parent directory and filename
            dir_path = os.path.dirname(path) or "/"
            filename = os.path.basename(path)

            if isinstance(content, str):
                content = content.encode("utf-8")

            # Create tar archive in memory
            tar_stream = io.BytesIO()
            with tarfile.open(fileobj=tar_stream, mode="w") as tar:
                info = tarfile.TarInfo(name=filename)
                info.size = len(content)
                info.mode = mode
                tar.addfile(info, io.BytesIO(content))

            tar_stream.seek(0)
            container.put_archive(dir_path, tar_stream)
            logger.debug(f"Wrote {len(content)} bytes to {path}")
            return True
        except Exception as e:
            logger.error(f"Failed to write {path}: {e}")
            return False

    def read_file(self, path: str) -> Optional[bytes]:
        """Read file from container using get_archive (binary-safe)."""
        try:
            container = self._client.containers.get(self._container_id)
            bits, stat = container.get_archive(path)

            # Extract file from tar stream
            tar_stream = io.BytesIO()
            for chunk in bits:
                tar_stream.write(chunk)
            tar_stream.seek(0)

            with tarfile.open(fileobj=tar_stream, mode="r") as tar:
                member = tar.getmembers()[0]
                f = tar.extractfile(member)
                if f is None:
                    return None
                return f.read()
        except Exception as e:
            logger.error(f"Failed to read {path}: {e}")
            return None

    def read_file_text(self, path: str, encoding: str = "utf-8") -> Optional[str]:
        """Read file as text."""
        data = self.read_file(path)
        return data.decode(encoding) if data is not None else None

    def file_exists(self, path: str) -> bool:
        """Check if file exists in container."""
        try:
            container = self._client.containers.get(self._container_id)
            exit_code, _ = container.exec_run(f"test -e {path}")
            return exit_code == 0
        except Exception:
            return False

    def get_file_info(self, path: str) -> Optional[FileInfo]:
        """Get file metadata."""
        try:
            container = self._client.containers.get(self._container_id)
            _, stat = container.get_archive(path)
            return FileInfo(
                path=path,
                size=stat.get("size", 0),
                mode=int(stat.get("mode", "0644"), 8) if isinstance(stat.get("mode"), str) else stat.get("mode", 0o644),
                is_directory=stat.get("linkTarget", "") == "" and stat.get("name", "").endswith("/"),
            )
        except Exception:
            return None

    def list_directory(self, path: str, pattern: str = "*") -> list[str]:
        """List directory contents, optionally with glob pattern."""
        try:
            container = self._client.containers.get(self._container_id)
            if pattern == "*":
                exit_code, output = container.exec_run(f"ls -1 {path}")
            else:
                exit_code, output = container.exec_run(
                    ["find", path, "-maxdepth", "1", "-name", pattern, "-printf", "%f\n"]
                )
            if exit_code != 0:
                return []
            return [f for f in output.decode("utf-8").strip().split("\n") if f]
        except Exception:
            return []

    def mkdir(self, path: str, parents: bool = True) -> bool:
        """Create directory in container."""
        try:
            container = self._client.containers.get(self._container_id)
            flag = "-p" if parents else ""
            exit_code, _ = container.exec_run(f"mkdir {flag} {path}")
            return exit_code == 0
        except Exception:
            return False

    def remove(self, path: str, recursive: bool = False) -> bool:
        """Remove file or directory."""
        try:
            container = self._client.containers.get(self._container_id)
            flag = "-rf" if recursive else "-f"
            exit_code, _ = container.exec_run(f"rm {flag} {path}")
            return exit_code == 0
        except Exception:
            return False

    def copy_to_container(self, local_path: str, container_path: str) -> bool:
        """Copy a local file or directory to the container."""
        try:
            container = self._client.containers.get(self._container_id)
            dir_path = os.path.dirname(container_path) or "/"

            tar_stream = io.BytesIO()
            with tarfile.open(fileobj=tar_stream, mode="w") as tar:
                tar.add(local_path, arcname=os.path.basename(container_path))

            tar_stream.seek(0)
            container.put_archive(dir_path, tar_stream)
            return True
        except Exception as e:
            logger.error(f"Failed to copy {local_path} to {container_path}: {e}")
            return False

    def copy_from_container(self, container_path: str, local_path: str) -> bool:
        """Copy a file from the container to local filesystem."""
        try:
            data = self.read_file(container_path)
            if data is None:
                return False

            os.makedirs(os.path.dirname(local_path), exist_ok=True)
            with open(local_path, "wb") as f:
                f.write(data)
            return True
        except Exception as e:
            logger.error(f"Failed to copy {container_path} to {local_path}: {e}")
            return False
