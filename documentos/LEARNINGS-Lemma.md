# Aprendizados do Lemma para o SquadX.dev

> Estudo de `~/desenvolvimento/lemma-platform` (uma plataforma "workspace compartilhado para
> humanos + agentes de IA") e o que dele agrega valor ao SquadX.dev. Documento de
> **contexto/aprendizado** (precedência mais baixa que CONSTITUTION/ADR/RFC/spec). Onde uma
> lição já é decisão nossa, aponto o ADR/RFC; onde é genuinamente nova, marco como candidata a
> ADR/RFC futuro.
>
> Fontes lidas: `README.md`, `agentbox/` + `agentbox-client/`, `lemma-cli/lemma_cli/daemon/`
> (`runner.py`, `mcp.py`, `catalog.py`, `harnesses/*`), `lemma-python/`, `lemma-typescript/`,
> `lemma-skills/`, `lemma-backend/app/modules/`, `lemma-stack/`, `desktop/`, `lemma-frontend/`
> (`styles/tokens.css`, `scripts/audit-design-system.mjs`, `lib/assistant/display-resource.ts`),
> `docs/design/` e `docs/security/`.

## O que é o Lemma

Não é concorrente direto: é um **builder de business-apps/tabelas/workflows** onde a unidade de
trabalho é um *data-app* ("pod"). O SquadX tem por unidade um *projeto de software + repo git*. Mas
a **maquinaria** é quase a mesma que a nossa — FastAPI + Next.js + daemon Python + Tauri, agentes em
sandbox, loop nativo **ou** CLIs de código externas (Claude Code/Codex/OpenCode/Cursor). É
**evolução paralela**: por isso as escolhas do Lemma são raras de tão transferíveis.

Tese central do produto (`README.md`): **"chat não é onde o trabalho vive"** — a saída do agente
deve virar artefato durável, com dono e permissão, numa UI própria, não uma transcrição. A frase que
vale roubar para o nosso posicionamento: *"a task queue, not a terminal session that evaporates."*

## O ponto mais importante: **já decidimos quase tudo o que importa**

O Lemma **valida externamente** a arquitetura que os nossos ADR/RFC já ratificaram. Não estamos
atrás conceitualmente — em governança estamos em paridade ou à frente. O valor do Lemma é (1)
validação, (2) **cookbook de implementação** do que decidimos mas ainda não construímos, (3) algumas
ideias genuinamente novas (design system, `display_resource`, hardening de sandbox, gates de CI).

Legenda: ✅ já decidido/implementado · 🟡 decidido, **código pendente** · ⚠️ lacuna nova (vale
implementar) · 🔭 direção futura · ❌ não copiar.

| # | Conceito no Lemma | Onde no Lemma | Estado no SquadX | Referência SquadX |
|---|---|---|---|---|
| 1 | **MCP como contrato único, harness-agnóstico** | `daemon/mcp.py`, `harnesses/registry.py` | ✅ decidido; 🟡 **código ausente nesta branch** (fonte em `22f9c4f`) | ADR-0003, RFC-0001; `client/squadx_client/mcp/` (só `.pyc`) |
| 2 | **Núcleo orientado a eventos; UI = projeção** | `core/pubsub`, `core/domain/realtime.py` | ✅ decidido | ADR-0002 |
| 3 | **Máquina de estados de tarefa com gate objetivo** | `workflow/execution/engine.py` (+ `run_waits`) | ✅ decidido — e **mais opinado** (Pass 5; agente nunca marca "concluída") | ADR-0004 |
| 4 | **Run Admission** (start/drop_duplicate/queue_follow_up/needs_human_decision + idempotency) | `dispatcher/admission.ts` (ref. do OpenTag) | 🟡 migração V34 + `RunAdmissionService` existem | RFC-0005 §2 |
| 5 | **Attention Budget** (visibility human/audit/debug + importance) | bus durável (Redis Streams) vs transiente (pub/sub) | 🟡 V33 + colunas em `execution_logs` | RFC-0005 §1, ADR-0007 |
| 6 | **Context Packet** (input curado/auditável) | — | 🟡 `orchestrator/context_packet.py` já existe no client | ADR-0007 (C) |
| 7 | **Gate de aprovação humana + destructive-action gate** | `authorization/session_approvals.py` (Redis TTL, fail-closed, hash de args) | ⚠️ **`Approval` existe mas está órfão** (nunca liga na execução) | `model/Approval.java`, `ApprovalService.java`, page `approvals/` |
| 8 | **Disable native tools do CLI; I/O via MCP** | `mcp.py` (`--disallowedTools`, `shell_tool=false`) | 🔭 nosso modelo é git-worktree (I/O real no repo) — aplicar só à *coordenação*, não a file-ops | `agents/external_cli_agent.py` (`self.tools=[]`) |
| 9 | **Fallback genérico por template de comando** (`{model}/{prompt}/{mcp_*}`) | `runner.py::run_provider_command` | ⚠️ hoje 5 providers hardcoded | `agents/external_cli_agent.py::_build_command` |
| 10 | **Hold-not-kill + heartbeat com capacidade + admission por capacidade** | `runner.py` (`_HeldRun`, 150s grace) | ⚠️ streaming morre se cai transporte | `docker/sandbox.py::execute_streaming` |
| 11 | **Descoberta de modelos + BYO-subscription + alias→modelo padrão** | `catalog.py` (`normalize_provider_model_name`) | ⚠️ lista estática por provider | `agents/factory.py`, `external_cli_agent.py` |
| 12 | **Sandbox de duas camadas** (manager fino + HTTP server *dentro* do sandbox) atrás de um `SandboxProvider` Protocol | `agentbox/providers/*`, `runtime_server.py` | 🔭 hoje `docker exec` por comando | `docker/sandbox.py` |
| 13 | **gVisor + cap-drop ALL + seccomp + non-root + nós isolados** | `agentbox/kubernetes.py` | ⚠️ hardening explícito vale mesmo em Docker puro | `documentos/DECISAO-ARQUITETURAL-SANDBOXING.md` |
| 14 | **Reaper por `active_operations` (ref-count) + heartbeat de sessão** | `agentbox/state.py`, `sessions.py` | ⚠️ warm-pool precisa de sinal seguro de reclaim | `docker/` (warm pool recente) |
| 15 | **Tokens assinados, TTL, escopados por (sandbox, app) p/ o live-view** | `agentbox/api/apps.py` | ⚠️ VNC→WebRTC precisa desse modelo de auth | `live/` (frontend), streaming client |
| 16 | **Sistema de design tokens 3 camadas + linter ratchet em CI** | `styles/tokens.css`, `scripts/audit-design-system.mjs` | ✅ **implementado nesta leva** (ver abaixo) | `frontend/src/styles/tokens.css`, `scripts/audit-design-system.mjs` |
| 17 | **`display_resource`: saída do agente vira deep-link p/ página real ou widget iframe sandboxed** | `lib/assistant/display-resource.ts`, `inline-widget.tsx` | ⚠️ escape-hatch de extensibilidade que não temos | rotas `app/(dashboard)/<feature>` |
| 18 | **Cards HITL tipados** (`request_approval` once/session/deny; `ask_user`) | `assistant-approval-cards.tsx` | ⚠️ casa com a page `approvals/` + #7 | `approvals/` |
| 19 | **Gate de drift de codegen** (OpenAPI commitado → clientes regenerados; `git diff --exit-code`) | `.github/workflows/ci.yml`, `docs/security/generated-code-policy.md` | ⚠️ mesmo fan-out (Spring→Next+Python+Expo) skewa em silêncio | `.github/workflows` |
| 20 | **CI com path-filter + 1 gate agregador; runners arm64 nativos** | `ci.yml` (`dorny/paths-filter`) | ⚠️ monorepo multi-linguagem sem scoping | `.github/workflows` |
| 21 | **Threat model de 1 página centrado na fronteira do sandbox + scanners como gate de release** | `docs/security/threat-model.md`, `security.yml` | ⚠️ artefato de confiança B2B que falta | `docs/security` (criar) |
| 22 | **Tauri casca-fina + sidecar supervisor (PyInstaller) reusando o install do CLI** | `desktop/src/main.rs` (~715 linhas), `lemma-stack supervise` | ⚠️ temos daemon Python + Tauri v2 — encaixe quase perfeito | `desktop/`, `client/` |
| 23 | **Skill em formato `SKILL.md` instalada no Claude Code/Cursor** (SquadX como back-layer) | `lemma-cli/.../commands/skills.py`, `lemma-skills/*` | 🔭 top-of-funnel de baixa fricção | — |

## O que já implementamos nesta leva (código, não só doc)

1. **Sistema de design tokens semânticos (3 camadas)** — `frontend/src/styles/tokens.css`:
   primitivos (shadcn HSL) → **semânticos por significado** (`--action`, `--intelligence`,
   `--collaboration`, `--success-accent`, `--attention`, `--danger`) → **status** (mapeando o enum
   `TaskStatus` atual — `TODO/IN_PROGRESS/IN_REVIEW/BLOCKED/DONE/CANCELLED` — sobre os papéis
   semânticos). Expostos como utilitários Tailwind com slot `<alpha-value>` (soft fills via
   `bg-intelligence/10`). Dark mode sobrescreve os **mesmos nomes** → nenhum componente ramifica por
   tema. Fonte única de render de status em `frontend/src/lib/status-tokens.ts`, **adotada** em
   `task-detail-sheet.tsx` e `kanban-board.tsx` (removidos os mapas de cor hardcoded).
2. **Linter de design com ratchet** — `frontend/scripts/audit-design-system.mjs` +
   `design-audit-baseline.json` (`rawHex`, `rawPaletteColor`, `arbitraryColor`). Baseline atual:
   `rawHex=4`, `rawPaletteColor=128`. Regras: dívida só pode **cair**, nunca subir. Scripts
   `pnpm design:audit[:ci|:update]`.

> Nota de alinhamento: o frontend usa o enum **em inglês** (`TaskStatus`), enquanto ADR-0004
> descreve o alvo em PT (`a_fazer/em_curso/...`). Os tokens foram ancorados no enum **real**; quando
> a migração control-panel trocar o vocabulário, basta reetiquetar a Camada 3.

## Cookbook: onde o Lemma mostra o "como" do que já decidimos

- **MCP `workspace` (ADR-0003/RFC-0001):** `lemma-cli/.../daemon/mcp.py` é uma receita quase completa
  de injeção por harness (Claude Code `--mcp-config --strict-mcp-config`, Codex `-c mcp_servers…`,
  OpenCode `OPENCODE_CONFIG_CONTENT`, Cursor `.cursor/mcp.json`). Nota importante: **o nosso MCP é de
  coordenação/briefing** (get_change/update_task_status), não substitui file/shell — porque o nosso
  agente **deve** escrever código real no worktree. Não copiar o "disable native tools" cegamente.
- **Session approval (RFC-0005 + gate #7):** `authorization/session_approvals.py` — TTL no Redis,
  **fail-closed** se o Redis cai, hash exato de `(tool_name, args)` para não aprovar prefixo e
  contrabandear comando extra. Modelo pronto para o nosso `DESTRUCTIVE_ACTIONS`.
- **Run Admission (RFC-0005 §2):** `dispatcher/admission.ts` confirma a nossa decisão de
  idempotency-key + follow-up durável; nossa V34 já tem a persistência.
- **Attention Budget (RFC-0005 §1):** o split de barramento do Lemma (Redis Streams durável vs
  pub/sub transiente) é a implementação natural do `visibility` — hoje conflado no STOMP.
- **Resiliência de run:** `runner.py` (hold-not-kill, grace 150s, heartbeat com `active_run_count`)
  é o blueprint para o nosso `execute_streaming` sobreviver a quedas de transporte.

## Lacunas novas com valor (candidatas a ADR/RFC)

- **#7 Ligar o gate de aprovação humana à execução** — *maior valor, menor conflito*. Temos
  `Approval` + `ApprovalService` + controller + page, **100% órfãos**. Wiring: (a) client emite
  `approval_requested` nos nós `escalate`/`commit_changes`; (b) backend ganha um `type` novo em
  `ExecutionService.handleDaemonTaskUpdate` que chama `ApprovalService.create` e estaciona a tarefa
  (`IN_REVIEW`/`BLOCKED`) até `ApprovalService.review` retomar. Alinha com ADR-0004 e com os cards
  HITL do Lemma (#18).
- **#9/#11 Harness protocol + template genérico + descoberta de modelos** — transforma "adicionar
  provider" em config; `catalog.py` é copiável quase direto.
- **#16/#17/#18 Design system + `display_resource` + cards HITL** — a redesign "Mission Control" é o
  momento; tokens já feitos, falta `display_resource` e widgets.
- **#19/#20/#21 Gates de engenharia** — codegen-drift, path-filter CI, threat model. Baixo esforço,
  alto retorno (velocidade + confiança B2B).
- **#13/#14/#15 Hardening de sandbox** — cap-drop/seccomp/non-root, reaper por ref-count, tokens
  assinados no live-view. Coordena com `DECISAO-ARQUITETURAL-SANDBOXING.md`.

## O que NÃO copiar

- ❌ **Pod-como-diretório-de-arquivos / bundle proprietário** para *repos*: o git já é esse artefato.
  Aplicável só a *templates* de squad/agente (config), não a código.
- ❌ **Superfícies de chat** (WhatsApp/Telegram) — fora da tese de um orquestrador dev.
- ❌ **Desktop macOS-only pré-1.0** como *escopo* — o Lemma serve de referência de *arquitetura*
  (casca-fina + sidecar), não de escopo de produto.
- ❌ **Dual-license (AGPL core + Apache tooling)** — só relevante se abrirmos o código; o *princípio*
  (licenciar permissivamente o daemon/SDK/CLI para proliferar integrações) vale mesmo fechados.

## Ranking (impacto ÷ esforço)

| # | Melhoria | Área | Esforço | Nota |
|---|---|---|---|---|
| 1 | Ligar gate de aprovação humana à execução (#7) | backend+client | M | infra já existe, órfã |
| 2 | Design tokens + linter ratchet (#16) | frontend | — | **feito** nesta leva |
| 3 | Harness protocol + template genérico + `catalog.py` (#9/#11) | client | M | provider vira config |
| 4 | Restaurar/portar MCP `workspace` (#1) de `22f9c4f` | client+backend | M–A | decidido; código em outra branch |
| 5 | Gates de CI: codegen-drift + path-filter (#19/#20) | infra | B–M | maior ROI de engenharia |
| 6 | `display_resource` + widget iframe + cards HITL (#17/#18) | frontend | M | escape-hatch de extensibilidade |
| 7 | Hardening de sandbox + reaper ref-count + token de live-view (#13/#14/#15) | client | M–A | superfície de venda enterprise |
| 8 | Threat model de 1 página (#21) | docs | B | artefato de confiança B2B |
| 9 | Tauri casca-fina + sidecar supervisor (#22) | desktop | M | reusa daemon+CLI |
| 10 | Skill `SKILL.md` para Claude Code/Cursor (#23) | client | B–M | top-of-funnel |

## Relacionado

- ADR-0002, ADR-0003, ADR-0004, ADR-0007; RFC-0001, RFC-0003, RFC-0005.
- `documentos/LEARNINGS-OpenTag.md` (mesmo formato; governança A–D).
- `documentos/DECISAO-ARQUITETURAL-SANDBOXING.md` (fronteira do sandbox).
