# PROCESS sandbox backend (ADR-0009 Phase 4)

Run native LangGraph agents **without Docker**, using OS isolation:

| Host | Isolator | Binary |
|------|----------|--------|
| Linux | bubblewrap | `bwrap` |
| macOS | Seatbelt | `sandbox-exec` |

## Enable

```bash
export SQUADX_SANDBOX_BACKEND=process
# optional: no network inside bwrap
export SQUADX_PROCESS_NETWORK=deny
# CI / emergency only (no isolation):
# export SQUADX_PROCESS_UNSAFE=1

squadx-client doctor
squadx-client start -f
```

## What works

- Native agent loop (tools: bash, read/write files under workspace)
- Workspace bind + deny writes outside (Seatbelt / bwrap bind)

## What does **not** work

| Feature | Status |
|---------|--------|
| Live View (VNC/WebRTC) | Unsupported — use Docker |
| External CLI (Claude Code / Codex / Gemini) | **Docker-only** |
| RFC-0006 egress sidecar | N/A — use `SQUADX_PROCESS_NETWORK=deny` or Docker |
| Warm pool | Docker-only |

## Threat model

**Laptop / Dev LIGHT**, not multi-tenant VPS. Prefer Team DOCKER (`install-vps.sh`) for production agent hosts.

## Install isolators

```bash
# Debian/Ubuntu
sudo apt install bubblewrap

# Fedora
sudo dnf install bubblewrap

# macOS — sandbox-exec ships with the OS
```

## Tests

```bash
cd client
pytest tests/test_process_sandbox_backend.py -v
# real isolator (if present):
pytest -m sandbox_process -v
```
