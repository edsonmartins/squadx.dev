"""Workspace path containment (ADR-0009 hardening)."""

from __future__ import annotations

from pathlib import Path

import pytest

from squadx_client.sandbox.errors import SandboxExecError
from squadx_client.sandbox.paths import resolve_under_workspace


def test_workspace_root(tmp_path: Path) -> None:
    assert resolve_under_workspace(tmp_path, "/workspace") == tmp_path.resolve()
    assert resolve_under_workspace(tmp_path, ".") == tmp_path.resolve()


def test_relative_and_workspace_prefix(tmp_path: Path) -> None:
    (tmp_path / "a").mkdir()
    (tmp_path / "a" / "b.txt").write_text("x", encoding="utf-8")
    assert resolve_under_workspace(tmp_path, "a/b.txt") == (tmp_path / "a" / "b.txt").resolve()
    assert (
        resolve_under_workspace(tmp_path, "/workspace/a/b.txt")
        == (tmp_path / "a" / "b.txt").resolve()
    )


def test_traversal_relative_refused(tmp_path: Path) -> None:
    with pytest.raises(SandboxExecError, match="outside workspace"):
        resolve_under_workspace(tmp_path, "../etc/passwd")


def test_traversal_workspace_prefix_refused(tmp_path: Path) -> None:
    with pytest.raises(SandboxExecError, match="outside workspace"):
        resolve_under_workspace(tmp_path, "/workspace/../../../etc/passwd")


def test_absolute_outside_refused(tmp_path: Path) -> None:
    with pytest.raises(SandboxExecError, match="outside workspace"):
        resolve_under_workspace(tmp_path, "/etc/passwd")


def test_absolute_inside_ok(tmp_path: Path) -> None:
    f = tmp_path / "in.txt"
    f.write_text("ok", encoding="utf-8")
    assert resolve_under_workspace(tmp_path, str(f.resolve())) == f.resolve()
