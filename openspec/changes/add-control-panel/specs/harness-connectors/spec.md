# Spec (delta) — harness-connectors

Cadastro de harnesses e seleção de modelo LLM por harness. Realiza ADR-0003 (contrato único).

## ADDED Requirements

### Requirement: R1 — Cadastro de harnesses
O sistema SHALL manter um cadastro de harnesses (`key`, `name`, `vendor`, `status`), cada um
falando o contrato MCP `workspace`.

#### Scenario: Listar conectores
- **WHEN** um usuário abre a tela de conectores
- **THEN** o sistema lista os harnesses com seu status (`conectado` | `disponível`)

#### Scenario: Harness usa o contrato único
- **WHEN** um harness cadastrado abre uma sessão
- **THEN** ele consome as mesmas tools MCP do `workspace` (workspace-mcp-server)

### Requirement: R2 — Seleção de modelo LLM por harness
O sistema SHALL permitir escolher o modelo LLM de cada harness a partir dos modelos disponíveis
daquele harness.

#### Scenario: Escolher modelo
- **WHEN** um usuário seleciona um modelo dentre os `models[]` de um harness
- **THEN** o sistema registra o `model` escolhido para aquele harness

#### Scenario: Modelo do harness resolve no responsável agente
- **WHEN** uma tarefa é atribuída a um responsável agente que usa um harness
- **THEN** o modelo exibido no responsável é o modelo escolhido daquele harness

### Requirement: R3 — Adicionar harness é configuração
O sistema SHALL permitir adicionar suporte a um novo harness por cadastro/configuração, sem
exigir alteração do contrato MCP.

#### Scenario: Novo harness sem mudar contrato
- **WHEN** um novo harness é cadastrado
- **THEN** ele passa a aparecer nos conectores e pode abrir sessão usando o mesmo contrato MCP
- **AND** nenhuma das tools existentes precisa mudar
