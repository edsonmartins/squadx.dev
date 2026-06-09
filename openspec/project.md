# OpenSpec — Contexto do Projeto (SquadX.dev)

Este arquivo dá o contexto que agentes e humanos precisam para propor e revisar mudanças de
spec neste repositório. Spec em português; identificadores/código em inglês.

## O que é o SquadX.dev

Plataforma onde humanos e agentes de IA desenvolvem software lado a lado. Duas faces:

- **Runtime de execução** (existente): orquestra agentes em sandboxes Docker, Live View, RBAC,
  billing, observabilidade. Stack: Spring Boot 3.4/Java 21 (camadas), Next.js 16, client Python
  (LangGraph/LiteLLM).
- **Control Panel / SquadX.dev Spec** (em especificação): camada spec-native — a spec é a unidade
  de trabalho; gera tarefas; `concluída` só via validação (Pass 5); harness plugável via MCP.

Ver `CONSTITUTION.md` para os princípios invioláveis.

## Convenções de stack (resumo)

- Backend: Spring Boot 3.4 / Java 21 **em camadas** (controller/service/repository/dto/model),
  Spring Data JPA + MapStruct, Flyway. PostgreSQL **sem prefixo de aplicação** nas tabelas.
- Frontend: Next.js 16 / React 19 / TS, TanStack Query + Zustand, shadcn.
- Multi-tenancy por organização; auditoria das transições (LGPD).
- ADRs em `docs/adr/` (MADR), RFCs em `docs/rfc/`, numerados a partir de `0001`.

## Layout OpenSpec

```
openspec/
  project.md                      # este arquivo
  AGENTS.md                       # fluxo OpenSpec para agentes
  specs/<capability>/spec.md      # verdade atual (preenchida quando um change é arquivado)
  changes/<change-id>/
    proposal.md                   # por quê + o que muda
    design.md                     # decisões técnicas (opcional, mas usado aqui)
    tasks.md                      # checklist; cada task → requisito + ADR/RFC
    specs/<capability>/spec.md     # delta: ## ADDED/MODIFIED/REMOVED Requirements
```

## Capabilities do Control Panel (change `add-control-panel`)

- `control-panel-work-model` — project → change → requirement → task; máquina de 6 estados; projeção.
- `spec-versioning-materialization` — versionamento + materialização no Git.
- `workspace-mcp-server` — contrato MCP harness-agnóstico.
- `execution-tracking` — webhooks Git + eventos MCP (estado = projeção).
- `pass5-validation` — portão de conformidade + cobertura cenário↔teste.
- `harness-connectors` — cadastro de harnesses + seleção de modelo LLM.
