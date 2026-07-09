# Aprendizados do OpenTag para o SquadX.dev

> Estudo de `~/desenvolvimento/opentag` (Agent Work Protocol) e o que dele agrega valor ao
> trabalho em equipe do SquadX.dev. Documento de **contexto/aprendizado** (precedência mais baixa
> que CONSTITUTION/ADR/RFC/spec — ver `CONSTITUTION.md`). A decisão de adotar está em **ADR-0007**;
> o contrato em **RFC-0005**; o plano executável em `openspec/changes/agent-work-governance/`.

## O que é o OpenTag

Não é concorrente do SquadX. É um **protocolo** para levar o agente até a *thread onde o trabalho
já acontece* (GitHub, Slack, Lark, Telegram), em vez de criar "mais um lugar de IA". A tese, em três
palavras: **Invocation · Governance · Compression** — invocar o agente onde o trabalho vive, decidir
o que ele pode ver/fazer/devolver, e comprimir um processo grande em poucos artefatos que humanos
realmente consomem.

Fontes lidas: `README.md`, `docs/agent-work-protocol.md` (visão de longo prazo), `docs/thread-runtime-design.md`
(o que está implementado no v0.2) e a implementação real em `packages/{core,dispatcher,runner,store}`.

O SquadX está no eixo **oposto** de *invocation* (cria task no próprio frontend → STOMP → sandbox),
mas as camadas de **governance** e **compression** do OpenTag são quase todas aplicáveis — e várias
já existem parcialmente no SquadX.

## Mapeamento conceito → SquadX

Legenda: ✅ já existe · ⚠️ lacuna (vale implementar) · 🔭 direção futura.

| # | Conceito OpenTag | Onde no OpenTag | Estado no SquadX | Arquivo SquadX |
|---|---|---|---|---|
| 1 | **Run Admission** (dedup, follow-up, needs_human_decision, actor-allowed-for-write) | `packages/dispatcher/src/admission.ts` | ⚠️ só há check de `RUNNING` | `backend/.../service/ExecutionService.java:52` |
| 2 | **Attention Budget** (callbacks ack/progress/final/audit; `visibility` human/audit/debug; `importance`) | `docs/thread-runtime-design.md` Delta 7; `packages/dispatcher/src/presentation.ts` | ⚠️ todo log vai ao dashboard | `ExecutionLog.java`, `WebSocketEventService.java` |
| 3 | **Quiet Agent Protocol** (agente não fala se não foi chamado; uma voz por run) | `agent-work-protocol.md` §Quiet | 🔭 N agentes podem inundar a thread | `client/.../messaging/mailbox.py` |
| 4 | **Context Packet** (input curado/auditável: intent/facts/sources/exclusions/budget) | `thread-runtime-design.md` Delta 5/6 | ⚠️ concatenação solta | `client/.../agents/base.py:298` `_build_context_string` |
| 5 | **Proposal / Approval / Apply** (snapshot imutável, intents semânticos, supersessão por domínio, ApplyPlan com preflight + outcome por intent) | `agent-work-protocol.md` §Proposal/Approval/Apply | 🔭 review→repair produz findings, não propostas aprováveis | `client/.../orchestrator/nodes.py` |
| 6 | **Run lineage** (run termina no resultado; `nextAction` cria novo run com `parent_run_id`) | `agent-work-protocol.md` §Next Action | ✅ parcial via `cycle_count`/fix-subtask | `client/.../orchestrator/state.py`, `nodes.py:613` |
| 7 | **Agente-a-agente ≠ chat** (task graph + contratos + artefatos + máquina de estado, não IM) | `agent-work-protocol.md` §Workbench | 🔭 coordenação ainda como mensagens | `client/.../messaging/`, frontend |
| 8 | **Layering** core/adapters/recipes/policies/routers + ordem de resolução de policy | `agent-work-protocol.md` §Layering | 🔭 runtime adapter existe; router não | `client/.../agents/factory.py` (`EXTERNAL_CLI`) |
| 9 | **Worktree por run + cleanup de artefatos internos** (`.claude/.codex/.omx` via `git clean`) | `packages/runner/src/git.ts` | ✅ worktree existe; ⚠️ falta cleanup | `client/.../git/worktree.py` |
| 10 | **Runner security** (allowlist de env, scrub de segredos, detecção de prompt-injection) | `packages/runner/src/security.ts` | ⚠️ injeta chaves no sandbox sem scrub | `client/.../agents/external_cli_agent.py`, `docker/sandbox.py` |
| 11 | **Result artifact first** (taxonomia: root_cause_note, suggested_changes, verification_summary, patch, PR, risk_note, follow_up_task) | `agent-work-protocol.md` §Result Artifact | 🔭 resultado é texto/output | `client/.../orchestrator/nodes.py` (commit gate) |
| 12 | **Cost-cap como freio de loop** | (SquadX antecipou) | ✅ já existe | `client/.../orchestrator/state.py` (`cost_budget_usd`), `nodes.py:595` |

## O que o SquadX já tem (não reinventar)

- **Worktree-por-run**: `client/squadx_client/git/worktree.py` cria branch `squadx/{task_id}/{agent}`
  e isola cada agente — paralelismo de squad já é possível.
- **Arbiter loop-breaker + cost-cap**: `orchestrator/nodes.py` (~595–651) decide `approve/continue/escalate`,
  com teto de custo (`state.cost_budget_usd`) e `max_cycles`.
- **Severidade de review** `blocker/major/minor/nit` em `nodes.py` (~469–487) — vocabulário reaproveitável
  para mapear `importance` da Attention Budget.
- **Team learnings**: verdict + blockers gravados no BrainSentry para o próximo run.

## Lacunas com valor de equipe (o que vamos implementar) — A–D

- **A — Attention Budget** (#2): classificar evento/log por `visibility` (human/audit/debug) + `importance`,
  no `ExecutionLog` (backend) e na emissão do client; o dashboard ganha **modo silencioso**. Resolve o
  ruído de N agentes de uma squad na thread humana. *Alto valor.*
- **B — Run Admission + dedup + follow-up** (#1): seam de admissão no `ExecutionService` — `idempotency_key`,
  `drop_duplicate` (replay idempotente), `queue_follow_up` (quando já há run ativo na mesma task) e
  `needs_human_decision`. Hoje só há o check de `RUNNING`. Coordena gatilhos simultâneos de dois membros.
  *Alto valor.* Reusa a ideia de `dedup_key` já definida em RFC-0003 §4.
- **C — Context Packet tipado** (#4): formalizar `_build_context_string` num packet auditável (intent/facts/
  sources/exclusions/budget), passado a agentes nativos e External CLI. O `budget` liga-se ao cost-cap (entrada
  vs. freio). *Médio valor.*
- **D — Hardening do sandbox** (#9, #10): allowlist/scrub de env sensível + detecção de prompt-injection no
  caminho External CLI, e limpeza de `.claude/.codex/.omx` antes do commit. *Médio valor; segurança.*

## Direção futura (registrada, fora do escopo atual)

- **Proposal / Approval / Apply** (#5): cada repair vira `SuggestedChangesSnapshot` imutável e aprovável,
  com supersessão por domínio e `ApplyPlan` (preflight → outcome por intent). É a forma "certa" do que o
  arbiter+cost-cap fazem hoje na marra.
- **Agente-a-agente ≠ chat** (#7): UI de coordenação como **grafo de tarefas + artefatos + pontos de decisão**,
  não log de chat. Mais importante para "squads de agentes" não virarem ruído.
- **Router de executor** (#8): seleção por tipo de task/custo/latência/performance histórica — a
  neutralidade de modelo operacional, evoluindo o runtime adapter `EXTERNAL_CLI` atual.
- **Result artifact first** (#11): resultado como artefato decision-grade (taxonomia), não transcript.

## Princípio-guia herdado do OpenTag

> *Dor → narrativa, comunalidade → protocolo, diferença de plataforma → adapter, método organizacional →
> recipe, regra de governança → policy.* Em uma linha: **Invocation. Governance. Compression.**
