# Plano de implementação — ADR-0009 (sandbox pluggable + instaladores)

**Status:** Em execução (Fases 0–1 no main; Fase 2 extract Docker em curso)  
**Data:** 2026-07-30  
**ADR:** [ADR-0009](../docs/adr/ADR-0009-sandbox-runtime-pluggable.md) (**Aceito**)  
**Objetivo:** Entregar isolamento de agentes em **variantes** (leve / Docker VPS / enterprise) e **instaladores** que deixem cada perfil pronto para uso.

---

## 1. Norte do plano

| Meta | Descrição |
|------|-----------|
| **P0** | Empresa sobe **VPS com Docker** e o client roda agentes com isolamento + egress (caminho atual industrializado) |
| **P1** | Dev no **Mac mini** instala em poucos passos (Colima/Docker “suave” + doctor) |
| **P2** | Runtime **PROCESS** (bubblewrap/Landlock/Seatbelt) — isolamento **sem** Docker no laptop |
| **P3** | Endurecimento **gVisor / Firecracker** + frota enterprise |

**Fora do escopo deste plano:** Control Panel / Pass 5; reescrever o backend Spring; WASM como runtime principal.

**Princípio:** o protocolo (STOMP claim/status/logs) é estável; só o `SandboxBackend` e o empacotamento mudam.

---

## 2. Estado atual (baseline)

| Área | Estado |
|------|--------|
| Docker hardened + sidecar egress | Implementado no client (`docker/`, RFC-0006) |
| `SandboxRuntime` DOCKER/GVISOR/FIRECRACKER | Esboço em `hardening.py`; gVisor/FC só se binário existir |
| Interface pluggable | **Não** extraída — `AgentSandbox` acoplado a Docker |
| `client/deploy/` | systemd + env example + README Linux |
| `scripts/install.sh` / brew formula | Esboços; repo URL / sha256 desatualizados |
| Smoke Mac + OpenRouter | GO local (ver `PILOTO-ESCOPO.md`) |
| Staging k8s real | NO-GO (#39 secrets/cluster) |

---

## 3. Arquitetura-alvo (lembrete)

```text
Control plane (SaaS ou self-host) ──STOMP/REST──► Runtime host
                                                   ├── LIGHT: PROCESS ou Colima
                                                   └── TEAM/ENT: Docker (+ upgrade path)
```

Ver packaging completo no ADR-0009 § Product packaging.

---

## 4. Fases e work packages

Estimativas em **eng-days** (1 pessoa familiar com o monorepo). Ordem é sequencial onde há dependência.

### Fase 0 — Governança e contratos (3–5 d)

**Objetivo:** ADR aceito + interface e config estáveis antes de fork do código.

| ID | Entrega | Critério de aceite | Est. |
|----|---------|-------------------|------|
| 0.1 | Aceitar ADR-0009 (status → Aceito) com packaging | Review stakeholders; changelog no ADR | 0.5 d |
| 0.2 | Spec da interface `SandboxBackend` (módulo + Protocol typing) | Doc + stub Python revisado; métodos: `start/exec/fs/stop/metrics` | 1 d |
| 0.3 | Config: `SQUADX_SANDBOX_BACKEND`, matriz de features (live, egress) | `config.py` + defaults; `doctor` lista backend | 1 d |
| 0.4 | Plano de testes por backend (unit + integration markers) | `pytest` markers `sandbox_docker` / `sandbox_process` | 0.5 d |
| 0.5 | Atualizar `ARQUITETURA-RUNTIME.md` + QUICKSTART com SKUs | Docs sem prometer PROCESS antes do merge | 0.5 d |

**Saída da fase:** time alinhado; PRs pequenos de “contrato” sem mudar comportamento default.

---

### Fase 1 — Industrializar **Team DOCKER** (VPS) (5–8 d)

**Objetivo:** instalador e operação de **VPS com Docker** prontos para cliente/piloto.

| ID | Entrega | Critério de aceite | Est. |
|----|---------|-------------------|------|
| 1.1 | `scripts/install-vps.sh` | Idempotente: user `squadx`, venv, unit systemd, env from example | 1.5 d |
| 1.2 | Pull ou build de imagens (GHCR) no install | `agent`, `agent:live`, `egress-proxy` presentes; tags documentadas | 1 d |
| 1.3 | `squadx-client doctor` (CLI) | Checks: docker, imagens, API health, token, LLM key, egress image | 1.5 d |
| 1.4 | Harden `client/deploy/README.md` + env example | OpenRouter + `SQUADX_DEFAULT_MODEL`; portas; troubleshooting | 0.5 d |
| 1.5 | Smoke automatizado VPS (script) | Após install: claim task (ou mock) + log “daemon ready” | 1 d |
| 1.6 | CI: build client image + documentar release tags | Job/release já parcial; checklist de release | 1 d |
| 1.7 | Egress IT em Linux (CI self-hosted ou doc runbook) | `SQUADX_DOCKER_IT=1` passa em host com `xt_set` **ou** runbook assinado | 1–2 d |

**Dependências:** GHCR push (#38) ✅; API acessível (SaaS ou self-host).  
**Não bloqueia:** k8s staging.

**Saída:** “Time sobe VPS e em &lt;30 min o client está claimando tasks.”

---

### Fase 2 — Extrair **SandboxBackend** Docker (refactor, sem feature nova) (4–6 d)

**Objetivo:** desacoplar orquestrador/agentes do Docker SDK para plugar PROCESS depois.

| ID | Entrega | Critério de aceite | Est. |
|----|---------|-------------------|------|
| 2.1 | Pacote `squadx_client/sandbox/` com Protocol + types | `Handle`, `NetworkPolicy`, erros tipados | 1 d |
| 2.2 | `DockerSandboxBackend` move lógica de `AgentSandbox`/`manager` | Suite client verde; zero regressão smoke | 2–3 d |
| 2.3 | Factory `get_sandbox_backend(settings)` | Default `docker`; env `SQUADX_SANDBOX_BACKEND` | 0.5 d |
| 2.4 | Adaptar `nodes.py`, `external_cli_agent`, warm pool | Só falam com a interface | 1 d |
| 2.5 | Guards de arquitetura (não importar docker SDK fora do backend) | Teste em `test_architecture_guards` | 0.5 d |

**Risco:** PR grande — preferir extrair em fatias (manager → backend, depois pool).

**Saída:** mesmo comportamento Docker; código pronto para um segundo backend.

---

### Fase 3 — Instalador **Dev LIGHT (Mac)** com Colima (3–5 d)

**Objetivo:** Mac mini / laptop do dev sem K8s e sem “montar stack Java” (modo client → SaaS ou API local opcional).

| ID | Entrega | Critério de aceite | Est. |
|----|---------|-------------------|------|
| 3.1 | `scripts/install-mac-client.sh` | brew deps, Colima start, venv client, pull imagens, wizard `.env` | 2 d |
| 3.2 | Integração `doctor` no Mac | Detecta `DOCKER_HOST` Colima; mensagens claras | 0.5 d |
| 3.3 | Homebrew formula real (tap) **ou** documentar “script only” até release | Formula com sha256 de tag real **ou** ADR: script first | 1 d |
| 3.4 | Doc “Dev LIGHT” (1 página) | Link no README + QUICKSTART sem claims falsos | 0.5 d |
| 3.5 | Smoke Mac automatizável | Script: install → doctor → (opcional) seed se API local | 1 d |

**Limitação documentada:** egress packet-proof no macOS ≠ Linux; Live View opcional.

**Saída:** Dev no Mac mini com ambiente de client “pronto” em um script.

---

### Fase 4 — Backend **PROCESS** (leve, sem Docker) (8–12 d)

**Objetivo:** isolamento por OS-primitives para Dev LIGHT sem Docker.

| ID | Entrega | Critério de aceite | Est. |
|----|---------|-------------------|------|
| 4.1 | Spike Linux bubblewrap (3–5 d spike timebox) | PoC: start workspace + `exec` bash + deny path fora do tree | 2 d |
| 4.2 | `ProcessSandboxBackend` (Linux) | `start/exec/fs/stop`; network: proxy allowlist **ou** fail-closed se policy exigir sidecar | 3 d |
| 4.3 | Seatbelt profile macOS (MVP) | Mesmo contrato; feature parity parcial OK | 2 d |
| 4.4 | Wire `SQUADX_SANDBOX_BACKEND=process` | Nativo loop roda task simples; External CLI **DOCKER-only** até 4.5 | 1 d |
| 4.5 | External CLI em PROCESS (opcional) | 1 provider **ou** doc explícita DOCKER-only | 1–2 d |
| 4.6 | Live View = unsupported em PROCESS | Doctor e runtime error claros | 0.5 d |
| 4.7 | Testes + docs install PROCESS | Marker `sandbox_process`; CI Linux com bwrap se disponível | 1 d |

**Saída:** Dev pode rodar sem Docker com isolamento “bom o bastante” para threat model laptop.

---

### Fase 5 — Enterprise / isolamento forte (paralelo, sob trigger) (6–15 d)

| ID | Entrega | Trigger | Est. |
|----|---------|---------|------|
| 5.1 | gVisor path real (`runsc` + testes) | Cliente enterprise ou policy | 2–3 d |
| 5.2 | Firecracker / microVM spike | SOC2 / multi-tenant host | 5–10 d |
| 5.3 | Frota multi-host (runbook + claim) | &gt;1 VPS | 1–2 d |
| 5.4 | Painel self-host k8s homolog (#39) | UAT staging | ops + secrets |

Não bloqueia P0/P1/P2 de instaladores.

---

### Fase 6 — Control plane self-host opcional (paralelo) (opcional 5–10 d)

Só se o SKU “empresa instala tudo na VPS” incluir **painel**:

| ID | Entrega |
|----|---------|
| 6.1 | `docker compose` “all-in-one” (postgres, redis, backend, frontend) com JWT/OAuth gotchas resolvidos |
| 6.2 | Doc “VPS full stack” vs “VPS só agents + SaaS API” |

Priorizar **só agents + SaaS** se quiser time-to-market.

---

## 5. Roadmap visual

```text
Semana 1–2     Fase 0 + início Fase 1 (VPS install + doctor)
Semana 2–3     Fecha Fase 1 (smoke VPS, egress runbook)
Semana 3–4     Fase 2 (extract SandboxBackend)
Semana 4–5     Fase 3 (install Mac Colima)
Semana 5–8      Fase 4 (PROCESS MVP Linux + macOS Seatbelt)
Sob demanda    Fase 5 (gVisor / Firecracker / k8s staging)
```

Ajuste se o time for &gt;1 pessoa: Fase 1 e spike PROCESS (4.1) em paralelo após 0.2.

---

## 6. Dependências e riscos

| Risco | Mitigação |
|-------|-----------|
| Refactor 2.x quebra smoke | Feature flag; smoke OpenRouter a cada merge na fatia |
| PROCESS fraco em segurança | Marketing honesto; default DOCKER em VPS; fail-closed se policy “strict” |
| Egress Docker ≠ PROCESS | Dois implementadores de policy; testes separados |
| Live View só Docker | Feature matrix no doctor e no painel (futuro) |
| Homebrew/release | Começar com script; formula quando houver tag estável |
| GHCR privado | imagePull / login no install-vps; ou packages public |

---

## 7. Critérios de “MVP de produto” (definição de pronto)

### MVP-A — Team DOCKER (primeiro GO comercial de runtime self-host)

- [ ] `install-vps.sh` em Ubuntu LTS limpa
- [ ] daemon systemd + doctor verde
- [ ] task real com LLM completa (como smoke OpenRouter)
- [ ] egress: IT ou runbook Linux assinado
- [ ] doc de 1 página “deploy VPS”

### MVP-B — Dev LIGHT (Mac)

- [ ] `install-mac-client.sh` em Mac Apple Silicon limpo (ou doc + script semi-auto)
- [ ] doctor + claim contra API SaaS/staging/local
- [ ] limitações (egress/live) documentadas

### MVP-C — PROCESS

- [ ] task nativa simples sem Docker no Linux
- [ ] `SQUADX_SANDBOX_BACKEND=process`
- [ ] Live View explicitamente unsupported

---

## 8. Work breakdown por repositório

| Área | Fases | Owners sugeridos |
|------|-------|------------------|
| `client/squadx_client/sandbox/` | 0, 2, 4, 5 | Client/Python |
| `client/deploy/`, `scripts/install-*.sh` | 1, 3 | DevOps + Client |
| `client` CLI `doctor` | 1, 3 | Client |
| `docs/adr`, `documentos/*` | 0, contínuo | Arquitetura |
| `infra/k8s`, CI | 5.4, 1.6 | DevOps |
| Backend Spring | só se expor `sandbox_backend` preferido por squad | Backend (fase tardia) |

---

## 9. Métricas de sucesso

| Métrica | Alvo |
|---------|------|
| Tempo install VPS (dev experiente) | &lt; 30 min cold |
| Tempo install Mac client | &lt; 20 min cold |
| Smoke regression Docker | verde em cada release client |
| PROCESS: cold start task trivial | &lt; 5 s até first tool (ordem de grandeza) |
| Issues de “não sei instalar” | queda após docs + doctor |

---

## 10. Decisões a fechar na Fase 0 (checklist)

- [ ] ADR-0009 **Aceito** ou “Aceito com ressalvas”
- [ ] Ordem comercial: VPS primeiro vs Mac primeiro (plano recomenda **VPS**)
- [ ] Live View: obrigatória no Team DOCKER? (recomenda: **sim** como target; Mac LIGHT opcional)
- [ ] External CLI em PROCESS: v1 ou só Docker?
- [ ] SaaS API obrigatória no Dev LIGHT ou full-local aceito?

---

## 11. Próxima ação imediata (próximos 5 dias)

1. Review + aceitar ADR-0009 (0.1).  
2. Abrir epic GitHub “ADR-0009 implementation” com issues por ID (1.1–1.7, 2.1–2.5).  
3. Implementar **1.1 + 1.3** (`install-vps.sh` + `doctor`) em paralelo com **0.2** (Protocol stub).  
4. Não começar PROCESS até 2.2 estar verde.

---

## 12. Referências

- ADR-0009, ADR-0008, RFC-0006  
- `documentos/ARQUITETURA-RUNTIME.md`  
- `documentos/PILOTO-ESCOPO.md` (GO local / NO-GO staging)  
- `documentos/DECISAO-ARQUITETURAL-SANDBOXING.md`  
- `client/deploy/README.md`  
