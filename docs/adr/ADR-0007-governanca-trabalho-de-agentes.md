# ADR-0007 — Governança de trabalho de agentes (primitivos do Agent Work Protocol)

## Status

Proposto — 2026-06-27.

## Contexto

O SquadX orquestra **squads de agentes** que executam em sandboxes, com status/logs voltando ao
dashboard por STOMP. À medida que mais de um agente (ou mais de uma pessoa) atua sobre a mesma
tarefa, surgem os mesmos problemas que o projeto `opentag` (um *Agent Work Protocol*) já
sistematizou (ver `documentos/LEARNINGS-OpenTag.md`):

- **Ruído**: todo log de todo agente vai para a thread humana; N agentes inundam o dashboard.
- **Corrida/duplicação**: dois gatilhos para a mesma tarefa criam trabalho concorrente; hoje só
  existe um check de `RUNNING` em `ExecutionService` (`backend/.../service/ExecutionService.java:52`).
- **Contexto despejado**: o input ao agente é concatenação solta (`agents/base.py` `_build_context_string`),
  sem fronteira auditável do que entrou e por quê.
- **Segredos no sandbox**: o caminho External CLI recebe o env do processo sem allowlist, e artefatos
  internos do CLI (`.claude/.codex`) podem poluir commits.

O SquadX **já** antecipou várias respostas do OpenTag: worktree-por-run (`git/worktree.py`), arbiter
loop-breaker, cost-cap (`state.cost_budget_usd`) e severidade de review (`blocker/major/minor/nit`).
A decisão aqui é **adotar os primitivos de governança que ainda faltam**, reusando o que já existe.

## Decisão

Adotar quatro primitivos de governança (A–D), na stack do repo (camadas + JPA + MapStruct + Flyway
no backend; LangGraph/daemon no client), sem novo módulo:

1. **A — Attention Budget.** Todo evento/log de execução carrega `visibility` (`human|audit|debug`)
   e `importance` (`low|normal|high|blocking`). Default conservador: tool-logs/prompts/planejamento
   interno → `audit|debug`; conclusão/blocker/escalação → `human`. O dashboard filtra por `visibility`
   (modo silencioso por padrão). Contrato em RFC-0005 §1.
2. **B — Run Admission.** Antes de criar uma `Execution`, uma decisão de admissão explícita
   (`start | drop_duplicate | queue_follow_up | needs_human_decision`) com `idempotency_key` para
   replay idempotente e `FollowUpRequest` durável quando já há run ativo na mesma tarefa. Contrato em
   RFC-0005 §2. Reusa a noção de `dedup_key` de RFC-0003 §4.
3. **C — Context Packet.** O input ao executor passa a ser um objeto curado e auditável
   (`summary/intent/facts/sources/exclusions/budget_tokens`), persistido como snapshot estável e
   preferido pelos builders de prompt (nativo e External CLI), com fallback à concatenação atual.
4. **D — Hardening do sandbox.** Allowlist + scrub de env sensível e detecção de prompt-injection no
   caminho External CLI (modo `enforce|audit|off`), e limpeza de `.claude/.codex/.omx` antes do commit.

**Reúso explícito** (não reescrita): o vocabulário de severidade de review alimenta `importance`; o
`cost_budget_usd` existente é o consumidor de `budget_tokens`; `WebSocketEventService` +
`@TransactionalEventListener` continuam o barramento; `git/worktree.py` continua o isolamento.

## Alternativas consideradas

1. **Não fazer nada / resolver caso a caso.** Mantém o acoplamento dos problemas (ruído, corrida,
   contexto, segredo) espalhado por handlers. Rejeitada — não escala para squads.
2. **Adotar o protocolo inteiro do OpenTag** (Proposal/Approval/Apply, router de executor, UI de
   task-graph) de uma vez. Esforço alto e algumas peças são direção de produto, não necessidade atual.
   Rejeitada por escopo; registrada como direção futura.
3. **Adotar só os quatro primitivos A–D, reusando o que o SquadX já tem (escolhido).**

## Consequências

- **Positivas:** dashboard legível sob squads (modo silencioso); coordenação segura de gatilhos
  simultâneos (dedup/follow-up); input ao agente auditável; menos vazamento de segredo e commits mais
  limpos. Tudo na stack existente.
- **Custos:** duas migrações Flyway novas (V33, V34) e colunas/tabela novas; o client passa a anexar
  metadados de evento (compat: defaults quando ausentes); um seam novo (`RunAdmissionService`) no
  caminho de criação de `Execution`.
- **Relacionado:** RFC-0005 (contrato), `documentos/LEARNINGS-OpenTag.md` (estudo), ADR-0002
  (event-sourced/UI como projeção — a Attention Budget é uma classificação na trilha de eventos),
  RFC-0003 (`dedup_key`); change `agent-work-governance`.
