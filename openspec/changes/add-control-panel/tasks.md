# Tasks — add-control-panel

Checklist de implementação. Cada task referencia o **requisito de origem** (capability:Rn) e o
**ADR/RFC** pertinente. Stack: camadas + JPA + MapStruct + Flyway (ADR-0006). Spec em PT,
identificadores em EN.

> Legenda: `wm`=control-panel-work-model, `vm`=spec-versioning-materialization,
> `mcp`=workspace-mcp-server, `et`=execution-tracking, `p5`=pass5-validation, `hc`=harness-connectors.

## 1. Modelo de trabalho (control-panel-work-model)

- [x] 1.1 Migração Flyway: `changes`, `requirements`, `scenarios`, `spec_tasks` (sem prefixo de
  app; FKs por projeto/mudança/requisito). — wm:R1, wm:R2, wm:R3 · ADR-0006 _(V33; ver nota de coordenação com a PR #1)_
- [x] 1.2 Entidades JPA + DTOs para Change/Requirement/Scenario/Task. — wm:R1–R3 · ADR-0006 _(mapeamento manual; não há MapStruct no repo)_
- [x] 1.3 Validação "requisito exige ≥1 cenário" no service de criação/edição. — wm:R2
- [x] 1.4 Rastreabilidade bidirecional requisito↔tarefa (`requirementRef`/`taskRefs`). — wm:R3
- [x] 1.5 Máquina de estados (6 estados) + tabela de transições válidas; rejeitar inválidas. — wm:R4 · ADR-0004, RFC-0003
- [x] 1.6 Garantir que `concluida`/`ajustes` só venham do Pass 5 (bloquear nos demais caminhos). — wm:R5 · ADR-0004
- [x] 1.7 Responsável humano|agente (modelo de Assignee). — wm:R6 · ADR-0003 _(resolução do modelo do harness fica em `harness-connectors`)_
- [x] 1.8 Projeção "onde estamos" (endpoint where-we-are: contagem por status/total/progresso). — wm:R7 · ADR-0002, RFC-0003 _(derivação a partir de eventos vem em `execution-tracking`)_
- [~] 1.9 Controllers REST (changes/requirements/spec-tasks + where-we-are) — feito; **telas (dashboard, workspace, kanban, detalhe) pendentes na fatia de frontend**. — wm:R1–R7

## 2. Versionamento + materialização (spec-versioning-materialization)

- [x] 2.1 Migração Flyway `V36`: `spec_versions` (version/current/summary/author/commit/content_hash; UNIQUE(change,version)). — vm:R1 · ADR-0001
- [x] 2.2 `SpecVersionService` (nova versão ao aprovar; `current` único; histórico). — vm:R1 · ADR-0001
- [x] 2.3 `ChangeFolderRenderer` determinístico (ordenação estável, sem voláteis; markdown OpenSpec). — vm:R3 · RFC-0002
- [x] 2.4 `DefaultSpecMaterializer` → `GitHubCommitGateway` (GitHub Git Data API: blobs→tree→commit→ref, branch `spec/<key>`); registra sha em `SpecVersion`. — vm:R2 · RFC-0002 _(no-op gracioso sem token/repo; provider GitHub)_
- [x] 2.5 Idempotência por content-hash: conteúdo igual + commit → no-op. — vm:R3 · RFC-0002
- [x] 2.6 Detecção de conflito de materialização: `update-ref` não-force; 422/409 → `CommitResult.conflict` → `unavailable`. — vm:R5 · RFC-0002
- [x] 2.7 Spec materializada como markdown OpenSpec legível (sem lock-in). — vm:R4 · ADR-0001
- [x] 2.8 Histórico de versões: endpoint `GET /changes/{id}/versions` + UI `VersionHistory`. — vm:R1, vm:R2

## 3. MCP server `workspace` (workspace-mcp-server)

- [~] 3.1 Superfície do contrato (`controlpanel/mcp/`): HTTP (`/api/v1/workspace/tools`) + `tools/list`. — mcp:R7 · RFC-0001, ADR-0003 _(bridge MCP stdio/SSE: adaptador externo posterior)_
- [x] 3.2 Auth por token de sessão escopado (user/org/projeto/change/assignee) + filtro; rejeita fora de escopo (`E_SCOPE`). — mcp:R6 · RFC-0001
- [x] 3.3 `get_change` / `get_tasks` (briefing; reúso de Change/Requirement/SpecTask services). — mcp:R1 · RFC-0001
- [x] 3.4 `update_task_status` (só `em_curso`/`implementado`; `em_curso`→transition, `implementado`→evento; rejeita proibidos). — mcp:R2 · RFC-0001, RFC-0003
- [x] 3.5 `report_blocker` (motivo obrigatório) → `bloqueada`. — mcp:R3 · RFC-0001
- [x] 3.6 `materialize_change` (delega à porta `SpecMaterializer`; Noop por ora). — mcp:R4 · RFC-0001, RFC-0002 _(impl real em `spec-versioning-materialization`)_
- [x] 3.7 `scaffold_tests` (método por cenário, nome rastreável `R<n>_<slug>`, + cobertura). — mcp:R5 · RFC-0001, ADR-0005
- [x] 3.8 `contract_version` (semver) anunciado em `tools/list` e na emissão de sessão. — mcp:R7 · RFC-0001

## 4. Rastreio de execução (execution-tracking)

- [x] 4.1 Migração Flyway `V34`: `spec_events` (append-only via trigger no-update; `unique(dedup_key)`; índice por tarefa/ocorrência). — et:R4, et:R6 · RFC-0003
- [x] 4.2 Ingestão de webhooks Git → eventos (push→STARTED, PR opened→PR_OPENED, merge→`SpecTaskMergedEvent` gatilho do Pass 5); HMAC SHA-256. — et:R1 · RFC-0003 _(consumo do gatilho em `pass5-validation`)_
- [x] 4.3 Ingestão de eventos MCP → eventos de tarefa (caminho `SpecEventService.record(source=MCP)`, exercido via `transition`). — et:R2 · RFC-0003 _(o MCP server em si: `workspace-mcp-server`)_
- [x] 4.4 Projeção determinística `SpecTaskProjector.project(events)→status`; materializada em `spec_tasks`. — et:R3 · ADR-0002, RFC-0003
- [x] 4.5 Idempotência por `dedup_key` (SHA-256 de source|ref|type|task). — et:R4 · RFC-0003
- [x] 4.6 Ordenação por ocorrência (`findBySpecTaskIdOrderByOccurredAtAscIdAsc`; reprojeção reprocessável). — et:R5 · RFC-0003
- [x] 4.7 Trilha de auditoria (eventos append-only). — et:R6 · ADR-0002 _(histórico na UI: fatia de frontend)_

## 5. Validação Pass 5 (pass5-validation)

- [x] 5.1 Mapa de cobertura cenário↔teste (flag `Scenario.covered` = "coberto por teste que passa"; `CoverageService` writer). — p5:R1 · ADR-0005, RFC-0004
- [x] 5.2 Etapa de cobertura: cenário sem teste reprova. — p5:R1 · RFC-0004
- [x] 5.3 Teste mapeado falhando reprova (colapsado em `covered=false` nesta fatia). — p5:R2 · RFC-0004 _(execução real de testes: integração externa)_
- [x] 5.4 Conformidade comportamental via `ConformanceReviewer` (interface plugável; default Noop). — p5:R3 · RFC-0004 _(cliente Pullwise real: posterior)_
- [x] 5.5 Desfechos: aprovado→`concluida`/`pass`; reprovado→`ajustes`/`fail`+crítica (via `applyPass5Outcome`/evento PASS5). — p5:R4 · ADR-0004, RFC-0003
- [x] 5.6 Disparo no merge (`Pass5TriggerListener`) + idempotência por `(tarefa, pr_sha)` (`pass5_runs`). — p5:R5 · RFC-0004, RFC-0003
- [x] 5.7 Endpoint de status/cobertura (`GET /spec-tasks/{id}/pass5`, `POST .../run`). — p5:R6 _(tela em si: fatia de frontend)_
- [~] 5.8 `scaffold_tests` (método por cenário, nome rastreável) — pertence ao `workspace-mcp-server`; aqui só o writer de cobertura. — p5:R1 · ADR-0005

## 6. Conectores de harness (harness-connectors)

- [x] 6.1 Migração Flyway `V37`: `harnesses` + `harness_models` (key/name/vendor/status/model/models; UNIQUE(org,key)). — hc:R1 · ADR-0003
- [x] 6.2 CRUD (`HarnessService`/`HarnessController`: register/list/select-model; status AVAILABLE/CONNECTED). — hc:R1 _(tela: fatia de frontend)_
- [x] 6.3 Seleção de modelo por harness (rejeita fora de `models`); `resolveModelForAgent`. — hc:R2 · ADR-0003
- [x] 6.4 Mapear `Agent` existente ↔ Harness/assignee (FK `agent_id`). — hc:R1 · ADR-0006
- [x] 6.5 Adicionar harness por configuração (registry), sem mudar o contrato MCP. — hc:R3 · ADR-0003

## 7. Transversais

- [x] 7.1 Multi-tenancy por organização + RBAC nas novas rotas (`validateUserAccess` em todos os services + filtro de sessão MCP). — ADR-0006
- [x] 7.2 Auditoria LGPD das transições (trilha append-only `spec_events`). — ADR-0002, et:R6
- [x] 7.3 Testes: cenários WHEN/THEN viram testes rastreáveis (70 testes do control-panel; dogfooding por capability). — ADR-0005
