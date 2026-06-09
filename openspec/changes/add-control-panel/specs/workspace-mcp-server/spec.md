# Spec (delta) — workspace-mcp-server

Contrato MCP harness-agnóstico do workspace. Realiza ADR-0003, RFC-0001.

## ADDED Requirements

### Requirement: R1 — Briefing via `get_change`/`get_tasks`
O sistema SHALL expor as tools `get_change` e `get_tasks` que devolvem a mudança (proposta, fase,
requisitos com cenários e tarefas) e a lista de tarefas com `requirementRef` e status.

#### Scenario: Abrir sessão e obter briefing
- **WHEN** um agente chama `get_change` com um `change_id` no escopo da sessão
- **THEN** o sistema retorna proposta, fase, requisitos (com cenários WHEN/THEN) e tarefas

#### Scenario: Listar tarefas filtradas por responsável
- **WHEN** um agente chama `get_tasks` com um `assignee`
- **THEN** o sistema retorna apenas as tarefas daquele responsável

### Requirement: R2 — Reporte de status restrito
O sistema SHALL aceitar, via `update_task_status`, apenas os status `em_curso` e `implementado`,
e SHALL rejeitar qualquer tentativa de definir `concluida`, `ajustes`, `em_validacao` ou
`bloqueada` por essa tool.

#### Scenario: Reportar em_curso
- **WHEN** um agente chama `update_task_status(task_id, "em_curso")`
- **THEN** o sistema registra um evento e retorna o status projetado da tarefa

#### Scenario: Rejeitar status proibido
- **WHEN** um agente chama `update_task_status(task_id, "concluida")`
- **THEN** o sistema rejeita a chamada com erro de validação
- **AND** não altera o estado da tarefa

### Requirement: R3 — Reporte de bloqueio com motivo
O sistema SHALL expor `report_blocker(task_id, reason)` que marca a tarefa como `bloqueada`,
exigindo um motivo não-vazio.

#### Scenario: Bloqueio com motivo
- **WHEN** um agente chama `report_blocker` com um motivo não-vazio
- **THEN** a tarefa transita para `bloqueada` e o motivo é registrado

#### Scenario: Bloqueio sem motivo é rejeitado
- **WHEN** um agente chama `report_blocker` com motivo vazio
- **THEN** o sistema rejeita a chamada com erro de validação

### Requirement: R4 — Materialização via MCP
O sistema SHALL expor `materialize_change(change_id)` que grava o change folder no repositório e
devolve `version` e `commit`.

#### Scenario: Materializar e obter commit
- **WHEN** `materialize_change` é chamada para uma mudança com versão corrente aprovada
- **THEN** o sistema retorna `{ ok, change_id, version, commit }`

### Requirement: R5 — Scaffold de testes via MCP
O sistema SHALL expor `scaffold_tests(change_id | requirement_id)` que gera, a partir dos
cenários, o esqueleto de testes (um método por cenário) e o mapa de cobertura.

#### Scenario: Gerar esqueleto a partir de um requisito
- **WHEN** `scaffold_tests` é chamada com um `requirement_id`
- **THEN** o sistema retorna `class_name`, `file`, `methods` (um por cenário) e `coverage`

### Requirement: R6 — Escopo e autenticação da sessão
O sistema SHALL vincular cada sessão MCP a um token escopado (organização, projeto, `change_id`,
responsável-agente) e SHALL rejeitar operações fora desse escopo.

#### Scenario: Operação fora do escopo é rejeitada
- **WHEN** um agente referencia um `task_id` que não pertence ao `change_id` da sessão
- **THEN** o sistema rejeita a chamada com erro de escopo

### Requirement: R7 — Contrato harness-agnóstico
O sistema SHALL oferecer o mesmo contrato de tools independentemente do harness (Claude Code,
Codex, Gemini CLI, Cursor).

#### Scenario: Mesmo contrato entre harnesses
- **WHEN** harnesses diferentes consomem o `workspace`
- **THEN** todos enxergam as mesmas tools com os mesmos schemas de entrada/saída
