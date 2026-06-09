# SquadX.dev — Constituição do Projeto

> Princípios invioláveis do projeto. Mudanças aqui exigem um ADR que registre o porquê.
> Em caso de conflito entre artefatos, vale a precedência:
> **repositório (código) > CONSTITUTION.md > ADRs > RFCs > specs (OpenSpec) > documentos/ (informais)**.

## Propósito

O SquadX.dev é uma plataforma onde **humanos e agentes de IA desenvolvem software lado a lado**.
Ela tem duas faces que se complementam:

- **Runtime de execução** (já existente): orquestra agentes em sandboxes Docker isolados, com
  Live View, RBAC, billing e observabilidade.
- **Control Panel / SquadX.dev Spec** (em especificação): a camada **spec-native** onde a
  **especificação é a unidade de trabalho** — discute-se e versiona-se a spec, ela gera as
  tarefas, e nada é dado como pronto sem passar por uma validação que confere o código contra
  a spec.

## Princípios

1. **A spec é a fonte de verdade.** Requisitos são descritos como deltas (ADDED / MODIFIED /
   REMOVED) com cenários de aceite em **WHEN/THEN**. Toda tarefa nasce de um requisito e aponta
   de volta para ele (rastreabilidade bidirecional).

2. **Sem drift, sem lock-in (materialização híbrida).** O Control Panel é dono da autoria e do
   versionamento da spec; a cada versão aprovada, **materializa** os arquivos no repositório
   (commit). O PR carrega spec + código no mesmo diff; o Git é o registro reconciliado. A spec
   permanece markdown que o time possui.

3. **Estado é projeção de eventos.** O "onde estamos" nunca é digitado à mão: é derivado de
   eventos (webhooks de Git + eventos MCP). O painel **observa** a execução; não é dono do código.

4. **`concluída` só pela validação (Pass 5).** O estado terminal de uma tarefa **nunca** é
   definido pelo agente nem pelo desenvolvedor — é atribuído pelo portão de conformidade. É isso
   que impede o descolamento entre spec e código.

5. **Cobertura cenário↔teste é obrigatória.** Cada cenário de aceite precisa de ≥1 teste que o
   cubra. Cenário sem teste **reprova** na validação. Testes são derivados da spec, nunca
   escritos em paralelo a ela.

6. **Contrato único e harness plugável.** Harnesses (Claude Code, Codex, Gemini CLI, Cursor)
   falam o **mesmo contrato MCP** com o workspace. O harness é a ferramenta; o **modelo LLM é
   escolhido pelo usuário**. Suportar mais um harness é configuração, não reescrita.

7. **Humanos e agentes são cidadãos de primeira classe.** Toda tarefa tem um responsável
   (pessoa ou agente). Duas pistas de execução: humano (IDE + commits/PR, status via webhook de
   Git) e agente (sessão no harness, status via MCP).

8. **Reúso da stack existente.** O Control Panel é um novo bounded context **sobre** o runtime
   atual do SquadX.dev — não uma reescrita. Reusa Project, identidade/Agent e o runtime de
   execução; segue as convenções do repositório (ver abaixo).

## Convenções de engenharia

- **Backend**: Spring Boot 3.4 / Java 21, arquitetura **em camadas** (controller/service/
  repository/dto/model), Spring Data JPA + MapStruct, Flyway para migrações. **JDK 21** é
  obrigatório para build (ver `CLAUDE.md`).
- **Persistência**: PostgreSQL, **sem prefixo de aplicação** nas tabelas; multi-tenancy por
  organização; trilha de **auditoria** das transições de estado (LGPD; preferência local-first).
- **Frontend**: Next.js 16 / React 19 / TypeScript, TanStack Query + Zustand, shadcn.
- **Client/Runtime**: Python 3.12, LangGraph + LiteLLM, sandboxes Docker hardened.
- **Idioma**: **spec e documentação em português; código e identificadores em inglês.**

## Governança

- **OpenSpec** (`openspec/`) é o formato de spec; mudanças vivem em `openspec/changes/<id>/` e,
  ao serem aplicadas, atualizam `openspec/specs/`.
- **ADRs** (`docs/adr/`, formato MADR) registram decisões arquiteturais; **RFCs** (`docs/rfc/`)
  detalham contratos e algoritmos. Ambos numerados em continuidade a partir de `0001`.
- Toda task referencia o requisito de origem e o ADR/RFC pertinente.
- `CLAUDE.md` orienta agentes de codificação; `openspec/AGENTS.md` orienta o fluxo OpenSpec.
