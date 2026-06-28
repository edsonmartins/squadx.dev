# Design — Agent Work Governance

Decisões técnicas da mudança `agent-work-governance`. Segue a stack do repositório (ADR-0006):
backend em camadas (controller/service/repository/dto/model), Spring Data JPA + MapStruct + Flyway,
PostgreSQL, multi-tenancy por organização; client Python (LangGraph/daemon). Spec em PT;
identificadores em EN. Realiza ADR-0007; contrato em RFC-0005.

## 1. Fronteiras

Não há novo bounded context: estende-se o domínio de **execução existente** (`Execution`,
`ExecutionLog`) e o **client daemon**. Reúso central:

- `WebSocketEventService` + `@TransactionalEventListener` — barramento de eventos atual.
- `ExecutionService.startExecution` / `handleDaemonTaskUpdate` — pontos de entrada já existentes.
- `validateUserAccess(orgId, userId)` — multi-tenancy (igual aos demais serviços).
- `git/worktree.py`, `state.cost_budget_usd`, severidade `blocker/major/minor/nit` — reúso no client.

## 2. Entidades → tabelas

| Entidade (EN)        | Tabela                | Migração | Notas |
|----------------------|-----------------------|----------|-------|
| `ExecutionLog` (+2 col) | `execution_logs`   | V33      | + `visibility`, `importance` (defaults) |
| `Execution` (+1 col) | `executions`          | V34      | + `idempotency_key`; `unique(organization_id, task_id, idempotency_key)` |
| `FollowUpRequest`    | `follow_up_requests`  | V34      | `task_id`, `organization_id`, `source_payload` JSONB, `decision` JSONB, `active_execution_id`, `status` |

`organization_id` em `follow_up_requests` e no índice de `executions` resolve-se via
`task.project.organization` (sem desnormalizar além do necessário para o índice único).

## 3. Camadas (backend)

- `model/` — `ExecutionLog` (+campos), `Execution` (+campo), `FollowUpRequest` (novo, `extends BaseEntity`),
  `enums/RunAdmissionAction`, `enums/RunAdmissionReasonCode`, `enums/FollowUpStatus`,
  `enums/RunEventVisibility`, `enums/RunEventImportance`.
- `service/` — **`RunAdmissionService`** (novo; `admit(...)` per RFC-0005 §2.2);
  `ExecutionService.startExecution` delega a ele; `WebSocketEventService.sendExecutionLog` propaga
  visibility/importance; `RunEventMetadata` helper (default por tipo, RFC-0005 §1.1).
- `repository/` — `FollowUpRequestRepository`; `ExecutionRepository.existsByIdempotencyKey(...)` /
  `findByTaskIdAndIdempotencyKey(...)`.
- `dto/` — `ExecutionResponse.LogEntry` (+`visibility`/`importance`); `FollowUpResponse`;
  `RunAdmissionDecision` (response). Tudo snake_case via `@JsonProperty`.
- `controller/` — leitura de follow-ups pendentes por task (`PageResponse`).

## 4. Componentes novos (client)

- `orchestrator/context_packet.py` — dataclass `ContextPacket` (RFC-0005 ref. shape do OpenTag).
- `messaging/run_event.py` — `default_run_event_metadata(event_type)` (RFC-0005 §1.1) anexado nos
  logs emitidos para `/app/executions/{id}/logs`.
- `agents/security.py` — `DEFAULT_SAFE_ENV`, `SENSITIVE_ENV_PATTERNS`, `assess_prompt(text)`
  (espelha `opentag/packages/runner/src/security.ts`), modo de `config.py`.
- Cleanup de artefatos internos (`.claude/.codex/.omx`) antes do commit gate (`orchestrator/nodes.py`)
  e no `external_cli_agent._collect_changed_files` (filtra do set commitado).

## 5. Status (rótulo PT / identificador EN)

- `FollowUpStatus`: `pending` (pendente) → `promoted` (promovido) | `cancelled` (cancelado).
- `RunAdmissionAction`: `start` / `drop_duplicate` / `queue_follow_up` / `needs_human_decision`.
- `visibility`: `human` (humano) / `audit` (auditoria) / `debug`. `importance`: `low/normal/high/blocking`.

## 6. UI (telas)

- Logs de execução: toggle **Silencioso / Auditoria** (default só `visibility=human`); `importance`
  ordena/destaca (`blocking` em evidência). Filtro client-side sobre os dados da query existente.
- Painel da tarefa: lista de follow-ups pendentes (quando há run ativo), com ação de promoção.

## 7. Rastreabilidade ADR/RFC

- attention-budget → ADR-0007, ADR-0002, RFC-0005 §1.
- run-admission → ADR-0007, RFC-0005 §2 (reusa `dedup_key` de RFC-0003 §4).
- context-packet → ADR-0007, RFC-0005 (handoff).
- sandbox-hardening → ADR-0007.

## 8. Riscos

- **Drift de client** (não envia metadados) → backend aplica default por tipo; tipo desconhecido =
  `human/normal` (não esconder por engano).
- **Esconder demais** → default conservador; conclusão/blocker/escalação são sempre `human`.
- **Índice único de idempotência** → escopo `(org, task, key)` evita colisão entre tarefas/orgs.
- **Cleanup agressivo** → restringir a raízes internas conhecidas (`.claude/.codex/.omx`), nunca a
  arquivos do usuário.
