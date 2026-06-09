"""SquadX workspace MCP bridge (RFC-0001).

Runs as an MCP server (stdio by default, SSE optional) that CLI harnesses — Claude Code, Codex,
Gemini CLI, Cursor — connect to. Each tool proxies to the SquadX backend's HTTP workspace API
using the scoped session token from SQUADX_WORKSPACE_TOKEN (issued via POST /api/v1/workspace/sessions).

Usage:
    SQUADX_WORKSPACE_TOKEN=<token> squadx-workspace-mcp                 # stdio
    SQUADX_WORKSPACE_TOKEN=<token> squadx-workspace-mcp --transport sse --port 8765
"""

import argparse
import sys
from typing import Any, Optional

from mcp.server.fastmcp import FastMCP

from ..config import settings
from .workspace_client import WorkspaceApiClient

mcp = FastMCP("squadx-workspace")

_client: Optional[WorkspaceApiClient] = None


def configure(client: WorkspaceApiClient) -> None:
    """Inject the workspace API client (also used by tests)."""
    global _client
    _client = client


def _require_client() -> WorkspaceApiClient:
    if _client is None:
        raise RuntimeError("workspace client not configured (missing SQUADX_WORKSPACE_TOKEN?)")
    return _client


# -- Tool implementations (kept separate from the decorators for testability) --

async def get_change_impl() -> Any:
    return await _require_client().get_change()


async def get_tasks_impl(assignee: Optional[str] = None) -> Any:
    return await _require_client().get_tasks(assignee)


async def update_task_status_impl(task_id: int, status: str, note: Optional[str] = None) -> Any:
    return await _require_client().update_task_status(task_id, status, note)


async def report_blocker_impl(task_id: int, reason: str) -> Any:
    return await _require_client().report_blocker(task_id, reason)


async def materialize_change_impl() -> Any:
    return await _require_client().materialize_change()


async def scaffold_tests_impl(requirement_id: Optional[int] = None) -> Any:
    return await _require_client().scaffold_tests(requirement_id)


# -- MCP tools (contract per RFC-0001 §4) --

@mcp.tool()
async def get_change() -> Any:
    """Briefing da mudança da sessão: proposta, fase, requisitos (com cenários WHEN/THEN) e tarefas."""
    return await get_change_impl()


@mcp.tool()
async def get_tasks(assignee: Optional[str] = None) -> Any:
    """Tarefas da mudança da sessão, com requirement_ref e status; filtro opcional por responsável."""
    return await get_tasks_impl(assignee)


@mcp.tool()
async def update_task_status(task_id: int, status: str, note: Optional[str] = None) -> Any:
    """Reporta progresso de uma tarefa. status deve ser 'em_curso' ou 'implementado' —
    'concluida'/'ajustes' são exclusivos do Pass 5 e serão rejeitados. Uma chamada por tarefa,
    na ordem em que concluir."""
    return await update_task_status_impl(task_id, status, note)


@mcp.tool()
async def report_blocker(task_id: int, reason: str) -> Any:
    """Marca uma tarefa como bloqueada. O motivo é obrigatório e fica registrado no board."""
    return await report_blocker_impl(task_id, reason)


@mcp.tool()
async def materialize_change() -> Any:
    """Materializa a versão corrente da spec no repositório Git e devolve versão + commit."""
    return await materialize_change_impl()


@mcp.tool()
async def scaffold_tests(requirement_id: Optional[int] = None) -> Any:
    """Gera o esqueleto de testes (um método por cenário, nome rastreável) + mapa de cobertura."""
    return await scaffold_tests_impl(requirement_id)


def main() -> None:
    parser = argparse.ArgumentParser(description="SquadX workspace MCP bridge")
    parser.add_argument("--transport", choices=["stdio", "sse"], default="stdio")
    parser.add_argument("--port", type=int, default=8765, help="SSE port (when --transport sse)")
    args = parser.parse_args()

    token = settings.workspace_token
    if not token:
        print(
            "SQUADX_WORKSPACE_TOKEN is not set. Open a workspace session "
            "(POST /api/v1/workspace/sessions) and export the token.",
            file=sys.stderr,
        )
        sys.exit(1)

    configure(WorkspaceApiClient(settings.api_url, token))

    if args.transport == "sse":
        mcp.settings.port = args.port
    mcp.run(transport=args.transport)


if __name__ == "__main__":
    main()
