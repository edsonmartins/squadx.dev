# Spec — run-admission

Verdade atual. Decisão de admissão explícita antes de criar uma `Execution`: idempotência (replay),
follow-up durável quando já há run ativo, e necessidade de decisão humana. Realiza ADR-0007,
RFC-0005 §2 (reusa `dedup_key` de RFC-0003 §4). Materializado a partir de `changes/agent-work-governance`.

## Requirements

### Requirement: R1 — Idempotência por chave de admissão
O sistema SHALL aceitar uma `idempotency_key` na criação de execução e SHALL garantir unicidade por
`(task_id, idempotency_key)` (a task já é escopada a uma organização). Uma segunda requisição com a
mesma chave SHALL resultar em `drop_duplicate`, retornando a execução já existente, sem criar nova.

#### Scenario: Replay do mesmo gatilho
- **WHEN** dois eventos com a mesma `idempotency_key` chegam para a mesma tarefa
- **THEN** apenas uma `Execution` é criada
- **AND** o segundo recebe a decisão `drop_duplicate` apontando a execução existente

### Requirement: R2 — Admissão substitui o check de RUNNING
O sistema SHALL rotear a criação de execução por um seam de admissão que retorna uma
`RunAdmissionDecision` (`start | drop_duplicate | queue_follow_up | needs_human_decision`), e SHALL
registrar a decisão como evento `admission.decided` com `visibility = "audit"`.

#### Scenario: Caminho normal
- **WHEN** chega um gatilho novo para uma tarefa sem execução ativa nem duplicata
- **THEN** a decisão é `start` e a execução é criada pelo fluxo atual
- **AND** um evento `admission.decided` é gravado na trilha

### Requirement: R3 — Follow-up durável quando há run ativo
O sistema SHALL, quando já existe uma `Execution` ativa (`PENDING` ou `RUNNING`) para a tarefa,
criar um `FollowUpRequest` durável (`status = pending`) em vez de iniciar trabalho concorrente, e
SHALL promovê-lo (FIFO por `created_at`) a nova execução quando a ativa terminar.

#### Scenario: Segundo gatilho com run ativo
- **WHEN** um segundo gatilho chega enquanto há execução ativa na tarefa
- **THEN** a decisão é `queue_follow_up` e um `FollowUpRequest` pendente é criado
- **AND** ao concluir a execução ativa, o follow-up mais antigo é promovido a nova execução

### Requirement: R4 — Autoridade para escrita
O sistema SHALL validar o acesso do solicitante via `validateUserAccess(organizationId, userId)`
antes da admissão, de modo que um solicitante sem acesso à organização não inicie execução.

#### Scenario: Actor sem permissão de escrita
- **WHEN** um usuário sem acesso à organização tenta iniciar uma execução
- **THEN** o acesso é negado (403)
- **AND** nenhuma `Execution` é criada
