# RFC-0001 — Contrato do `workspace` MCP server

> Realiza ADR-0003. Define o contrato harness-agnóstico que Claude Code, Codex, Gemini CLI e
> Cursor consomem. Identificadores em inglês; rótulos de UI em PT.

## 1. Visão geral

O `workspace` é um **MCP server** exposto pelo backend do SquadX.dev. Ele dá ao agente:
- **briefing** (a mudança, requisitos, cenários, tarefas),
- **canal de status** (reportar progresso/bloqueio — vira evento; ADR-0002),
- **materialização** (gravar a versão da spec no repo; RFC-0002),
- **scaffold de testes** (derivar testes dos cenários; ADR-0005).

## 2. Transporte, autenticação, escopo

- **Transporte:** MCP sobre stdio (CLI local do harness) e/ou streamable HTTP/SSE para harnesses
  remotos. O servidor anuncia as tools via `tools/list`.
- **Auth:** cada sessão é vinculada a um **token de sessão** emitido pelo Control Panel ao abrir
  a sessão do agente (curto, escopado a um `change_id` e a um assignee-agente). O token resolve
  organização, projeto e permissões (reusa o JWT/RBAC existente).
- **Escopo:** todas as tools operam **dentro do `change_id`/projeto da sessão**; o servidor
  rejeita `task_id`/`requirement_id` fora desse escopo (`E_SCOPE`).

## 3. Tipos comuns

```jsonc
Status        = "a_fazer" | "em_curso" | "em_validacao" | "concluida" | "bloqueada" | "ajustes"
ReportStatus  = "em_curso" | "implementado"        // o que o agente PODE reportar (ADR-0004)
RequirementType = "ADDED" | "MODIFIED" | "REMOVED"

Scenario  = { name: string, when: string, then: string }
Requirement = { id: string, type: RequirementType, title: string, scenarios: Scenario[] }
TaskBrief = { id: string, title: string, requirementRef: string, status: Status }
Error     = { code: string, message: string, retriable: boolean }
```

Erros padronizados: `E_SCOPE`, `E_NOT_FOUND`, `E_INVALID_TRANSITION`, `E_VALIDATION`,
`E_CONFLICT` (materialização), `E_RATE_LIMITED`, `E_INTERNAL`.

## 4. Tools

### 4.1 `get_change`
Briefing completo da mudança.
```jsonc
// input
{ "change_id": "string" }
// output
{
  "id": "string",
  "proposal": "string",          // resumo do proposal.md
  "phase": "spec" | "implementacao" | "validacao" | "concluida",
  "requirements": Requirement[],
  "tasks": TaskBrief[]
}
```

### 4.2 `get_tasks`
```jsonc
// input
{ "change_id": "string", "assignee": "string?" }   // assignee opcional p/ filtrar
// output
{ "tasks": TaskBrief[] }
```

### 4.3 `update_task_status`
Reporta progresso. **`status` ∈ ReportStatus** (o agente nunca envia `concluida`/`ajustes`/
`em_validacao`/`bloqueada` por aqui — bloqueio tem tool própria).
```jsonc
// input
{ "task_id": "string", "status": "em_curso" | "implementado", "note": "string?" }
// output
{ "ok": true, "task_id": "string", "status": Status }   // status = projeção resultante
// erros: E_INVALID_TRANSITION (ex.: implementado sem em_curso), E_SCOPE, E_NOT_FOUND
```
Cada chamada **emite um evento** (`source` = harness; ADR-0002). Convenção: **uma chamada por
tarefa, na ordem em que conclui**.

### 4.4 `report_blocker`
```jsonc
// input
{ "task_id": "string", "reason": "string" }      // reason obrigatório, não-vazio
// output
{ "ok": true, "task_id": "string", "status": "bloqueada" }
// erros: E_VALIDATION (reason vazio), E_SCOPE, E_NOT_FOUND
```

### 4.5 `materialize_change`
Grava o change folder no repositório e devolve o commit (RFC-0002). Idempotente por
(`change_id`, versão).
```jsonc
// input
{ "change_id": "string" }
// output
{ "ok": true, "change_id": "string", "version": "string", "commit": "string" }
// erros: E_CONFLICT (materialização concorrente), E_INTERNAL
```

### 4.6 `scaffold_tests`
Gera o esqueleto de testes a partir dos cenários (ADR-0005).
```jsonc
// input
{ "change_id": "string?", "requirement_id": "string?" }  // um dos dois
// output
{
  "class_name": "string",
  "file": "string",                                   // caminho proposto no repo
  "methods": [ { "scenario_name": "string", "method_name": "string" } ],
  "coverage": { "total": number, "covered": number }
}
```

## 5. Semântica de sessão

1. Agente abre sessão → recebe token escopado a (`org`, `project`, `change_id`, `assignee`).
2. Chama `get_change`/`get_tasks` (briefing).
3. Trabalha; reporta cada tarefa concluída com `update_task_status` (uma por vez).
4. Em impedimento, `report_blocker`.
5. Na aprovação de uma versão da spec, `materialize_change` é chamada (pelo painel ou pelo agente
   com permissão) e o `commit` alimenta o `SpecVersion`.

## 6. Versionamento do contrato

O servidor expõe `protocolVersion` e um `contractVersion` (semver) nas capabilities. Mudanças
incompatíveis exigem novo `contractVersion` e nota de migração. Harnesses negociam na inicialização.

## 7. Itens em aberto

- Política de retry/idempotência para `update_task_status` duplicado (coordenar com RFC-0003).
- Limites de payload do briefing para changes muito grandes (paginação de `get_tasks`).
