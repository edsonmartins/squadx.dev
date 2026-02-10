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

## Requirements

- Python 3.11+
- Docker (for sandbox execution)
- LLM API key (OpenAI or Anthropic)

## License

MIT
