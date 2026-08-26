# Auditoria ADR-0011 — Codebase × Documentação (gap analysis)

- **Data:** 2026-08-26
- **Objeto:** verificar o estado real do repositório contra o planejado em `spec/ADR-0011-escopo-squadx-v2.md` (status: Proposto, 2026-08-10)
- **Método:** inspeção direta de arquivos, contagens via grep/find, histórico git

---

## Sumário executivo

| Dimensão | Resultado |
|---|---|
| Último commit | **2026-08-04** (`b0063a8`) — 22 dias sem commits; **nenhuma tarefa do ADR-0011 iniciada** |
| Lista "Preservar" | **14/14 ativos presentes** ✅ (1 deles não versionado ⚠️) |
| Lista "Descartar" | **0/9 executado** ❌ — tudo ainda no repo; webhook Stripe público **ativo** (`SecurityConfig.java:66`) |
| Tarefas derivadas | **0/8 executadas** ❌ (T-0011-1..8 todas pendentes, inclusive os 4 P0s) |
| Achado novo crítico | **~60 arquivos de trabalho não-commitados na `main`**, incluindo o pacote `controlpanel/` completo e as migrations V38–V54 |

---

## 1. Estado do repositório (fatos)

- Branch atual: `main`. Último commit: `b0063a8` (2026-08-04, stack postgres-live/minio).
- Working tree suja: **42 arquivos modificados (+799/−92)** e dezenas de arquivos **untracked**.
- Untracked inclui:
  - `backend/.../controlpanel/` — pacote inteiro (controller, dto, event, mapper, mcp, model, repository, service, validation)
  - 4+ controllers novos: `HarnessController`, `CodeIntelligenceController`, `ExecutionArtifactController`, `InternalTaskController`
  - `intelligence/`, `dto/harness/`, `dto/artifact/`, eventos novos
  - Migrations **V38–V54** (git só conhece até **V37** — por isso o ADR cita V37)
  - `DIAGNOSTIC-SQUADX-2026-08-10.md` (citado no ADR como ativo a preservar)
  - Os próprios **ADR-0010/0011/0012 do `spec/`**
- Contagem de controllers commitados: **37** (auditoria do ADR via 33; superfície cresceu, contra a meta do v2 de cair para ~12).

## 2. Lista "Preservar" — verificação

| Ativo no ADR | Existe? | Observação |
|---|---|---|
| `client/` integral | ✅ | 749 testes coletados (ADR citava 744) |
| `egress_sidecar.py` | ✅ | `client/squadx_client/docker/`, 58 LOC |
| `network_policy.py` | ✅ | |
| `test_architecture_guards.py` | ✅ | 246 LOC |
| Loop LangGraph de 8 nós | ✅ | `graph.py:78-85`: analyze, plan, execute, review, arbiter, escalate, commit, error |
| 7 especialistas | ✅ | `agents/factory.py` |
| `hardening.py`, `lifecycle.py`, `sandbox/paths.py` | ✅ | |
| Corpus 2026-06-09 (ADRs 0001–0006, RFCs 0001–0004) | ✅ | `docs/adr/`, `docs/rfc/` (hoje vão até RFC-0006) |
| `SquadX-dev-Spec-Dominio.md` | ✅ | `spec/` |
| `squadx-control-panel.html` | ✅ | `spec/` |
| Backend núcleo (ExecutionController, WebSocketEventService, RunAdmissionService, TaskController, AuthController/RBAC, StompSubscriptionAuthorizer) | ✅ | todos presentes |
| Postgres + Flyway + Redis | ✅ | V1–V37 commitadas; V38–V54 só em disco |
| `DIAGNOSTIC-SQUADX-2026-08-10.md` | ⚠️ | existe, mas **untracked** — risco de perda |

**Veredito:** nada do que era para preservar foi perdido. Mas dois ativos preserváveis estão apenas no disco.

## 3. Lista "Descartar" — verificação

| Item | Ação prevista | Estado real (26/08) |
|---|---|---|
| Billing / Stripe | Remover ou flag off; **webhook desligado** | ❌ `BillingController`, `BillingService`, `Subscription`, V8 presentes; **webhook público ativo** em `SecurityConfig.java:66` (`.requestMatchers("/api/v1/billing/webhook").permitAll()`) |
| Calendário | Remover | ❌ `CalendarSyncController` presente |
| Reuniões, gravações, highlights, AiAnalysisService | Remover | ❌ `MeetingController`, `RecordingController`, `HighlightController` presentes |
| White-label | Remover | ❌ `BrandController` presente |
| Templates de equipe | Remover | ❌ `TemplateController` presente |
| `mobile/` | Arquivar | ❌ presente |
| `desktop/` | Arquivar | ❌ presente |
| `documentos/KanbanBoard.tsx` | Remover | ❌ presente |
| Fontes de verdade paralelas | Consolidar | ❌ **piorou**: numeração de ADR agora colide — `docs/adr/ADR-0010-code-intelligence...` ≠ `spec/ADR-0010-direcao-autoria-spec.md`; idem ADR-0011 |

**Veredito:** zero execução. A superfície de ataque apontada pelo ADR (webhook público sem produto) segue exposta.

## 4. Tarefas derivadas — verificação

| # | Prioridade | Tarefa | Estado |
|---|---|---|---|
| T-0011-1 | P0 | Sessão T-000 (fixar N; disposição mobile/desktop/billing/squad-maps); **LICENSE ausente** | ❌ Sem `LICENSE*` na raiz (apenas badge MIT no README; ironia: `squad-maps/` tem LICENSE próprio) |
| T-0011-2 | P0 | `ruff check --fix` → CI verde; republicar imagens | ❌ **88 erros** ruff hoje (81 auto-fixable) |
| T-0011-3 | P0 | Reclassificar ADRs 0001–0006 como "Aceito — não implementado" | ❌ Sem a nota em `docs/adr/` |
| T-0011-4 | P0 | Corrigir `CLAUDE.md` | ❌ Confirmado falso: diz "**no @PreAuthorize annotations**" (existem **17** em 3 controllers); diz Python **3.12** (CI usa **3.11**, `ci.yml:13`); implica `validateUserAccess` universal (presente em **11 de 51** services) |
| T-0011-5 | P1 | Remover domínios descartados, um commit por domínio | ❌ Nenhum removido |
| T-0011-6 | P1 | RFC do laço mínimo decisão→tarefa | ❌ Não existe (RFCs param no 0006) |
| T-0011-7 | P1 | Fechar `IN_REVIEW → DONE` | ❌ Transição **ativa** em `TaskStatusTransition.java` (`IN_REVIEW → Set.of(DONE, IN_PROGRESS, CANCELLED)`), contradizendo ADR-0004 |
| T-0011-8 | P2 | Instrumentar taxa de sucesso por tarefa | ❌ Sem evidência |

## 5. Achados novos (fora do escopo literal do ADR)

1. **Risco de perda de trabalho (maior risco atual).** O início do Control Panel — pacote `controlpanel/`, harness connectors, code intelligence, spec versions, migrations V38–V54 — existe **somente no disco**, na `main`, sem commit, sem CI, sem backup remoto. Um `git checkout .` acidental destruiria semanas de trabalho. Isso precede qualquer item do ADR: **primeiro commitar (ou branchar) esse estado**.
2. **Decisões do v2 não versionadas.** ADR-0010/0011/0012 vivem só em `spec/` local. A "fonte de verdade" do v2 não está sob controle de versão — violando o espírito do próprio Control Panel.
3. **Colisão de numeração de ADRs.** Duas séries paralelas (`docs/adr/` até ADR-0011 vs `spec/` ADR-0010–0012) com temas distintos. Resolver antes de materializar specs: renumerar a série do `spec/` (ex.: ADR-0013+) ou declarar `docs/adr/` canônico e migrar.
4. **Superfície cresceu desde a auditoria** (33→37 controllers commitados, +~5 untracked). A tese do ADR (reduzir para ~12) fica mais distante a cada semana sem execução do Descartar.

## 6. Ordem sugerida de execução (proposta)

1. **P0-imediato (antes de tudo):** colocar o trabalho não-commitado sob versionamento — branch `feat/control-panel-wip` ou commits atômicos na main; incluir `spec/*.md` e `DIAGNOSTIC`.
2. **T-0011-1** sessão T-000 (decisões + `LICENSE`).
3. **T-0011-2** `ruff --fix` + CI verde + imagens.
4. **T-0011-4 + T-0011-3** corrigir CLAUDE.md e reclassificar ADRs (meia hora, documental).
5. **T-0011-5** descartes, um domínio por commit, CI verde entre cada (billing primeiro — remove o webhook público).
6. **T-0011-7** fechar `IN_REVIEW→DONE`; **T-0011-6** RFC do laço; **T-0011-8** instrumentação.

## 7. Conclusão

O ADR-0011 está **íntegro como documento** (todos os ativos a preservar existem) e **intocado como plano** (nada do Descartar/Tarefas foi executado). Entre a escrita do ADR e hoje, o repositório acumulou um terceiro estado que o ADR não previu: **construção em andamento sem versionamento**. A recomendação central desta auditoria é tratar isso como o item 0 da fila, antes de qualquer tarefa do próprio ADR.

---

## Adendo (26/08, tarde) — Control Panel: corpus × implementação

**Pergunta auditada:** temos ADRs/RFCs/OpenSpec aplicados *dentro* do SquadX, com telas de acompanhamento?

**Corpus de referência (`spec/`):** `PROMPT-ControlPanel-spec.md` (meta-prompt, decomposição em 6 capabilities), `SquadX-dev-Spec-Dominio.md` (domínio autoritativo, 8 seções), `squadx-control-panel.html` (mock UX, ~860 linhas), `SquadX-dev-Spec-Proposta.pdf` (racional).

### Backend — SIM, implementado conforme a spec (porém 100% não-commitado)

O pacote `controlpanel/` (untracked) cobre as 6 capabilities do prompt e as 8 seções do domínio:

| Seção da spec de domínio | Implementação encontrada | Status |
|---|---|---|
| §1 Entidades | Models: `Change`, `SpecVersion`, `SpecEvent`, `SpecTask`, `Requirement`, `Scenario`, `Pass5Run` + enums (`AssigneeType`, `ChangePhase`, `EventSource`, `Pass5Result`, `RequirementType`, `TaskEventType`) | ✅ |
| §2 Máquina de status | `SpecTaskStatus`: exatamente os 6 estados (`A_FAZER…AJUSTES`) + javadoc "**CONCLUIDA e AJUSTES só são atribuídos pelo Pass 5**"; `SpecTaskStateMachine` | ✅ fiel |
| §3 Contrato MCP | `WorkspaceToolService` implementa as 6 tools: `getChange`(:65), `getTasks`(:75), `updateTaskStatus`(:87), `reportBlocker`(:105), `materializeChange`(:113), `scaffoldTests`(:125); sessões (`WorkspaceSession*`, filtro, auth) | ✅ = RFC-0001 |
| §4 Versionamento/materialização | `SpecVersionService` + `GitSpecMaterializer` grava `openspec/changes/{id}/spec.md`; `GitWebhookController/Service` | ✅ = ADR-0001 |
| §5 Pass 5 | `Pass5Service`, `Pass5Run`, `CoverageService`, `ScenarioCoverageController` | ✅ = RFC-0004 |
| §6 Spec→testes | `scaffoldTests` gera esqueleto JUnit rastreável (com guardas de path escape) | ✅ |
| §7 Duas pistas | Humano: webhook de Git; Agente: sessões MCP por harness | ✅ |
| Conectores/Harness | `HarnessController`, migrations V51–V52 (`harness_connectors`, `link_agents_to_harnesses`) | ✅ |

Migrações V53 (`spec_event_actor`) e V54 (`spec_versions`) suportam o modelo. **`openspec/changes/add-control-panel/tasks.md` marca 50/50 tarefas concluídas** (versão de trabalho).

### Frontend — PARCIAL (mapa de telas §8 coberto em fração)

| Tela prevista (§8) | Estado |
|---|---|
| Dashboard por projeto ("onde estamos", atividade ao vivo) | ❌ não visto além do dashboard genérico existente |
| Specs/Workspace (histórico de versões, requisitos→tarefas, gerar testes) | ⚠️ `/changes/page.tsx` existe (71 LOC — lista básica) |
| Tarefas lista + kanban filtrável | ❌ kanban spec-native não visto |
| Detalhe da tarefa (briefing, cenários WHEN/THEN, portão Pass 5) | ⚠️ componentes `pass5-panel.tsx` e `spec-task-event-timeline.tsx` existem; integração completa não confirmada |
| Conectores (status + seletor de modelo LLM) | ⚠️ `/harnesses/page.tsx` existe (57 LOC) |
| Validação · Pass 5 (fila + cobertura cenário↔teste) | ⚠️ `pass5-panel.tsx` cobre parte; fila dedicada não vista |

API client: `changesApi` e `specTaskEventsApi` presentes em `api.ts`.

### Veredito do adendo

- **Núcleo (backend): a aplicação de ADR/RFC/OpenSpec como produto dentro do SquadX EXISTE e é fiel à spec de domínio** — mas inteira não-commitada.
- **Telas: parcialmente** — páginas e componentes-chave existem, porém abaixo do mapa de UX do protótipo.
- **Risco dominante permanece o mesmo da seção 5:** semanas de implementação do próprio Control Panel vivem apenas no disco local, na `main`, sem versionamento. O `tasks.md 50/50` descreve o disco, não o remoto.
