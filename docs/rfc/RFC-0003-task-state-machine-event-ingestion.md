# RFC-0003 — Máquina de estados das tarefas + ingestão de eventos

> Realiza ADR-0002 e ADR-0004. Define o modelo de evento, as fontes (webhook Git + MCP +
> Pass 5), a projeção determinística do estado e as garantias de idempotência/ordenação.

## 1. Modelo de evento

Eventos são append-only, por tarefa:
```jsonc
SpecEvent = {
  id: "uuid",
  task_id: "string",
  type: "TaskEventType",
  source: "git" | "mcp" | "pass5",
  source_ref: "string",      // ex.: sha do commit, id da chamada MCP, id do run do Pass 5
  payload: { ... },
  occurred_at: "timestamp",
  received_at: "timestamp",
  dedup_key: "string"        // idempotência (ver §4)
}

TaskEventType =
  | "started"            // dev/agente começou           (→ em_curso)
  | "implemented"        // afirmação de "terminei"        (evento; NÃO muda board sozinho)
  | "pr_opened"          // PR aberto                      (→ em_validacao)
  | "blocked"            // impedimento (com motivo)       (→ bloqueada)
  | "unblocked"          // impedimento resolvido          (→ estado ativo anterior)
  | "pass5_approved"     // Pass 5 aprovou                 (→ concluida)
  | "pass5_changes"      // Pass 5 reprovou (com crítica)  (→ ajustes → em_curso)
```

## 2. Fontes

- **Git (webhook):** `branch <id>` → `started`; `PR opened` (ref. ao `id`) → `pr_opened`;
  `merge` → dispara Pass 5 (RFC-0004). Reusa o `IntegrationWebhookService` existente.
- **MCP:** `update_task_status(em_curso)` → `started`; `update_task_status(implementado)` →
  `implemented`; `report_blocker` → `blocked` (RFC-0001).
- **Pass 5:** `pass5_approved` / `pass5_changes` (RFC-0004).

Vínculo tarefa↔commit/PR/branch: por **convenção de nome** (branch/commit/PR referenciam o `id`
da tarefa, ex.: `2.1`); o ingestor extrai o `id` e associa.

## 3. Projeção do estado (determinística)

`project(events: SpecEvent[]) -> Status` aplica os eventos ordenados por `occurred_at` (desempate
por `received_at`, depois `id`):

```
status = a_fazer
blocked_reason = null
for e in ordered(events):
  switch e.type:
    started:        if status in {a_fazer, ajustes}: status = em_curso
    implemented:    /* não muda status; registra afirmação */
    pr_opened:      if status in {em_curso}: status = em_validacao
    blocked:        prev_active = status; status = bloqueada; blocked_reason = e.payload.reason
    unblocked:      status = prev_active or em_curso; blocked_reason = null
    pass5_approved: if status == em_validacao: status = concluida
    pass5_changes:  if status == em_validacao: status = ajustes; revise_reason = e.payload.critique
                    then status = em_curso  /* reabre */
return { status, blocked_reason, revise_reason }
```

Regras-chave (ADR-0004): `concluida`/`ajustes` só por `pass5_*`. `implemented` nunca move o board
sozinho. `pr_opened` é o que leva a `em_validacao`. Transições inválidas são **ignoradas** na
projeção (não quebram), mas registradas como anomalia para diagnóstico.

## 4. Idempotência e ordenação

- **Idempotência:** `dedup_key = hash(source, source_ref, type, task_id)`. Eventos com
  `dedup_key` repetido são descartados na ingestão (webhooks Git e retries MCP duplicam).
- **Ordenação:** por tarefa, por `occurred_at`. Webhooks fora de ordem são tolerados porque a
  projeção é uma função pura sobre o conjunto ordenado (reprocessável).
- **Reprocessamento:** a projeção pode ser recomputada do zero a partir dos eventos (reconstrói o
  estado e os dashboards — ADR-0002).

## 5. Persistência (stack do repo, ADR-0006)

- Tabela `spec_events` (append-only) com índice por `(task_id, occurred_at)` e `unique(dedup_key)`.
- Projeção materializada em `spec_tasks` (colunas `status`, `blocked_reason`, `revise_reason`,
  `pass5`) para leitura barata; recomputável a partir de `spec_events`.
- Toda transição é auditável (LGPD): o próprio `spec_events` é a trilha.

## 6. Itens em aberto

- Janela de tolerância para eventos atrasados antes de considerar a projeção "estável".
- Mapeamento `unblocked` quando há múltiplos bloqueios sobrepostos (default: limpa ao primeiro
  `unblocked`).
