# Runbook — Egress firewall on Linux (RFC-0006 / ADR-0008)

**Audience:** operators running the SquadX **client daemon** on a Docker host (Team DOCKER / VPS).  
**Goal:** prove or document that agent egress is **default-deny + allowlist**, not open bridge.

> **Status:** Integration test exists in the client suite (`SQUADX_DOCKER_IT=1`) but may not run in
> every CI environment (needs Linux, Docker, `xt_set` / `ip_set`). This runbook is the
> **signed operational path** when automated IT cannot run (ADR-0009 §1.7).

## Prerequisites

| Requirement | Why |
|-------------|-----|
| Linux host (not macOS host netns) | Sidecar + ipset live in Linux netns |
| Docker Engine | Agent + `egress-proxy` containers |
| Kernel modules `ip_set` / `xt_set` | Allowlist IP sets for DNS answers |
| Images `squadx/agent` (+ optional `:live`) and `squadx/egress-proxy` | Built or pulled |
| `SQUADX_EGRESS_SIDECAR=true` (default) | Feature on |
| `SQUADX_EGRESS_FAIL_OPEN=false` (default) | Fail-closed if policy cannot apply |

## Quick automated path

From a **repo checkout** on the Linux Docker host:

```bash
# 1) Host readiness
./scripts/homolog-client-host.sh check

# 2) Images (build or pull)
./scripts/homolog-client-host.sh build-images
# or: ./scripts/homolog-client-host.sh pull-ghcr

# 3) Integration tests that exercise sidecar + policy
export SQUADX_DOCKER_IT=1
./scripts/homolog-client-host.sh test-egress
```

On a **installed VPS** without full monorepo tests:

```bash
# After install-vps.sh
sudo -u squadx /opt/squadx-client/.venv/bin/squadx-client doctor
./scripts/smoke-vps.sh doctor   # if scripts are on the host
```

Doctor checks:

- `egress.sidecar` — enabled
- `egress.kernel` — `ip_set` / `xt_set` visible via `lsmod` (WARN if not)

## Manual verification (when IT cannot run)

### 1. Modules

```bash
lsmod | grep -E 'ip_set|xt_set' || sudo modprobe ip_set
# Optional: ensure load on boot (distro-specific)
```

### 2. Images present

```bash
docker image inspect squadx/agent:latest
docker image inspect squadx/egress-proxy:latest
```

### 3. Config

```bash
grep -E 'SQUADX_EGRESS|SQUADX_NETWORK_POLICY|SQUADX_BLOCK_CLOUD' /etc/squadx/squadx-client.env
# Expect:
#   SQUADX_EGRESS_SIDECAR=true
#   SQUADX_NETWORK_POLICY=agent-default   # or org policy name
#   SQUADX_EGRESS_FAIL_OPEN must NOT be true in production
```

### 4. Runtime proof (task path)

1. Start the daemon (`systemctl start squadx-client` or `squadx-client start -f`).
2. Dispatch a smoke task from the control plane that needs network (e.g. `curl` to an
   allowlisted host vs a blocked host).
3. Confirm in logs:
   - sidecar container created / netns joined
   - policy applied (or run aborted if fail-closed)
4. Optional: from the agent container, attempt `curl -m 5 http://169.254.169.254/` —
   must fail (metadata block, ADR-0008 Phase 0 + policy).

### 5. Failure modes (expected)

| Symptom | Interpretation |
|---------|----------------|
| Doctor WARN on `egress.kernel` | Load `ip_set`/`xt_set` or accept fail-closed starts |
| Task ERROR at sandbox start, sidecar logs | Policy could not apply → **fail-closed** (good) |
| `SQUADX_EGRESS_SIDECAR=false` | **Open egress** (except host metadata DROP) — not production default |

## macOS / Colima note

Colima runs a Linux VM: **image builds and many ITs** work. Full packet proof of host
`DOCKER-USER` / netns still belongs on a **Linux VPS** for Team DOCKER homolog.

## Sign-off (operator)

| Field | Value |
|-------|--------|
| Host / date | |
| Kernel `uname -r` | |
| `lsmod` ip_set/xt_set | yes / no |
| doctor egress checks | OK / WARN / FAIL |
| `SQUADX_DOCKER_IT` result | pass / skip / n/a |
| Manual curl allow / deny | |
| Operator | |

Retain this table (ticket comment or internal wiki) as evidence for homolog gate #41 / ADR-0009 §1.7.
