# Tasks — add-control-panel

Checklist de implementação. Cada task referencia o **requisito de origem** (capability:Rn) e o
**ADR/RFC** pertinente. Stack: camadas + JPA + MapStruct + Flyway (ADR-0006). Spec em PT,
identificadores em EN.

> Legenda: `wm`=control-panel-work-model, `vm`=spec-versioning-materialization,
> `mcp`=workspace-mcp-server, `et`=execution-tracking, `p5`=pass5-validation, `hc`=harness-connectors.

## 1. Modelo de trabalho (control-panel-work-model)

- [x] 1.1 Migração Flyway: `changes`, `requirements`, `scenarios`, `spec_tasks` (sem prefixo de
  app; FKs por projeto/mudança/requisito). — wm:R1, wm:R2, wm:R3 · ADR-0006
- [x] 1.2 Entidades JPA + DTOs (MapStruct) para Change/Requirement/Scenario/Task. — wm:R1–R3 · ADR-0006
- [x] 1.3 Validação "requisito exige ≥1 cenário" no service de criação/edição. — wm:R2
- [x] 1.4 Rastreabilidade bidirecional requisito↔tarefa (`requirementRef`/`taskRefs`). — wm:R3
- [x] 1.5 Máquina de estados (6 estados) + tabela de transições válidas; rejeitar inválidas. — wm:R4 · ADR-0004, RFC-0003
- [x] 1.6 Garantir que `concluida`/`ajustes` só venham do Pass 5 (bloquear nos demais caminhos). — wm:R5 · ADR-0004
- [x] 1.7 Responsável humano|agente (modelo de Assignee; agente resolve harness+modelo). — wm:R6 · ADR-0003
- [x] 1.8 Projeção "onde estamos" (dashboard + barra por mudança) a partir de eventos. — wm:R7 · ADR-0002, RFC-0003
- [x] 1.9 Controllers REST + telas (dashboard, workspace, tarefas lista/kanban, detalhe). — wm:R1–R7

## 2. Versionamento + materialização (spec-versioning-materialization)

- [x] 2.1 Migração Flyway: `spec_versions` (version/current/summary/author/commit; FK change). — vm:R1 · ADR-0001
- [x] 2.2 Serviço de versionamento semântico (nova versão ao aprovar; `current` único). — vm:R1 · ADR-0001
- [x] 2.3 Render determinístico do change folder (ordenação estável, sem voláteis). — vm:R3 · RFC-0002
- [x] 2.4 Materializador: branch da mudança + commit; registrar sha em `SpecVersion`. — vm:R2 · RFC-0002
- [x] 2.5 Idempotência: diff vazio → no-op retornando o commit existente. — vm:R3 · RFC-0002
- [x] 2.6 Detecção de conflito de materialização (não sobrescrever cegamente). — vm:R5 · RFC-0002
- [x] 2.7 Garantir spec materializada como markdown OpenSpec legível (sem lock-in). — vm:R4 · ADR-0001
- [x] 2.8 Histórico de versões na UI (com commit). — vm:R1, vm:R2

## 3. MCP server `workspace` (workspace-mcp-server)

- [x] 3.1 Esqueleto do MCP server (`integration/mcp/`): transporte stdio + HTTP/SSE; `tools/list`. — mcp:R7 · RFC-0001, ADR-0003
- [x] 3.2 Auth por token de sessão escopado (org/projeto/change/assignee); rejeitar fora de escopo. — mcp:R6 · RFC-0001
- [x] 3.3 `get_change` / `get_tasks` (briefing). — mcp:R1 · RFC-0001
- [x] 3.4 `update_task_status` (apenas `em_curso`/`implementado`; rejeitar proibidos) → emite evento. — mcp:R2 · RFC-0001, RFC-0003
- [x] 3.5 `report_blocker` (motivo obrigatório) → `bloqueada`. — mcp:R3 · RFC-0001
- [x] 3.6 `materialize_change` (delega ao materializador; retorna version+commit). — mcp:R4 · RFC-0001, RFC-0002
- [x] 3.7 `scaffold_tests` (esqueleto por cenário + cobertura). — mcp:R5 · RFC-0001, ADR-0005
- [x] 3.8 `contractVersion` (semver) anunciado nas capabilities. — mcp:R7 · RFC-0001
- [x] 3.9 `search_code` delegando ao snapshot nativo `READY`, sem aceitar `snapshot_id` do agente. — mcp:R8 · code-intelligence
- [x] 3.10 `get_symbol_context` para símbolos básicos do snapshot nativo, com localização e evidência. — mcp:R8 · code-intelligence

## 4. Rastreio de execução (execution-tracking)

- [x] 4.1 Migração Flyway: `spec_events` (append-only; `unique(dedup_key)`; índice por tarefa/ocorrência). — et:R4, et:R6 · RFC-0003
- [x] 4.2 Ingestão de webhooks Git → eventos (started/pr_opened/merge→Pass 5); reúso do `IntegrationWebhookService`. — et:R1 · RFC-0003
- [x] 4.3 Ingestão de eventos MCP → eventos de tarefa. — et:R2 · RFC-0003
- [x] 4.4 Projeção determinística `project(events)→status`; materializar em `spec_tasks`. — et:R3 · ADR-0002, RFC-0003
- [x] 4.5 Idempotência por `dedup_key`. — et:R4 · RFC-0003
- [x] 4.6 Ordenação por ocorrência (tolerância a fora de ordem). — et:R5 · RFC-0003
- [x] 4.7 Trilha de auditoria (endpoint + timeline no detalhe da tarefa). — et:R6 · ADR-0002

## 5. Validação Pass 5 (pass5-validation)

- [x] 5.1 Mapa de cobertura cenário↔teste por convenção de nome (parser dos métodos). — p5:R1 · ADR-0005, RFC-0004
- [x] 5.2 Etapa de cobertura: cenário sem teste reprova. — p5:R1 · RFC-0004
- [x] 5.3 Execução dos testes mapeados; teste quebrado reprova. — p5:R2 · RFC-0004
- [x] 5.4 Integração Pullwise (adapter opcional e interface plugável) p/ conformidade comportamental. — p5:R3 · RFC-0004
- [x] 5.5 Desfechos: aprovado→`concluida`/`pass`; reprovado→`ajustes`/`fail`+crítica→reabre. — p5:R4 · ADR-0004, RFC-0003
- [x] 5.6 Disparo no merge + idempotência por (tarefa, sha). — p5:R5 · RFC-0004, RFC-0003
- [x] 5.7 Tela de validação (cobertura ✓/✕ + critérios + crítica). — p5:R6
- [x] 5.8 `scaffold_tests` na stack do repo (JUnit 5; método por cenário, nome rastreável). — p5:R1 · ADR-0005

## 6. Conectores de harness (harness-connectors)

- [x] 6.1 Migração Flyway: `harnesses` (key/name/vendor/status/model/models). — hc:R1 · ADR-0003
- [x] 6.2 CRUD + tela de conectores (status conectado/disponível). — hc:R1
- [x] 6.3 Seleção de modelo por harness; resolução no responsável-agente. — hc:R2 · ADR-0003
- [x] 6.4 Mapear `Agent` existente ↔ Harness/assignee (reúso). — hc:R1 · ADR-0006
- [x] 6.5 Adicionar harness por configuração, sem mudar o contrato MCP. — hc:R3 · ADR-0003

## 7. Transversais

- [x] 7.1 Multi-tenancy por organização + RBAC nas novas rotas (reúso do existente). — ADR-0006
- [x] 7.2 Auditoria LGPD das transições (via `spec_events`). — ADR-0002, et:R6
- [x] 7.3 Testes: cada cenário WHEN/THEN das specs vira teste rastreável (dogfooding do Pass 5). — ADR-0005
