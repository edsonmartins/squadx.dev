# Spec (delta) — context-packet

Input curado e auditável ao executor, em vez de concatenação solta de contexto. Realiza ADR-0007,
RFC-0005 (handoff).

## ADDED Requirements

### Requirement: R1 — Packet de contexto tipado e auditável
O sistema SHALL montar, antes de invocar um agente, um `ContextPacket` com `summary`, `intent`,
`facts` (com fonte), `sources` (com papel e razão de inclusão), `exclusions`, `must_preserve` e
`budget_tokens`, derivado de `main_task`, `reuse_map`, `acceptance_criteria` e `completed_subtasks`,
e SHALL persistir esse packet como snapshot estável da execução.

#### Scenario: Montagem do packet
- **WHEN** o orquestrador prepara a execução de uma subtarefa
- **THEN** um `ContextPacket` é montado a partir do contexto disponível
- **AND** o packet é persistido como snapshot e não recomputado na leitura

### Requirement: R2 — Builders de prompt preferem o packet
O sistema SHALL, quando há um `ContextPacket`, construir o prompt (caminho nativo e External CLI) a
partir de `summary/intent/facts/exclusions`, mantendo fallback à concatenação atual quando o packet
está ausente, de modo que execuções antigas continuem funcionando.

#### Scenario: Prompt nativo com packet
- **WHEN** um agente nativo recebe um contexto com `ContextPacket`
- **THEN** o prompt inclui summary, intent, facts e exclusions do packet

#### Scenario: Sem packet (compat)
- **WHEN** não há `ContextPacket` no contexto
- **THEN** o prompt é construído pela concatenação existente, sem erro

### Requirement: R3 — Orçamento de contexto ligado ao cost-cap
O sistema SHALL expor `budget_tokens` no packet e SHALL alinhá-lo ao teto de custo existente
(`cost_budget_usd`), de forma que o orçamento de entrada e o freio de loop sejam coerentes.

#### Scenario: Orçamento coerente
- **WHEN** uma execução define um teto de custo
- **THEN** o `budget_tokens` do packet reflete esse limite de entrada
