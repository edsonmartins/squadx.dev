# Deploying the SquadX client daemon (dedicated Docker host)

The client daemon creates every agent sandbox — and the egress firewall sidecar —
through the host's Docker daemon (`docker.from_env()`). It therefore runs as a
**native process on a host with Docker**, not as a Kubernetes pod. The cluster runs
the backend, frontend, Postgres and Redis; this host connects to the backend over
STOMP and does the sandbox work.

```
  k8s cluster:  backend + frontend + postgres + redis
        │  STOMP (wss)
        ▼
  Docker host (this):  squadx-client daemon
        └─ creates hardened sandboxes (siblings) + egress sidecar per task
```

> **Homolog without k8s.** You can point the daemon at a backend started with
> `docker compose` + `./mvnw spring-boot:run` on the same machine. See
> `documentos/HOMOLOGACAO-LOCAL-DOCKER.md` and `scripts/homolog-client-host.sh`.

> Why not a pod? Running the daemon in-cluster would require either mounting the
> node's Docker socket into the pod (root-equivalent control of the node) or a
> privileged Docker-in-Docker sidecar. A dedicated host keeps that privilege off
> the cluster and matches what the code already assumes. See the audit note in the
> repo for the rejected alternatives.

## Quick smoke (from repo root)

```bash
./scripts/homolog-client-host.sh check
./scripts/homolog-client-host.sh build-images   # or: pull-ghcr
./scripts/homolog-client-host.sh test-egress    # SQUADX_DOCKER_IT=1 (Linux/Colima)
```

## Dev LIGHT on macOS (Colima — ADR-0009)

For a Mac mini / laptop pointing at SaaS (or a local API) — **not** a production agent host:

```bash
./scripts/install-mac-client.sh
source ~/.squadx/env.sh
squadx-client doctor
./scripts/smoke-mac.sh
```

Full page: `documentos/DEV-LIGHT-MAC.md`. Egress packet-proof remains a **Linux** concern (`EGRESS-RUNBOOK.md`).

## One-shot VPS install (Team DOCKER — ADR-0009)

On a **Linux** host with Docker Engine and a monorepo checkout:

```bash
# optional: login to GHCR if images are private
# echo $GHCR_TOKEN | docker login ghcr.io -u USER --password-stdin

./scripts/install-vps.sh --pull-images
# or build images locally instead of pull:
# ./scripts/install-vps.sh

sudoedit /etc/squadx/squadx-client.env   # API URL, token, LLM keys
sudo -u squadx /opt/squadx-client/.venv/bin/squadx-client doctor
sudo systemctl enable --now squadx-client
sudo journalctl -u squadx-client -f
```

`squadx-client doctor` validates Docker, sandbox images, API health, token, LLM keys,
and the configured `SQUADX_SANDBOX_BACKEND` (default `docker`) before you start the unit.

### Post-install smoke

```bash
./scripts/smoke-vps.sh              # doctor + env + systemd (if present)
./scripts/smoke-vps.sh doctor       # doctor only
```

### Egress proof (Linux)

See **[EGRESS-RUNBOOK.md](./EGRESS-RUNBOOK.md)** (RFC-0006 / ADR-0008): automated
`SQUADX_DOCKER_IT=1` path via `homolog-client-host.sh test-egress`, or the manual
sign-off table when IT cannot run on the host.

## Provision the host (manual)

1. **Install Docker** (Engine, **Linux** preferred for egress). The egress firewall
   needs an `xt_set`-capable kernel (standard on most distros); without it the
   daemon fails closed rather than running unfiltered.

   - **Linux:** Docker Engine from the distro/docs.
   - **macOS (dev only):** `brew install colima docker && colima start` — good for
     image builds and integration tests; production homolog should still use Linux.

2. **Create the service user** and give it Docker access:
   ```bash
   sudo useradd --system --home /opt/squadx-client --shell /usr/sbin/nologin squadx
   sudo usermod -aG docker squadx    # root-equivalent on this host — dedicate the host
   ```

3. **Install the client package** into a virtualenv at `/opt/squadx-client/.venv`:
   ```bash
   sudo git clone <repo> /opt/squadx-client/src
   sudo python3 -m venv /opt/squadx-client/.venv
   sudo /opt/squadx-client/.venv/bin/pip install -e "/opt/squadx-client/src/client"
   sudo chown -R squadx:squadx /opt/squadx-client
   ```

4. **Build (or pull) the sandbox images.** Locally, from the repo root:
   ```bash
   make build-sandbox-images   # -> squadx/agent:latest, squadx/agent:live, squadx/egress-proxy:latest
   ```
   or pull the published images and set `SQUADX_AGENT_IMAGE` / `SQUADX_EGRESS_PROXY_IMAGE`
   to the `ghcr.io/edsonmartins/squadx.dev/...` paths in the env file.
   For the WebRTC live view, use the `:live` agent image.

5. **Configure and start the service:**
   ```bash
   sudo mkdir -p /etc/squadx
   sudo cp client/deploy/squadx-client.env.example /etc/squadx/squadx-client.env
   sudoedit /etc/squadx/squadx-client.env          # fill in API URL, token, keys
   sudo cp client/deploy/squadx-client.service /etc/systemd/system/
   sudo systemctl daemon-reload
   sudo systemctl enable --now squadx-client
   sudo systemctl status squadx-client
   sudo journalctl -u squadx-client -f             # logs
   ```

## Verify

- `docker ps` on the host shows `squadx-agent-*` (and `squadx-egress-*`) containers
  appear while a task runs and are torn down after.
- The backend shows the task moving to running and receiving logs.
- Egress: from inside a running agent, an allowlisted host resolves and a
  non-allowlisted one does not (see `client/tests/test_egress_sidecar.py` integration
  test for the executable spec).

## Scaling

Run more hosts, each with its own token; the backend's poll/claim path
(`/pending` + `/{id}/claim`, compare-and-set) ensures only one host runs any given
task. Tune `SQUADX_MAX_CONCURRENT_AGENTS` per host.
