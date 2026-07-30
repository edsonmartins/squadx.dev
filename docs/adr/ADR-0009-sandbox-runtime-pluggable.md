# ADR-0009 — Runtime de sandbox pluggable (Docker vs OS-primitives vs microVM)

## Status

**Proposto** — 2026-07-30.  
Draft para decisão de produto/engenharia. Não implementa código; registra o mapa de
alternativas e o impacto no client. Complementa (não substitui):

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

## Plano de implementação sugerido (não comprometido)

1. **Doc-only (este ADR)** — alinhamento de stakeholders.
2. **Extrair interface** `SandboxBackend` sem mudar comportamento (DOCKER only).
3. **MVP PROCESS** no Linux: bubblewrap + workspace bind + network proxy simples;
   desliga Live View; `SQUADX_SANDBOX_BACKEND=process`.
4. **gVisor** como drop-in quando `runsc` presente (já esboçado).
5. **Firecracker** quando houver host KVM e requisito enterprise.

## Critérios de aceite (quando implementar)

- [ ] Mesma task nativa roda em `DOCKER` e `PROCESS` (sem Live View no PROCESS).
- [ ] External CLI mínima (ex. um provider) em PROCESS *ou* documentada como DOCKER-only.
- [ ] Egress: PROCESS tem allowlist *ou* fail-closed se policy exigir sidecar.
- [ ] Docs de install por OS e por backend.
- [ ] Testes de arquitetura: default continua DOCKER; PROCESS não quebra CI sem bwrap.

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
