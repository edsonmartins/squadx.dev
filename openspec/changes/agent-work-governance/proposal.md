# Proposta — Governança de trabalho de agentes (Agent Work Governance)

## Por quê

O SquadX orquestra **squads de agentes** sobre a mesma tarefa/projeto. Conforme mais de um agente
(ou mais de uma pessoa) atua, aparecem problemas de coordenação que o projeto `opentag` (um *Agent
Work Protocol*) já sistematizou — estudo em `documentos/LEARNINGS-OpenTag.md`:

- **Ruído**: todo log de todo agente vai para o dashboard; N agentes inundam a thread humana.
- **Corrida/duplicação**: dois gatilhos para a mesma tarefa criam trabalho concorrente; hoje só há
  um check de `RUNNING` (`backend/.../service/ExecutionService.java:52`).
- **Contexto despejado**: o input ao agente é concatenação solta (`agents/base.py`), sem fronteira
  auditável.
- **Segredos no sandbox**: o caminho External CLI recebe o env sem allowlist; artefatos do CLI
  (`.claude/.codex`) podem poluir commits.

O SquadX já tem worktree-por-run, arbiter, cost-cap e severidade de review — esta mudança adota só
os primitivos de governança que **faltam**, reusando o que existe.

## O que muda

Quatro capabilities (ADR-0007, RFC-0005):

1. **attention-budget** — todo evento/log de execução carrega `visibility` (`human|audit|debug`) e
   `importance` (`low|normal|high|blocking`); o dashboard ganha **modo silencioso** (default só
   `human`). Reusa o vocabulário de severidade de review para `importance`.
2. **run-admission** — decisão de admissão explícita antes de criar `Execution`
   (`start | drop_duplicate | queue_follow_up | needs_human_decision`), com `idempotency_key`
   (replay idempotente) e `FollowUpRequest` durável quando já há run ativo na mesma tarefa.
3. **context-packet** — o input ao executor vira objeto curado e auditável
   (`summary/intent/facts/sources/exclusions/budget_tokens`), persistido como snapshot e preferido
   pelos builders de prompt (nativo e External CLI), com fallback à concatenação atual.
4. **sandbox-hardening** — allowlist/scrub de env sensível + detecção de prompt-injection no caminho
   External CLI (`enforce|audit|off`), e limpeza de `.claude/.codex/.omx` antes do commit.

## Princípios que isto realiza

- **Estado como projeção de eventos** (ADR-0002) — `visibility/importance` é classificação na trilha.
- **Governança de execução** — ADR-0007; contrato RFC-0005; reusa `dedup_key` de RFC-0003.
- **Stack do repo** (ADR-0006) — camadas + JPA + MapStruct + Flyway; sem novo módulo.

## Fora de escopo (direção futura)

- **Proposal / Approval / Apply** (snapshot imutável, supersessão por domínio, ApplyPlan).
- **UI de task-graph** para coordenação agente-a-agente (substituir "chat" por grafo de tarefas).
- **Router de executor** por custo/latência/performance histórica.
- Roteamento de callback dirigido por `visibility` (hoje só filtra apresentação no dashboard).
