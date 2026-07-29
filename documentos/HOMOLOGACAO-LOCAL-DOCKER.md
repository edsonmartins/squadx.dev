# Homologação sem Kubernetes (path local / host Docker)

Para avançar **#40** (client host) e **#41** (egress) **sem** cluster de staging
(`#39`). O Control Panel e o deploy k8s ficam de fora.

Escopo global do piloto: `documentos/PILOTO-ESCOPO.md`.

---

## Arquitetura deste path

```
┌─────────────────────────────────────────────┐
│  Máquina dev / VM Linux                     │
│                                             │
│  docker compose:  postgres + redis          │
│                   (+ backend + frontend opcional) │
│         │                                   │
│         │ STOMP / HTTP                      │
│         ▼                                   │
│  squadx-client daemon (processo nativo)     │
│         │ docker.sock                       │
│         ▼                                   │
│  sandboxes: agent (+ egress-proxy sidecar)  │
└─────────────────────────────────────────────┘
```

Em produção/homolog k8s o backend fica no cluster e o client em host dedicado
(`client/deploy/README.md`). Aqui tudo pode ser na mesma máquina.

---

## 0. Docker no macOS (Colima)

Este repo não exige Docker Desktop. Em macOS:

```bash
brew install colima docker docker-buildx
colima start --cpu 4 --memory 8 --disk 60
docker info
```

> **Egress (#41):** o enforcement real (netns + ipset) é **Linux**. Colima roda uma
> VM Linux — os testes `SQUADX_DOCKER_IT=1` usam o daemon Docker *dessa* VM e são
> o melhor proxy local. Prova final em host Linux bare-metal continua recomendada
> (`documentos/HOMOLOGACAO-VERIFICACAO.md` §1).

---

## 1. Script único (#40 / #41)

Na raiz do repo:

```bash
chmod +x scripts/homolog-client-host.sh

./scripts/homolog-client-host.sh check          # pré-requisitos
./scripts/homolog-client-host.sh build-images   # squadx/agent{:latest,:live} + egress-proxy
# ou, se GHCR público/login ok:
# ./scripts/homolog-client-host.sh pull-ghcr

./scripts/homolog-client-host.sh test-egress    # pytest -m integration (sidecar)
# atalho:
# ./scripts/homolog-client-host.sh all
```

**Critérios de aceite (#40 imagens):**

- [ ] `docker images` mostra `squadx/agent:latest`, `squadx/agent:live`, `squadx/egress-proxy:latest`
- [ ] (opcional) pull de `ghcr.io/edsonmartins/squadx.dev/*` funciona

**Critérios de aceite (#41 egress IT):**

- [ ] `SQUADX_DOCKER_IT=1 pytest -m integration tests/test_egress_sidecar.py` passa
- [ ] Se falhar por `xt_set` / DNS sidecar no macOS+Colima: documentar e rodar em **Linux real**

**Estado observado (macOS + Colima, 2026-07-29):**

| Passo | Resultado |
|-------|-----------|
| Imagens `agent` / `agent:live` / `egress-proxy` | ✅ build local |
| Sandbox start com sidecar | ✅ (após fix `put_archive` bool) |
| Host `iptables` (metadata DOCKER-USER) | ❌ no Mac host — precisa Linux |
| Allowlist resolve + deny | ⚠️ IT ainda falha DNS no agent sob Colima; provar em Linux |

O script exporta `DOCKER_HOST=unix://$HOME/.colima/docker.sock` automaticamente.

---

## 2. Backend local (sem k8s)

### Atalho automatizado

```bash
./scripts/homolog-local-smoke.sh up      # postgres:55432 + redis:56379 (evita conflito com Postgres local)
# em outro terminal: seguir o bloco que o script imprime (JDK 21 + JWT base64 + exclude OAuth2)
./scripts/homolog-local-smoke.sh seed    # login admin, task, start execution
./scripts/homolog-local-smoke.sh client  # daemon claima a task
./scripts/homolog-local-smoke.sh status
```

### Gotchas (reproduzidos em 2026-07-29)

| Problema | Sintoma | Fix |
|----------|---------|-----|
| Postgres do host na `:5432` | `role "squadx" does not exist` | usar portas **55432/56379** (script `up`) |
| `JWT_SECRET` plain text | `Illegal base64 character: '-'` no login | `openssl rand -base64 48` |
| OAuth2 vazio no `application.yml` | boot falha em `Client id must not be empty` / issuer resolve | `SPRING_AUTOCONFIGURE_EXCLUDE=...OAuth2ClientAutoConfiguration` |
| `AWS_ACCESS_KEY_ID` vazio | `Access key ID cannot be blank` no S3 bean | dummy keys ou não ativar S3 |
| Sem `OPENAI_API_KEY` / `ANTHROPIC_API_KEY` | daemon claima task e falha em `OPENAI_API_KEY is required` | exportar chave real para smoke “verde” de agente |

### Manual

```bash
# deps (portas homolog se 5432/6379 ocupadas)
./scripts/homolog-local-smoke.sh up

# Backend (JDK 21) — JWT **base64**, OAuth2 excluído
cd backend
export SPRING_DATASOURCE_URL=jdbc:postgresql://127.0.0.1:55432/squadx
export SPRING_DATASOURCE_USERNAME=squadx
export SPRING_DATASOURCE_PASSWORD=squadx_dev_password
export SPRING_DATA_REDIS_HOST=127.0.0.1
export SPRING_DATA_REDIS_PORT=56379
export JWT_SECRET=$(openssl rand -base64 48 | tr -d '\n')
export AWS_ACCESS_KEY_ID=local-dev-not-real
export AWS_SECRET_ACCESS_KEY=local-dev-not-real
export SPRING_AUTOCONFIGURE_EXCLUDE=\
org.springframework.boot.autoconfigure.security.oauth2.client.servlet.OAuth2ClientAutoConfiguration
./mvnw spring-boot:run -DskipTests
```

Frontend (opcional):

```bash
cd frontend
pnpm install
NEXT_PUBLIC_API_URL=http://localhost:8080 \
NEXT_PUBLIC_WS_URL=http://localhost:8080/ws \
pnpm dev
```

---

## 3. Daemon do client

```bash
cd client
python3 -m venv .venv && source .venv/bin/activate
pip install -e ".[dev]"

# Env mínimo (dev) — ver client/deploy/squadx-client.env.example
export SQUADX_API_URL=http://localhost:8080
export SQUADX_WS_URL=ws://localhost:8080/ws
export SQUADX_API_TOKEN=...          # token de client emitido pelo backend
export SQUADX_AGENT_IMAGE=squadx/agent:live
export SQUADX_EGRESS_PROXY_IMAGE=squadx/egress-proxy:latest
export SQUADX_EGRESS_SIDECAR=true
export ANTHROPIC_API_KEY=...         # ou OPENAI_/GOOGLE_

python -m squadx_client.main start --foreground
```

Em host Linux de homolog (systemd): seguir `client/deploy/README.md` à risca.

---

## 4. Smoke — resultados de referência (2026-07-29)

### 4.1 Pipeline sem LLM key

| Passo | Resultado |
|-------|-----------|
| postgres+redis (55432/56379) + backend health | ✅ |
| Login + task + execution START | ✅ |
| Client STOMP + poll claim | ✅ |
| Falha em analyze sem key | esperado |

### 4.2 Pipeline com OpenRouter (aceite local A — GO)

| Passo | Resultado |
|-------|-----------|
| `OPENROUTER_API_KEY` + `SQUADX_DEFAULT_MODEL=openrouter/openai/gpt-4o-mini` | ✅ |
| Analyze + plan + coding LLM (fix #58) | ✅ |
| `write_file` `homolog_ok.txt` = `HOMOLOG_OK` + `read_file` | ✅ |
| `task_execution_completed` (task_id=6) | ✅ |
| Arbiter | `escalate` (1 blocker na review) — pipeline ok |

**Conclusão aceite local:** **GO** para “runtime funciona de ponta a ponta na dev machine com LLM”.  
**Não é** GO de staging (#39–#43). Ver `documentos/PILOTO-ESCOPO.md` § Veredito.

Checklist UAT formal (staging): issue #43.

---

## 5. O que este path **não** prova

| Item | Precisa |
|------|---------|
| Ingress / TLS staging.squadx.dev | #39 cluster |
| Deploy job CI `squadx-staging` | #39 secrets |
| Multi-node client scaling | 2+ hosts |
| Packages pull *dentro* do cluster | imagePullSecret / public GHCR |

---

## Referências

- `scripts/homolog-client-host.sh`
- `client/deploy/README.md`
- `documentos/HOMOLOGACAO-VERIFICACAO.md` §§1–2, 4
- Makefile: `build-sandbox-images`, `build-egress-proxy`
