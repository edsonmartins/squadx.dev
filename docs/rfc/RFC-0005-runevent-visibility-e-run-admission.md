# RFC-0005 — RunEvent (visibility/importance) e Run Admission

> Realiza ADR-0007. Define (1) os metadados de visibilidade/importância de eventos de execução e seus
> defaults por tipo, e (2) a decisão de admissão de run com idempotência e follow-up. Referência de
> implementação: `opentag/packages/core` (schema) e `opentag/packages/dispatcher/src/admission.ts`.

## 1. RunEvent: visibility e importance

Cada evento/log de uma `Execution` carrega dois metadados, além de `level`/`message`/`metadata` já
existentes em `ExecutionLog`:

```jsonc
RunEventVisibility = "human" | "audit" | "debug"
RunEventImportance = "low" | "normal" | "high" | "blocking"
```

- **visibility** decide o canal: `human` entra na thread do dashboard por padrão; `audit` e `debug`
  ficam disponíveis sob demanda (modo auditoria), não no fluxo silencioso.
- **importance** ranqueia dentro do canal humano (ex.: `blocking` destaca; `low` agrupa).

### 1.1 Defaults por tipo de evento

Um helper único calcula o default (client: `default_run_event_metadata(event_type)`; backend: aplica
o mesmo mapa ao persistir, com fallback seguro quando o client não envia). Mapa-base (espelha
`opentag/docs/thread-runtime-design.md` Delta 7):

| event_type (exemplos)                | visibility | importance |
|--------------------------------------|------------|------------|
| `run.created`, `admission.decided`   | audit      | normal     |
| `subtask.started`, `tool.log`        | debug      | low        |
| `agent.prompt`, `context_packet.generated` | audit | low      |
| `review.finding` (blocker)           | human      | blocking   |
| `review.finding` (major/minor/nit)   | audit      | normal     |
| `run.escalated`, `run.blocked`       | human      | blocking   |
| `run.completed`, `run.failed`        | human      | high       |
| `cost.budget_exceeded`               | human      | blocking   |

Mapeamento da severidade de review existente (`blocker/major/minor/nit`) → `importance`:
`blocker→blocking`, `major→high`, `minor→normal`, `nit→low`; só `blocker` é `visibility=human`.

### 1.2 Compatibilidade (drift)

Clients antigos não enviam os campos. O backend aplica o default por tipo; se o tipo for desconhecido,
default seguro `visibility="human", importance="normal"` (não esconder por engano). O frontend usa
checagem explícita `visibility === "human"` e `switch` com `default` (convenção de drift do repo).

## 2. Run Admission

Antes de criar a `Execution`, o `RunAdmissionService` decide:

```jsonc
RunAdmissionAction =
  | "start"               // cria run normal
  | "drop_duplicate"      // mesmo evento-fonte já gerou run → replay idempotente
  | "queue_follow_up"     // já há run ativo na mesma task → enfileira follow-up durável
  | "needs_human_decision"// não dá para agir com segurança

RunAdmissionReasonCode =
  | "new_event"
  | "duplicate_source_event"
  | "active_run_same_task"
  | "actor_not_allowed_for_write"
  | "policy_rejected"

RunAdmissionDecision = {
  action: RunAdmissionAction,
  reason: string,
  reason_code: RunAdmissionReasonCode,
  decided_at: timestamp,
  active_execution_id?: string,   // presente em queue_follow_up
  idempotency_key?: string
}
```

### 2.1 Idempotência

`idempotency_key` é fornecida pelo originador (ex.: id do gatilho/evento) ou derivada como
`hash(source, source_ref, task_id)` — coerente com `dedup_key` de RFC-0003 §4. Uma `Execution`
guarda sua `idempotency_key` com índice **único por (organization_id, task_id, idempotency_key)**.
Replay com a mesma chave → `drop_duplicate`, retornando a execução existente (não cria nova).

### 2.2 Algoritmo (determinístico)

```
admit(request, currentUser):
  validateUserAccess(orgId, currentUser)                 # multi-tenancy (igual aos demais serviços)
  if existsExecutionByIdempotencyKey(org, task, key):
      return drop_duplicate(active_execution_id = existing.id)
  if isWriteRequest(request) and not actorAllowedForWrite(currentUser, task):
      return needs_human_decision(actor_not_allowed_for_write)
  if existsActiveExecution(task):                        # status PENDING|RUNNING
      followUp = createFollowUpRequest(task, request, active.id)
      appendRunEvent(active.id, "follow_up_request.queued", visibility=audit)
      return queue_follow_up(active_execution_id = active.id)
  return start(new_event)
```

`start` segue o fluxo atual de `ExecutionService.startExecution` (resolve agente/squad, cria
`Execution`, dispara via STOMP). Toda decisão grava `admission.decided` (RFC-0005 §1, `visibility=audit`).

### 2.3 Follow-up: promoção

Quando a execução ativa termina (`handleTaskCompleted`), o follow-up pendente mais antigo da tarefa
é **promovido** a nova execução (cria run com lineage para o follow-up). Status do follow-up:
`pending → promoted | cancelled`. Promoção automática é opcional por policy (default: manual via
endpoint de leitura + ação).

## 3. Persistência (stack do repo, ADR-0006/0007)

- `execution_logs`: + colunas `visibility`, `importance` (V33), com defaults.
- `executions`: + coluna `idempotency_key`; índice `unique(organization_id, task_id, idempotency_key)` (V34).
- `follow_up_requests` (V34): `id`, `task_id`, `organization_id`, `source_payload` (JSONB),
  `decision` (JSONB), `active_execution_id`, `status`, `created_at`. Índice por `(task_id, status)`.
- DTO JSON snake_case via `@JsonProperty` (`visibility`, `importance`, `idempotency_key`,
  `active_execution_id`, `reason_code`).

## 4. Itens em aberto

- Allowlist de actor para escrita por escopo (org/squad/projeto) — começa como `validateUserAccess`;
  granularidade fina (binding) fica para depois.
- Priorização entre múltiplos follow-ups na mesma tarefa (default: FIFO por `created_at`).
- Se `visibility` deve, no futuro, dirigir diretamente o roteamento de callback (hoje só filtra a
  apresentação no dashboard) — alinhado ao "Projection Policy" do OpenTag, mantido fora de escopo.
