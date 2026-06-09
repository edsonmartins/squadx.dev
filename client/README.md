# SquadX Client

Python client for SquadX.dev - AI Development Squad Orchestration.

## Overview

The SquadX Client is a LangGraph-based orchestration system that manages AI development agents executing tasks in isolated Docker sandboxes.

## Features

- **Multi-Agent Orchestration**: Coordinates frontend, backend, fullstack, devops, and QA agents
- **Docker Sandbox Execution**: Isolated container environment for code execution
- **Live View Support**: VNC/noVNC streaming for real-time task observation
- **Git Integration**: Automatic branch creation and commit generation
- **Metrics Tracking**: Per-agent token usage, cost, and execution time

## Installation

```bash
pip install -e .
```

For development:
```bash
pip install -e ".[dev]"
```

## Usage

### Start the Daemon

```bash
squadx-client daemon start
```

### Run E2E Test

```bash
python scripts/run_e2e_test.py --with-sandbox --verbose
```

## Architecture

```
squadx_client/
├── agents/           # Agent implementations
├── docker/           # Docker sandbox management
├── orchestrator/     # LangGraph orchestrator
├── streaming/        # VNC streaming
├── llm/              # LLM routing
└── stomp/            # Backend communication
```

## Workspace MCP bridge

`squadx-workspace-mcp` exposes the Control Panel workspace tool contract (get_change, get_tasks,
update_task_status, report_blocker, materialize_change, scaffold_tests) to CLI harnesses over MCP.

1. Open a workspace session (panel UI or `POST /api/v1/workspace/sessions` with `change_id`,
   optional `assignee` and `harness_key` — the key marks the connector as connected) and copy the token.
2. Register the bridge in your harness, e.g. Claude Code:

```bash
claude mcp add squadx-workspace \
  -e SQUADX_API_URL=http://localhost:8080 \
  -e SQUADX_WORKSPACE_TOKEN=<session token> \
  -- squadx-workspace-mcp
```

For remote harnesses use SSE: `squadx-workspace-mcp --transport sse --port 8765`.

## Requirements

- Python 3.11+
- Docker (for sandbox execution)
- LLM API key (OpenAI or Anthropic)

## License

MIT
