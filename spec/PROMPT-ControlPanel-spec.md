# Prompt — Spec, ADRs e RFCs do Control Panel (SquadX.dev Spec)

> **Como usar:** cole este prompt no Claude Code, na raiz do repositório do SquadX.dev.
> Ele foi escrito para o seu fluxo: primeiro o Claude Code entende o projeto, propõe um
> plano em **plan mode** para você aprovar, e só então gera os artefatos.

---

Você é o arquiteto de software responsável por especificar o **Control Panel** — o módulo
"SquadX.dev Spec": a camada spec-native de gestão de trabalho onde a especificação é a
unidade de trabalho e humanos e agentes de IA implementam lado a lado.

Sua tarefa é produzir três conjuntos de artefatos, **alinhados ao projeto existente**:
1. A **spec** no formato OpenSpec (change folders com delta specs e cenários WHEN/THEN).
2. Os **ADRs** (formato MADR), numerados em continuidade aos que já existem.
3. Os **RFCs** para os mecanismos mais profundos (contratos e algoritmos).

## Insumos anexos (além do repositório)

Junto deste prompt vão os materiais que já descrevem o Control Panel de forma concreta:

- **Modelo de domínio (`SquadX-dev-Spec-Dominio.md`)** — a **referência de domínio e
  comportamento, destilada e autoritativa**: entidades, máquina de status com transições,
  contrato das tools do MCP (com schemas), versionamento/materialização, regras do Pass 5,
  spec→testes, as duas pistas de execução e o mapa de telas. **Use este documento como a
  fonte primária de comportamento.**
- **Protótipo HTML (`squadx-control-panel.html`)** — companheiro **visual** do documento
  acima: confirma layout, fluxo de telas e detalhes de UX. Consulte-o quando precisar ver
  como algo se apresenta.
  - **Atenção:** o protótipo é um mock client-side (HTML/JS, dados em memória). Isso **não**
    é a arquitetura-alvo. A implementação real deve reusar a stack do SquadX.dev (backend,
    frontend, persistência, MCP, deploy do repo). Trate-o como spec de UX, jamais como
    decisão técnica.
- **Proposta (`SquadX-dev-Spec-Proposta.pdf`)** — o **racional e o material "para
  discussão"**: o problema, o ciclo e o porquê das decisões. Use como contexto e base do
  `proposal.md`.

Precedência em caso de divergência: **repositório > "Contexto de design" deste prompt >
modelo de domínio > protótipo > proposta.**

## Etapa 0 — Entenda o projeto antes de escrever qualquer artefato (obrigatório)

Em **plan mode**, sem gerar arquivos ainda:
- Leia o `CONSTITUTION.md` e todos os ADRs/RFCs existentes do repositório.
- Mapeie o escopo atual do SquadX.dev (runtime de execução de agentes) e como ele está
  estruturado: módulos, camadas, pontos de extensão.
- Identifique a stack e as convenções reais em uso (backend, frontend, persistência,
  mensageria, infra MCP, padrões de teste, deploy).
- Verifique se o OpenSpec já está inicializado (`openspec/`), o formato de spec adotado e
  os `AGENTS.md` existentes.
- Liste o que já existe e será **reutilizado** pelo Control Panel vs. o que será **criado**.
- Leia também os **insumos anexos** (protótipo e proposta) para extrair o domínio e os
  comportamentos esperados, respeitando o aviso de que o protótipo é um mock, não a arquitetura.

**Pare ao final da Etapa 0** e me apresente: (a) um resumo do seu entendimento do projeto,
(b) a decomposição proposta em capabilities, (c) a lista de ADRs e RFCs que pretende criar.
Só prossiga após a minha aprovação. Se algo do contexto de design abaixo conflitar com o
que você encontrou no repo, aponte o conflito em vez de assumir.

## Contexto de design — decisões já tomadas (não reabrir sem motivo)

O Control Panel não é "mais uma ferramenta SDD"; é o plano de controle onde a spec governa
o trabalho. Decisões firmadas:

- **Spec como fonte de verdade**, no modelo de **delta** do OpenSpec: requisitos marcados
  como ADDED / MODIFIED / REMOVED, cada um com cenários de aceite em **WHEN/THEN**.
- **Rastreabilidade**: cada requisito gera tarefas vinculadas a ele; toda tarefa aponta
  para o requisito de origem.
- **Materialização híbrida (anti-drift, anti-lock-in)**: o Control Panel é dono da autoria
  e do **versionamento** da spec; a cada versão aprovada, ele **materializa** os arquivos no
  repositório (commit). O PR carrega spec + código no mesmo diff; o Git é o registro
  reconciliado. O painel escreve nas duas pontas, então elas não divergem.
- **Modelo de status (6 estados do board)**: `a fazer → em execução → em validação →
  concluída`, com `bloqueada` e `ajustes necessários` como desvios. O estado `concluída`
  **nunca** é definido pelo agente nem pelo dev — é atribuído pela validação (Pass 5).
- **Humanos e agentes juntos**: cada tarefa tem um responsável (pessoa ou agente). Duas
  pistas de execução: humano (IDE + commits/PR, status via webhook de Git) e agente (sessão
  no harness, status reportado via MCP).
- **Harness plugável + modelo LLM à escolha**: Claude Code, Codex, Gemini CLI, Cursor — todos
  falam o **mesmo contrato MCP** com o workspace. O harness é a ferramenta; o modelo LLM é
  escolhido pelo usuário. Suportar mais um harness é configuração, não reescrita.
- **MCP server `workspace`** (contrato harness-agnóstico). Tools mínimas:
  `get_change`, `get_tasks`, `update_task_status`, `report_blocker`, `materialize_change`,
  `scaffold_tests`.
- **Spec → testes**: a partir do `spec.md`, gerar esqueleto de testes (um por cenário,
  rastreável). O Pass 5 exige **cobertura cenário↔teste**: cenário sem teste reprova.
- **Núcleo orientado a eventos**: o estado das tarefas é projeção de eventos (webhooks de
  Git + eventos MCP). A UI ("onde estamos") é projeção, não dado digitado — o painel
  **observa** a execução, não é dono do código.
- **Pass 5 = portão de conformidade** (Pullwise): confere código contra os cenários de
  aceite; desfechos `aprovado` ou `ajustes necessários` (com a crítica anexada).

## Decomposição sugerida (valide e ajuste contra o projeto real)

Proponha como capabilities OpenSpec separadas (ajuste os nomes ao padrão do repo):
- **control-panel-work-model** — projeto → mudança → requisito → tarefa; máquina de estados;
  projeção "onde estamos".
- **spec-versioning-materialization** — versionamento da spec e materialização no Git.
- **workspace-mcp-server** — o contrato MCP (as tools acima).
- **execution-tracking** — as duas pistas: ingestão de webhooks de Git + eventos MCP.
- **pass5-validation** — portão de conformidade e cobertura cenário↔teste.
- **harness-connectors** — cadastro de harnesses e escolha de modelo LLM.

## Entregáveis

1. **OpenSpec change folders** (um por capability ou agrupados conforme o padrão do repo):
   `proposal.md`, `specs/<capability>/spec.md` (requisitos ADDED + cenários WHEN/THEN),
   `design.md` (decisões técnicas) e `tasks.md` (checklist; cada task referenciando o
   requisito e o ADR/RFC pertinente).
2. **ADRs (MADR)** para, no mínimo: (a) Control Panel como fonte de verdade + materialização
   no repo; (b) núcleo event-sourced com a UI como projeção; (c) MCP como contrato único de
   harness; (d) máquina de estados com Pass 5 como único caminho para "concluída"; (e)
   cobertura cenário↔teste como critério de validação; (f) decisões de stack (reúso do que
   já existe no SquadX.dev).
3. **RFCs** para: (1) contrato do `workspace` MCP server — schema de entrada/saída de cada
   tool; (2) versionamento + materialização (como uma versão da spec vira commit); (3)
   máquina de estados das tarefas + ingestão de eventos (webhook de Git + eventos MCP); (4)
   o algoritmo do Pass 5 (cobertura e desfechos).

## Convenções obrigatórias

- Respeite o `CONSTITUTION.md` e os padrões já vigentes no repo (em caso de conflito, pare e
  pergunte).
- Numere ADRs e RFCs **em continuidade** aos existentes; não recomece do zero.
- Toda task referencia a regra do requisito e o ADR/RFC correspondente.
- Persistência: sem prefixo de tabela (se PostgreSQL); arquitetura hexagonal/DDD; siga o
  padrão do projeto para transações e queries (ex.: QueryDSL/native em adapters).
- Sensibilidade a dados e LGPD: trilha de auditoria das transições; preferência local-first.
- Spec em português; código e identificadores em inglês, conforme o repo.

## Processo e Definition of Done

- **Gate de plan mode**: Etapa 0 → minha aprovação → geração.
- Gere de forma incremental, capability por capability; ao final de cada uma, resuma o que
  produziu.
- **DoD**: todo requisito tem ≥1 cenário de aceite; todo cenário é rastreável a uma task;
  todo ADR está em MADR com contexto/decisão/alternativas/consequências; todo RFC traz os
  contratos (schemas) ou algoritmos descritos de forma implementável; nenhuma decisão do
  contexto de design acima foi silenciosamente contrariada.

**Comece pela Etapa 0** e me apresente o entendimento do projeto + a decomposição + a lista
de ADRs e RFCs antes de gerar qualquer arquivo.
