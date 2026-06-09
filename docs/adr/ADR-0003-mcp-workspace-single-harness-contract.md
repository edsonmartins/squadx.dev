# ADR-0003 — MCP `workspace` como contrato único e harness-agnóstico

## Status

Aceito — 2026-06-09.

## Contexto

Agentes executam em **harnesses** diferentes (Claude Code, Codex, Gemini CLI, Cursor), e o
**modelo LLM** é escolhido pelo usuário. Se cada harness exigir integração própria, "suportar
mais um" vira reescrita, e o briefing/relato de status fica inconsistente entre eles.

O runtime atual do SquadX.dev **não possui** infraestrutura MCP — ela é nova.

## Decisão

Expor um **MCP server `workspace`** com um **contrato único** que todo harness consome. As tools
mínimas (schemas formais no RFC-0001):

- `get_change(change_id)` — briefing: proposta, fase, requisitos (com cenários) e tarefas.
- `get_tasks(change_id)` — lista de tarefas com `requirementRef` e status.
- `update_task_status(task_id, status, note?)` — status ∈ {`em_curso`, `implementado`}.
- `report_blocker(task_id, reason)` — marca `bloqueada` com motivo.
- `materialize_change(change_id)` — grava o change folder no repo e devolve o `commit`.
- `scaffold_tests(change_id | requirement_id)` — gera esqueleto de testes a partir dos cenários.

O harness é **a ferramenta**; o **modelo é configuração**. Adicionar um harness é cadastro
(ADR/RFC de harness-connectors), não reescrita. O agente abre a sessão chamando `get_change`/
`get_tasks` e reporta **uma chamada por tarefa, na ordem em que conclui** — é isso que mantém o
"onde estamos" em tempo real (ADR-0002).

## Alternativas consideradas

1. **Integração por harness (adapters ad hoc).** N integrações divergentes; alto custo de
   manutenção. Rejeitada.
2. **API REST própria do painel para os agentes.** Funciona, mas reimplementa o que o ecossistema
   de harnesses já fala (MCP) e perde a portabilidade entre ferramentas. Rejeitada.
3. **MCP `workspace` único (escolhido).** Um contrato, muitos harnesses; alinhado ao padrão de
   mercado de agentes.

## Consequências

- **Positivas:** harness plugável; briefing e status uniformes; o "onde estamos" alimentado por
  um único canal de eventos do lado do agente.
- **Custos:** construir e operar um MCP server novo (transporte, auth, versionamento do contrato
  — RFC-0001); `materialize_change` cruza a fronteira spec↔Git (coordenar com RFC-0002).
- **Relacionado:** RFC-0001 (schemas), ADR-0002 (eventos), capability `harness-connectors`.
