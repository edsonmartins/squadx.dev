# Spec (delta) — workspace-mcp-server

## ADDED Requirements

### Requirement: R8 — Consultar code intelligence por revisão
O sistema SHALL adicionar `search_code`, `get_symbol_context`, `get_dependencies` e
`get_change_impact` ao contrato harness-agnóstico.

#### Scenario: harness consulta impacto
- **WHEN** qualquer harness chama `get_change_impact` dentro do escopo da sessão
- **THEN** recebe o contrato canônico com revisão, provider, confiança e evidências
- **AND** a operação respeita organização, projeto e limites de paginação.

