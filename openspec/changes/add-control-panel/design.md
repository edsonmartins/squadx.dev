# Design — Control Panel (SquadX.dev Spec)

Decisões técnicas da mudança `add-control-panel`. Segue a stack do repositório (ADR-0006):
Spring Boot 3.4 / Java 21 em camadas, Spring Data JPA + MapStruct + Flyway, PostgreSQL,
multi-tenancy por organização, auditoria das transições. Spec em PT; identificadores em EN.

## 1. Fronteiras do bounded context

- **Novo contexto** `dev.squadx.controlpanel.*` no mesmo backend (sem novo módulo Maven).
- **Reúso** (ADR-0006): `Project` (escopo), `Agent` (assignee-agente / harness), runtime de
  execução existente (pista do agente que reporta via MCP), `IntegrationWebhookService`
  (ingestão de webhooks Git).
- A `Task` de execução existente (tabela `tasks`) **não** é tocada; a Task do Control Panel é
  uma entidade nova em `spec_tasks`.

## 2. Entidades → tabelas

| Entidade (EN) | Tabela | Notas |
|---|---|---|
| Change | `changes` | mudança/change folder; `phase`; FK `project_id` |
| Requirement | `requirements` | `type` (ADDED/MODIFIED/REMOVED); FK `change_id` |
| Scenario | `scenarios` | `when`, `then`, `covered`; FK `requirement_id` |
| SpecVersion | `spec_versions` | `version`, `current`, `summary`, `author`, `commit`; FK `change_id` |
| Task (Control Panel) | `spec_tasks` | `requirement_ref`, `status`, `assignee`, `pass5`, `blocker_reason`, `revise_reason` |
| SpecEvent | `spec_events` | append-only; `unique(dedup_key)`; projeção do estado (RFC-0003) |
| Harness | `harnesses` | `key`, `vendor`, `status`, `model`, `models[]` |

Sem prefixo de aplicação; nomes distintos para não colidir com tabelas existentes (`tasks`,
`projects`). Migrações Flyway na sequência do repo (atual: V32 → próximas Vnn).

## 3. Camadas

- `controller/` — REST do painel (dashboard, specs/workspace, tarefas, conectores, validação).
- `service/` — regras: máquina de estados (projeção), versionamento, materialização, Pass 5.
- `repository/` — Spring Data JPA; queries por organização/projeto.
- `dto/` + MapStruct — request/response (snake_case via `@JsonProperty`, como no repo).
- `event/` — ingestão e projeção (`SpecEvent`); reusa o barramento de eventos existente.
- `integration/` — adaptadores MCP server (`workspace`) e Pullwise (Pass 5).

## 4. Componentes novos

- **`workspace` MCP server** (RFC-0001) — em `integration/mcp/`; transporte stdio + HTTP/SSE;
  auth por token de sessão escopado (reusa JWT/RBAC). Tools: get_change, get_tasks,
  update_task_status, report_blocker, materialize_change, scaffold_tests.
- **Materializador** (RFC-0002) — render determinístico do change folder → commit; lock por change.
- **Projeção de estado** (RFC-0003) — `project(events) → status`; tabela `spec_events` +
  materialização em `spec_tasks`.
- **Pass 5 / Pullwise** (RFC-0004) — orquestra cobertura → testes → revisão semântica; desfechos
  emitem eventos.
- **scaffold de testes** (ADR-0005) — gera métodos rastreáveis (um por cenário) na stack do repo.

## 5. Status (rótulo PT / identificador EN)

`a_fazer`/A fazer, `em_curso`/Em execução, `em_validacao`/Em validação, `concluida`/Concluída,
`bloqueada`/Bloqueada, `ajustes`/Ajustes necessários. `implementado` é **evento**, não estado
(ADR-0004). `concluida`/`ajustes` só via Pass 5.

## 6. UI (telas — reúso do frontend Next.js)

Dashboard (por projeto), Specs/Workspace (por mudança, com "onde estamos" e histórico de versões
+ commit), Tarefas (lista + kanban; filtro Humanos/IA), Detalhe da tarefa (briefing + cenários +
histórico + card do agente + portão Pass 5 + commits), Conectores (harness + seletor de modelo),
Validação · Pass 5 (fila + cobertura cenário→teste). UI é **projeção** (ADR-0002).

## 7. Rastreabilidade e ADR/RFC

- work-model → ADR-0004, ADR-0006, RFC-0003.
- versioning-materialization → ADR-0001, RFC-0002.
- workspace-mcp-server → ADR-0003, RFC-0001.
- execution-tracking → ADR-0002, RFC-0003.
- pass5-validation → ADR-0005, RFC-0004.
- harness-connectors → ADR-0003 (contrato), capability própria.

## 8. Riscos

- Coexistência de dois conceitos de "Task" (execução vs spec) — mitigado por nomes (`tasks` vs
  `spec_tasks`) e por o painel apenas **observar** a execução.
- Materialização concorrente / divergência do repo — lock + detecção de conflito (RFC-0002).
- Mapeamento cenário→teste por convenção de nome — exige disciplina; o scaffold reduz o risco.
