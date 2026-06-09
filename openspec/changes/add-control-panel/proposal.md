# Proposta — Control Panel (SquadX.dev Spec)

## Por quê

Hoje o trabalho se fragmenta entre ferramentas e o contexto se perde no caminho:

- A discussão e a spec vivem no chat; as tarefas no board; o código no repositório — três
  lugares desconectados.
- O "todo" se perde: é preciso reconstruir manualmente onde o trabalho está.
- Tarefas chegam órfãs no board, sem vínculo com o requisito que as originou — perde-se o
  "porquê".

O **Control Panel** é o plano de controle onde a **especificação governa o trabalho**: a spec é
a unidade de trabalho, gera as tarefas, humanos e agentes implementam lado a lado, e **nada é
dado como pronto sem passar por uma validação** que confere o código contra a spec.

## O que muda

Introduz um novo bounded context "spec" **sobre** o runtime de execução existente (ADR-0006),
com seis capabilities:

1. **control-panel-work-model** — projeto → mudança → requisito → tarefa; máquina de 6 estados
   (`a_fazer → em_curso → em_validacao → concluida`, com `bloqueada` e `ajustes`); a projeção
   "onde estamos".
2. **spec-versioning-materialization** — versionamento semântico da spec e **materialização no
   Git** (cada versão aprovada vira commit; PR carrega spec + código no mesmo diff).
3. **workspace-mcp-server** — o contrato **MCP** harness-agnóstico (`get_change`, `get_tasks`,
   `update_task_status`, `report_blocker`, `materialize_change`, `scaffold_tests`).
4. **execution-tracking** — as duas pistas de execução: ingestão de **webhooks de Git** (humano)
   e **eventos MCP** (agente); o estado é **projeção** desses eventos.
5. **pass5-validation** — portão de conformidade (Pullwise): **cobertura cenário↔teste** e os
   desfechos `aprovado` / `ajustes necessários` (com a crítica).
6. **harness-connectors** — cadastro de harnesses (Claude Code, Codex, Gemini CLI, Cursor) e
   **seleção do modelo LLM** por harness.

## Princípios que isto realiza

- **Spec como fonte de verdade** + **materialização híbrida** (sem drift, sem lock-in) — ADR-0001.
- **Estado como projeção de eventos** — ADR-0002.
- **MCP como contrato único** — ADR-0003.
- **`concluida` só pelo Pass 5** — ADR-0004.
- **Cobertura cenário↔teste obrigatória** — ADR-0005.
- **Novo bounded context na stack em camadas existente, com reúso** — ADR-0006.

## Fora de escopo (desta mudança)

- Reescrever o runtime de execução de agentes (sandboxes/daemon) — é **reusado** como a pista do
  agente, não refeito.
- Implementação do harness-side de cada CLI (Claude Code, etc.) — aqui definimos o **contrato**
  (MCP); a integração de cada harness é cadastro (harness-connectors).
- Escolha do fornecedor de revisão semântica além de Pullwise (interface plugável — RFC-0004).

## Decisões registradas com a equipe

- Bootstrap completo de governança (CONSTITUTION + openspec/ + ADRs/RFCs do 0001).
- Novo bounded context (não estender a `Task` de execução existente).
- Seguir o padrão **em camadas** do repositório (não hexagonal/QueryDSL) — precedência repo > prompt.
