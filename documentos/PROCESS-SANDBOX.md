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

- Native agent loop (tools: bash under isolator; read/write under workspace path containment)
- Linux: bwrap bind workspace + ro host toolchain
- macOS: Seatbelt write-limit under workspace (+ host reads for toolchain)

## What does **not** work

| Feature | Status |
|---------|--------|
| Live View (VNC/WebRTC) | Unsupported — use Docker |
| External CLI (Claude Code / Codex / Gemini) | **Docker-only** |
| RFC-0006 egress sidecar | N/A — use `SQUADX_PROCESS_NETWORK=deny` or Docker |
| Warm pool | Docker-only |
| Multi-tenant hard isolation | **No** — laptop threat model only |

## Honest isolation model

| Operation | Isolation |
|-----------|-----------|
| `execute` / `execute_streaming` | bwrap or Seatbelt |
| `write_file` / `read_file` | **Host FS** under workspace only (`resolve_under_workspace`; traversal denied) |
| Seatbelt `file-read*` | Broad (host Python/toolchain) — not a read jail |
| Network | Shared by default; `SQUADX_PROCESS_NETWORK=deny` → bwrap `--unshare-net` or Seatbelt deny |

## Threat model

**Laptop / Dev LIGHT**, not multi-tenant VPS. Prefer Team DOCKER (`install-vps.sh`) for production agent hosts.
Never set `SQUADX_PROCESS_UNSAFE=1` outside tests (runs with **no** OS sandbox).

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
