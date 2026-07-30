# ADR-0009 — Runtime de sandbox pluggable (Docker vs OS-primitives vs microVM)

## Status

**Aceito** — 2026-07-30.  
Decisão de produto/engenharia: sandbox pluggable + packaging Dev LIGHT / Team DOCKER /
Enterprise. Complementa (não substitui):

- `documentos/DECISAO-ARQUITETURAL-SANDBOXING.md` (Fase 1 Docker → 2 gVisor → 3 Firecracker)
- ADR-0007 (governança / sandbox-hardening de *policy*)
- ADR-0008 / RFC-0006 (egress em nível de rede)
- `documentos/ARQUITETURA-RUNTIME.md` (deploy: k8s ≠ sandbox)

## Contexto

O SquadX executa **código gerado por agentes** (loop nativo LangGraph ou CLI externa) e,
opcionalmente, expõe **Live View** (VNC→WebRTC). Hoje o isolamento é:

```text
Host com Docker
  └── squadx-client (daemon)
        ├── container agent (hardened)
        └── sidecar egress-proxy (netns compartilhado)
```

Kubernetes isola o **painel** (frontend/backend/Postgres/Redis), **não** o agente.
Pergunta recorrente: *existe algo mais leve que Docker/K8s para sandbox de agentes?*

Pesquisa de mercado (coding agents 2025–2026): Claude Code / Anthropic sandbox-runtime
usam **bubblewrap** (Linux) e **Seatbelt** (macOS); Codex e Cursor usam **Landlock +
seccomp** (Linux) / Seatbelt (macOS); plataformas multi-tenant (E2B, etc.) usam
**Firecracker**. Docker continua comum, mas **não é a única** opção “leve”.

## Decisão (proposta)

1. **Manter Docker hardened + egress sidecar como runtime default** do daemon
   (`SandboxRuntime.DOCKER` / Fase 1). É o que encaixa Live View, External CLI e
   toolchain completa sem reescrever o client.

2. **Tratar o isolador como pluggable** sob um contrato único `SandboxBackend`
   (nome de trabalho), com backends:

   | Backend | Nome | Quando |
   |---------|------|--------|
   | **A. Process / OS-primitives** | `PROCESS` | Dev laptop, “sem Docker”, threat model fraco |
   | **B. Container** | `DOCKER` (+ opcional `GVISOR`) | Default piloto / SaaS com client-host |
   | **C. MicroVM** | `FIRECRACKER` / Kata | Enterprise multi-tenant / compliance |
   | **D. Remote sandbox** | `REMOTE` (E2B-like) | Zero install no cliente (trade-off soberania) |

3. **Não exigir K8s para isolar agentes.** K8s permanece opcional para o control plane.

4. **Confirmar o caminho da spec antiga:** Docker → gVisor → Firecracker continua
   válido para **endurecer** isolamento; o backend **PROCESS** é o caminho **mais leve**,
   não um substituto de Firecracker.

## Alternativas consideradas

### A — Só Docker (status quo)

- **Prós:** já implementado; imagens `agent` / `agent:live`; egress sidecar; External CLI.
- **Contras:** usuário precisa de Docker Engine; mais pesado que bubblewrap no laptop;
  kernel compartilhado (escape teórico).

### B — Só OS-primitives (bubblewrap / Landlock / Seatbelt)

- **Prós:** mais leve; sem daemon Docker; alinhado a Claude Code / Codex / Cursor no desktop.
- **Contras:** kernel do host; Live View VNC e stack completa de imagem ficam difíceis;
  multi-tenant fraco; egress policy (RFC-0006) precisa ser reimplementada (proxy userspace,
  não netns/ipset do sidecar).

### C — Só microVM (Firecracker)

- **Prós:** melhor isolamento.
- **Contras:** KVM, ops pesada, **não** é “mais leve” que Docker; overkill para MVP local.

### D — Sandbox cloud gerenciado

- **Prós:** zero Docker no cliente.
- **Contras:** código/dados no provedor (bate no posicionamento “código local”); custo;
  latência; acoplamento.

### E — WASM

- **Prós:** levíssimo.
- **Contras:** inadequado para bash/git/CLI coding agents full-stack. **Descartado** como
  runtime principal.

## Contrato `SandboxBackend` (esboço)

Interface lógica (Python, client). Não é API pública ainda.

```text
start(task, policy) → handle
  - workspace mount / copy
  - env scrubbed (provider keys)
  - network policy (allowlist | deny | off)
exec(handle, cmd | stream) → logs / exit
  - usado pelo loop nativo e pelo External CLI
fs read/write/list (handle, path)
stop(handle) / ttl / metrics
optional: vnc_endpoint(handle)   # só backends que suportam live
```

Mapeamento inicial:

| Método | DOCKER (hoje) | PROCESS (futuro) | FIRECRACKER (futuro) |
|--------|---------------|------------------|----------------------|
| start | `containers.run` + sidecar | `bwrap`/`sandbox-exec` + filho | microVM + guest agent |
| network | egress-proxy netns | proxy HTTP/DNS userspace | NIC guest + firewall host |
| live VNC | imagem `:live` | **não** (ou X11 host arriscado) | guest com VNC se image permitir |
| install deps | no container | no workspace host (perigoso) / tmp | no guest |

Config proposta (env):

```bash
SQUADX_SANDBOX_BACKEND=docker|process|firecracker|remote   # default: docker
SQUADX_SANDBOX_RUNTIME=docker|gvisor|firecracker           # sob backend docker (já esboçado)
```

## O que o usuário precisa instalar

| Backend | Linux | macOS | Notas |
|---------|-------|-------|--------|
| **process** | kernel + `bubblewrap` (ou Landlock tools) | Seatbelt nativo | Sem Docker; ideal dev |
| **docker** | Docker Engine + `xt_set` se egress on | Colima/Docker Desktop | **Default atual** |
| **gvisor** | Docker + `runsc` | limitado | `SandboxRuntime.GVISOR` |
| **firecracker** | KVM + firecracker/containerd | difícil nativo | Host dedicado / cloud |
| **remote** | só rede + token | só rede + token | Conta no provedor |

**K8s:** só se for hospedar o **painel** (staging/prod). O agent host continua à parte.

## Impacto no código (client)

| Área | Impacto |
|------|---------|
| `docker/manager.py`, `sandbox.py` | Extrair interface; implementação Docker vira um backend |
| `egress_sidecar.py` / RFC-0006 | Acoplado a Docker netns; PROCESS precisa de **outro** enforcement |
| `hardening.py` | Já tem `SandboxRuntime` DOCKER/GVISOR/FIRECRACKER — estender com `PROCESS` / unificar nomes |
| `agents/external_cli_agent.py` | Hoje assume `execute_streaming` no container; PROCESS = subprocess + cwd workspace |
| Live View | Só backends com display/VNC; PROCESS = feature flag off |
| Warm pool | Específico Docker; PROCESS pode pool de processos ou nenhum |
| Config / deploy docs | Matriz “o que instalar” por backend; `client/deploy` só para DOCKER |

Backend Spring: **sem mudança obrigatória** no primeiro passo (continua despachando tasks por
STOMP). Opcional depois: `sandbox_backend` preferido por squad/org.

## Consequências

### Positivas

- Resposta clara a “precisamos de Docker/K8s?”: **K8s não; Docker default; leve = PROCESS**.
- Alinha mercado (desktop agents) com path **leve** sem abandonar o path **seguro** (microVM).
- Spec antiga (fases Docker→gVisor→Firecracker) permanece o eixo de *upgrade de isolamento*.

### Negativas / riscos

- Dois+ backends = custo de manutenção e matriz de testes.
- PROCESS **não** substitui Docker para Live View + parity de imagem.
- Confundir “leve” com “seguro” em multi-tenant.

### Neutras

- Default continua Docker até PROCESS ser MVP e testado.
- Remote sandbox fica como opção de produto (BYO cloud), não core open-source obrigatório.

## Product packaging — variantes de entrega

O produto **não** é “Docker ou K8s”. O produto é:

> **Control plane** (API/UI) + **runtime de agentes** (claim → sandbox → logs/custo → opcional live).

Dois (e depois três) canais de *onde o agente roda* coexistem. O protocolo com o backend
(STOMP + REST claim/status/logs) é o **mesmo**.

```text
                 ┌──────────────────────────────┐
                 │  Control plane (API / UI)    │
                 │  SaaS SquadX  |  self-host   │
                 └───────────────┬──────────────┘
                                 │ STOMP / REST
          ┌──────────────────────┼──────────────────────┐
          ▼                                             ▼
┌─────────────────────┐                     ┌─────────────────────┐
│  Dev desktop (LIGHT)│                     │  Team / Enterprise  │
│  Mac mini / laptop  │                     │  VPS / bare metal   │
│  PROCESS (meta)     │                     │  DOCKER (+ gVisor / │
│  Colima hoje        │                     │  Firecracker depois)│
└─────────────────────┘                     └─────────────────────┘
```

### Matriz de SKUs / modos

| Modo | Quem é o usuário | Onde o **painel** roda | Onde o **agente** roda | Sandbox backend | K8s? |
|------|------------------|------------------------|------------------------|-----------------|------|
| **Dev LIGHT** | Dev individual | SaaS (ou backend local opcional) | Mac mini / laptop | `PROCESS` (alvo) · `DOCKER` via Colima (**hoje**) | Não |
| **Team DOCKER** | Time / PME | SaaS **ou** compose/VPS | **VPS com Docker** (1+ hosts) | `DOCKER` + egress | Não obrigatório |
| **Enterprise** | Compliance / multi-host | K8s self-host ou SaaS dedicated | N hosts Docker/KVM | `DOCKER` → `GVISOR` → `FIRECRACKER` | Sim (painel) |

### O que cada instalador entrega

#### 1) Instalador **Dev LIGHT** (Mac mini / laptop)

**Objetivo:** “um comando e o client está claimando tasks da API.”

| Entrega | Conteúdo |
|---------|----------|
| Dependências | Homebrew path: Python 3.11+, git, **Colima + Docker CLI** (hoje); depois opcional sem Docker se `PROCESS` existir |
| Pacote | `squadx-client` (venv / brew formula) |
| Imagens | pull `agent` (+ `:live` se Live View) e `egress-proxy` **ou** skip se backend PROCESS |
| Config wizard | `SQUADX_API_URL`, token, `OPENROUTER_API_KEY` / OpenAI / Anthropic, `SQUADX_DEFAULT_MODEL` |
| Serviço | opcional `launchd` (macOS) / user systemd |
| Doctor | `squadx-client doctor`: Docker/Colima up, imagens, ping API, key LLM presente |
| **Fora do escopo** | K8s, Postgres local, Java, painel self-host |

**Estado hoje:** viável com script (base: `scripts/install.sh` + Colima); formula Homebrew ainda placeholder. Smoke real no Mac mini (OpenRouter) prova o valor do modo.

**Meta PROCESS:** mesmo instalador, sem passo Colima/Docker, com feature Live View **off** ou degradada.

#### 2) Instalador **Team DOCKER** (VPS Linux)

**Objetivo:** “empresa sobe um VPS, instala Docker + client, aponta para a API (SaaS ou self-host).”

| Entrega | Conteúdo |
|---------|----------|
| Dependências | Docker Engine, (opcional) `xt_set` para egress fail-closed |
| Pacote | `client/deploy`: venv em `/opt/squadx-client`, `squadx-client.service` |
| Imagens | `make build-sandbox-images` **ou** pull GHCR (`agent`, `agent:live`, `egress-proxy`) |
| Config | `/etc/squadx/squadx-client.env` (API URL, token, LLM keys, policy) |
| Runtime | `SQUADX_SANDBOX_BACKEND=docker`, egress on por default |
| Doctor | daemon active, docker ok, claim de smoke task |
| **Painel** | **não** embutido: cliente usa SaaS **ou** stack separada (compose/k8s) |

**Estado hoje:** documentação e unit systemd já existem (`client/deploy/`); falta empacotar em `install-vps.sh` one-shot + release de imagens estável.

#### 3) Pacote **Enterprise** (painel + frota)

**Objetivo:** “self-host completo ou SaaS dedicado + N hosts de agente.”

| Entrega | Conteúdo |
|---------|----------|
| Painel | Helm / kustomize (`infra/k8s` overlays staging/prod) |
| Secrets | Sealed Secrets / External Secrets / CI secrets (não `change-me` no git) |
| Runtime hosts | Mesmo instalador Team DOCKER × N; claim/CAS garante 1 host por task |
| Upgrade path | gVisor se `runsc`; Firecracker quando KVM + requisito SOC2/multi-tenant |
| Observability | Prometheus/Grafana/Loki já esboçados em `infra/` |

**Estado hoje:** overlays + CI deploy-staging (bloqueado por secrets/cluster reais); client multi-host já é o modelo mental do deploy.

### Control plane: SaaS vs self-host (ortogonal ao sandbox)

| Painel | Dev LIGHT | Team DOCKER | Enterprise |
|--------|-----------|-------------|------------|
| **SaaS SquadX** | Default | Comum | Conta dedicada / VPC |
| **Self-host compose** | Opcional (dev full stack) | PME sem k8s | Raro |
| **Self-host k8s** | Não | Opcional | Default |

Instalador de **painel** ≠ instalador de **client**. São artefatos separados.

### Matriz de features por modo

| Feature | Dev LIGHT | Team DOCKER | Enterprise |
|---------|-----------|-------------|------------|
| Claim + logs + status | ✅ | ✅ | ✅ |
| LLM nativo (OpenRouter/OpenAI/…) | ✅ | ✅ | ✅ |
| External CLI no sandbox | 🟡 Colima/Docker; PROCESS TBD | ✅ | ✅ |
| Egress allowlist comprovado | 🟡 limitado no macOS | ✅ Linux + xt_set | ✅ |
| Live View | 🟡 se Docker+`:live`+Supabase | ✅ | ✅ |
| Multi-host scale-out | ❌ | ✅ (N VPS) | ✅ |
| gVisor / Firecracker | ❌ | opcional | sob trigger |
| “Código não sai da empresa” | laptop do dev | VPS + git interno | VPS/KVM + rede privada |

### Ordem de entrega recomendada (instaladores)

1. **Team DOCKER / VPS** — maior ROI: docs + systemd já existem; empacotar `install-vps.sh`.
2. **Dev LIGHT (Mac)** — Colima path agora; PROCESS quando o backend existir (ADR body acima).
3. **Enterprise** — endurecer staging/prod k8s (#39) + frota de clients.

### Mensagem comercial (uma linha cada)

- **Dev:** “Instale o client no Mac; o painel é SaaS (ou local se quiser).”
- **Time:** “Uma VPS com Docker roda seus agentes; o código fica na sua infra.”
- **Enterprise:** “Painel no seu cluster; agentes em hosts dedicados com isolamento reforçável.”

## Plano de implementação sugerido (não comprometido)

1. **Doc-only (este ADR + packaging)** — alinhamento de stakeholders. ✅ em curso
2. **`install-vps.sh` + doctor** — modo Team DOCKER.
3. **`install-mac-client.sh` (Colima)** — modo Dev LIGHT com Docker suave.
4. **Extrair interface** `SandboxBackend` sem mudar comportamento (DOCKER only).
5. **MVP PROCESS** no Linux (+ Seatbelt macOS depois): workspace bind + network proxy;
   Live View off; `SQUADX_SANDBOX_BACKEND=process`.
6. **gVisor** como drop-in quando `runsc` presente (já esboçado).
7. **Firecracker** quando houver host KVM e requisito enterprise.

## Critérios de aceite (quando implementar)

- [ ] Mesma task nativa roda em `DOCKER` e `PROCESS` (sem Live View no PROCESS).
- [ ] External CLI mínima (ex. um provider) em PROCESS *ou* documentada como DOCKER-only.
- [ ] Egress: PROCESS tem allowlist *ou* fail-closed se policy exigir sidecar.

## Changelog

| Data | Mudança |
|------|---------|
| 2026-07-30 | Hardening: `SandboxSession` hot path, path containment, factory table, PROCESS deadline timeout, honest PROCESS docs. |
| 2026-07-30 | Phase 4: `ProcessSandboxBackend` (bwrap/Seatbelt); `create_sandbox_session`; External CLI Docker-only. |
| 2026-07-30 | Phase 3: `install-mac-client.sh` + `smoke-mac.sh` + `DEV-LIGHT-MAC.md`; doctor Colima/DOCKER_HOST. |
| 2026-07-30 | Phase 2: `DockerSandboxBackend` + `create_agent_sandbox()`; daemon/orchestrator via factory; guards contra Docker SDK fora de `docker/`. |
| 2026-07-30 | Status **Aceito**. Contrato Python em `client/squadx_client/sandbox/` (Protocol + factory + `SQUADX_SANDBOX_BACKEND`); runtime default ainda `AgentSandbox` (Docker). Fase 1: `install-vps.sh` + `doctor`. |
| 2026-07-30 | Status **Proposto** + packaging SKUs. |
- [ ] Docs de install por OS e por backend.
- [ ] Testes de arquitetura: default continua DOCKER; PROCESS não quebra CI sem bwrap.

## Plano de implementação

Ver **[documentos/PLANO-ADR-0009-SANDBOX-E-INSTALADORES.md](../../documentos/PLANO-ADR-0009-SANDBOX-E-INSTALADORES.md)**
(fases 0–6, instaladores VPS/Mac, extract `SandboxBackend`, PROCESS, enterprise).

## Referências

### Internas

- `documentos/DECISAO-ARQUITETURAL-SANDBOXING.md`
- `documentos/THREAT-MODEL.md`, `documentos/ARQUITETURA-RUNTIME.md`
- `client/squadx_client/docker/hardening.py` (`SandboxRuntime`)
- ADR-0008, RFC-0006

### Externas (pesquisa 2025–2026)

- Anthropic sandbox-runtime / Claude Code: bubblewrap + Seatbelt
- OpenAI Codex / Cursor: Landlock + seccomp (+ Seatbelt no macOS)
- Firecracker + E2B: microVM para multi-tenant
- Surveys: “AI agent sandboxing 2026” (containers vs microVM vs OS primitives vs Wasm)

## Changelog

| Data | Nota |
|------|------|
| 2026-07-30 | Proposto (draft pós-pesquisa de alternativas leves a Docker/K8s) |
| 2026-07-30 | Seção **Product packaging**: Dev LIGHT / Team DOCKER / Enterprise + o que cada instalador entrega |
