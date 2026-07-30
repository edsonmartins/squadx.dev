# Architecture Decision Records (ADRs)

Decisões arquiteturais no formato **MADR** (Markdown Any Decision Records). Numeradas a partir
de `0001`. Cada ADR tem: contexto, decisão, alternativas consideradas e consequências.

| # | Título | Status |
|---|--------|--------|
| [0001](ADR-0001-control-panel-source-of-truth-materialization.md) | Control Panel como fonte de verdade + materialização no Git | Aceito |
| [0002](ADR-0002-event-sourced-core-ui-as-projection.md) | Núcleo event-sourced; UI/estado como projeção | Aceito |
| [0003](ADR-0003-mcp-workspace-single-harness-contract.md) | MCP `workspace` como contrato único de harness | Aceito |
| [0004](ADR-0004-task-state-machine-pass5-gate.md) | Máquina de estados com Pass 5 como único caminho para "concluída" | Aceito |
| [0005](ADR-0005-scenario-test-coverage-validation.md) | Cobertura cenário↔teste como critério de validação | Aceito |
| [0006](ADR-0006-control-panel-bounded-context-stack.md) | Control Panel como bounded context na stack em camadas existente | Aceito |
| [0007](ADR-0007-governanca-trabalho-de-agentes.md) | Governança de trabalho de agentes (primitivos do Agent Work Protocol) | Proposto |
| [0008](ADR-0008-egress-enforcement-nivel-de-rede.md) | Enforcement de egress no nível de rede (sidecar) | Aceito |
| [0009](ADR-0009-sandbox-runtime-pluggable.md) | Runtime de sandbox pluggable (Docker vs OS-primitives vs microVM) | Proposto |
