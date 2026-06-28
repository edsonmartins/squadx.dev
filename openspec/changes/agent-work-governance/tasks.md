# Tasks — agent-work-governance

Checklist de implementação. Cada task referencia o **requisito de origem** (`capability:Rn`) e o
**ADR/RFC** pertinente. Stack: camadas + JPA + MapStruct + Flyway (ADR-0006). Spec em PT,
identificadores em EN.

> Legenda: `ab`=attention-budget, `ra`=run-admission, `cp`=context-packet, `sh`=sandbox-hardening.

## 1. attention-budget

- [x] 1.1 Migração `V33__add_execution_log_visibility.sql`: colunas `visibility` (default `'human'`)
  e `importance` (default `'normal'`) em `execution_logs` — ab:R1 · ADR-0007, RFC-0005 §1
- [x] 1.2 `model/ExecutionLog.java` (+`visibility`/`importance` como String, drift-safe) — ab:R1 · RFC-0005 §1
- [x] 1.3 `RunEventMetadata` helper (default por tipo de evento + nível) no backend — ab:R2 · RFC-0005 §1.1
- [x] 1.4 `WebSocketEventService.sendExecutionLog` propaga visibility/importance; `addLog` resolve os
  campos com fallback default quando ausentes — ab:R1, ab:R3 · RFC-0005 §1.2
- [x] 1.5 `dto/execution/ExecutionResponse` (LogEntry): `visibility`/`importance` — ab:R1
- [x] 1.6 Client: `messaging/run_event.py` `default_run_event_metadata(...)` + `for_severity`;
  `daemon.send_execution_log` anexa visibility/importance — ab:R2 · RFC-0005 §1.1
- [x] 1.7 Frontend: componente `execution-logs.tsx` com toggle Silencioso/Auditoria (default só `human`,
  checagem `=== "human"`), renderizado no `task-detail-sheet` — ab:R3 · ADR-0007

## 2. run-admission

- [x] 2.1 Migração `V34__add_run_admission.sql`: `executions.idempotency_key` +
  `unique(task_id, idempotency_key)` (task ⊂ org); tabela `follow_up_requests` — ra:R1, ra:R3 · RFC-0005 §3
- [x] 2.2 Enums `RunAdmissionAction`/`RunAdmissionReasonCode`/`FollowUpStatus`; `model/FollowUpRequest`;
  `model/Execution` (+`idempotencyKey`) — ra:R1, ra:R3 · RFC-0005 §2
- [x] 2.3 `repository`: `FollowUpRequestRepository`; `ExecutionRepository.findByTaskIdAndIdempotencyKey`
  + `findByTaskIdAndStatusIn` — ra:R1
- [x] 2.4 `service/RunAdmissionService.admit(task, request, currentUser)` per algoritmo RFC-0005 §2.2;
  `ExecutionService` grava `admission.decided`/`follow_up_request.queued` (`visibility=audit`) — ra:R1, ra:R2 · RFC-0005 §2
- [x] 2.5 `ExecutionService.startExecution` delega ao `RunAdmissionService` (substitui o check de
  `RUNNING`); embute `admission` na resposta — ra:R2 · RFC-0005 §2.2
- [x] 2.6 Promoção de follow-up em `handleTaskCompleted`/`handleTaskFailure` (FIFO por `created_at`);
  endpoint `GET /executions/task/{id}/follow-ups` (`PageResponse`) — ra:R3 · RFC-0005 §2.3
- [x] 2.7 Frontend: `task-detail-sheet` envia `idempotency_key` (UUID por clique) e exibe o desfecho
  da admissão (drop_duplicate/queue_follow_up) via toast. (Nota: o daemon não inicia execuções —
  o produtor da chave é o frontend/autopilot, não `_send_task_*`.) — ra:R1 · RFC-0005 §2.1

> **Nota de autoria/access:** R4 (actor-not-allowed-for-write) é hoje realizado por
> `validateUserAccess` em `startExecution` (anterior à admissão): um não-membro recebe 403 sem criar
> execução. A allowlist fina por escopo fica como evolução (RFC-0005 §4).

## 3. context-packet

- [x] 3.1 `orchestrator/context_packet.py`: dataclass `ContextPacket` (+`ContextFact`/`ContextSource`)
  e `build_context_packet` — cp:R1 · RFC-0005, ADR-0007
- [x] 3.2 `orchestrator/nodes.py` `execute_subtask`: monta o packet (uma vez por subtask, estável) a
  partir de `main_task`/`reuse_map`/`acceptance_criteria`/`completed_subtasks` (+`exclusions`) — cp:R1, cp:R2
- [x] 3.3 `agents/base.py` `_build_context_string` renderiza do packet quando presente (fallback à
  concatenação) — cp:R2
- [x] 3.4 `agents/external_cli_agent.py` `_build_prompt` prefixa o bloco "SquadX context packet" — cp:R2
- [x] 3.5 `budget_tokens` exposto no packet (lê `context["budget_tokens"]`); alinhamento conceitual ao
  `cost_budget_usd` — cp:R3 · (cost-cap)

## 4. sandbox-hardening

- [x] 4.1 `agents/security.py`: `DEFAULT_SAFE_ENV` + `_SENSITIVE_ENV_PATTERNS` + `scrub_env`; aplicado
  defensivamente em `daemon._run_external_cli_task` — sh:R1 · ADR-0007
- [x] 4.2 `agents/security.py`: `assess_prompt(text)` (override/exfiltração/arquivo sensível) + modo
  `cli_security_mode` (`enforce|audit|off`) em `config.py` — sh:R2
- [x] 4.3 Integrar assess em `external_cli._assess_prompt_security` (antes de rodar o CLI) — sh:R1, sh:R2
- [x] 4.4 Cleanup de `.claude/.codex/.omx`: `filter_internal_artifacts` no commit gate (`nodes.py`) e no
  `external_cli._collect_changed_files` — sh:R3 · (worktree existente)

## 5. Transversais

- [x] 5.1 Multi-tenancy: `startExecution` chama `validateUserAccess` antes da admissão — ra:R4 · ADR-0006
- [x] 5.2 Compat/drift: defaults aplicados quando visibility/importance ausentes (helper + teste
  `addLog`/`RunEventMetadata`) — ab:R3 · RFC-0005 §1.2
- [x] 5.3 Testes: backend `ExecutionServiceTest`/`RunAdmissionServiceTest`/`WebSocketEventServiceTest` ✓;
  frontend `execution-logs` ✓; client `test_run_event`/`test_context_packet`/`test_security` ✓ — todas · ADR-0005
- [ ] 5.4 Materializar: promover deltas para `openspec/specs/` ao aceitar/arquivar — todas

## Estado

As quatro capabilities (A/B/C/D) implementadas e verdes (2026-06-27):
- **A attention-budget** + **B run-admission**: backend 34 testes, frontend 156, `test-compile` OK.
- **C context-packet** + **D sandbox-hardening**: client suite completa **538 passed, 2 skipped**.
- Pendente apenas a materialização (5.4) na fase de aceitar/arquivar.
