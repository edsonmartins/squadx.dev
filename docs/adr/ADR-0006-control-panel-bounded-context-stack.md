# ADR-0006 — Control Panel como bounded context na stack em camadas existente

## Status
Aceito — 2026-06-09.  
**Reclassificado 2026-08-26 (T-0011-3 do ADR-0011):** spec vigente do Control Panel, em implementação — backend em `backend/src/main/java/dev/squadx/controlpanel/` e telas em `frontend/src/app/(dashboard)/{changes,validations,harnesses}`. Não confundir com código descontinuado.


## Contexto

O Control Panel introduz um domínio novo (Change, Requirement, Scenario, SpecVersion, uma Task
spec-driven, Harness, Event de spec) **sobre** um SquadX.dev que já existe e já tem `Project`,
`Task` (status `TODO/IN_PROGRESS/IN_REVIEW/BLOCKED/DONE/CANCELLED`), `Agent`/`Squad`, `Execution`
e `Autopilot`. Há sobreposição de nomes e tensão de modelagem.

O prompt de especificação sugeriu **arquitetura hexagonal/DDD + QueryDSL**, mas o repositório
real é **em camadas** (controller/service/repository), Spring Data JPA + **MapStruct** + Flyway,
sem QueryDSL, sem prefixo de aplicação nas tabelas. Pela precedência **repositório > prompt**,
o repo vence.

## Decisão

1. **Novo bounded context "spec" (Control Panel)**, com entidades próprias, em vez de estender a
   `Task` de execução existente. Motivos: a Task do Control Panel é **spec-driven**, tem uma
   **máquina de estados diferente** (6 estados, `concluida` só pelo Pass 5) e rastreabilidade a
   requisito/cenário — semântica distinta da Task de execução do runtime.
2. **Reúso explícito**, não reescrita:
   - `Project` é reusado (tudo é escopado por projeto).
   - `Agent` é reusado como **assignee do tipo agente / Harness** (a pista do agente).
   - O **runtime de execução existente** (sandboxes/daemon) é a "pista do agente" que reporta via
     MCP; o Control Panel **observa**.
3. **Seguir a stack do repo**: camadas (controller/service/repository/dto/model), Spring Data JPA
   + MapStruct + Flyway, multi-tenancy por organização, auditoria das transições. **Sem**
   hexagonal/ports-adapters e **sem** QueryDSL (desvio consciente do prompt, registrado aqui).
4. **Nomes de tabela do contexto spec** (sem prefixo de aplicação, mas distintos para evitar
   colisão com as tabelas existentes): `changes`, `requirements`, `scenarios`, `spec_versions`,
   `spec_tasks`, `spec_events`, `harnesses`. A `Task` de execução existente permanece em `tasks`;
   a Task do Control Panel vive em `spec_tasks`.
5. **Pacote**: `dev.squadx.controlpanel.*` (ou subpacotes por capability) dentro do mesmo backend
   Spring, sem novo módulo Maven.

## Alternativas consideradas

1. **Estender a `Task` existente.** Menos entidades, mas mistura duas máquinas de estado e dois
   ciclos de vida num só modelo; acopla o runtime ao Control Panel. Rejeitada.
2. **Hexagonal/DDD + QueryDSL só no novo contexto** (como o prompt sugeria). Mais "limpo" na
   teoria, mas cria dois estilos arquiteturais no mesmo backend, aumentando o atrito de
   manutenção. Rejeitada por precedência do repo.
3. **Novo bounded context na stack em camadas + reúso (escolhido).**

## Consequências

- **Positivas:** separação clara de ciclos de vida; reúso de Project/Agent/runtime; coerência com
  o resto do SquadX.dev; menor atrito de manutenção.
- **Custos:** dois conceitos de "Task" coexistem (execução vs spec) — exige clareza de nomes
  (`tasks` vs `spec_tasks`) e cuidado em integrações; o mapeamento Agent↔Harness precisa ser
  explícito (capability `harness-connectors`).
- **Relacionado:** todos os demais ADRs; capability `control-panel-work-model`.
