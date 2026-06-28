# Spec (delta) — attention-budget

Classificação de eventos/logs de execução por `visibility` e `importance`, para manter o dashboard
legível sob squads (modo silencioso). Realiza ADR-0007, ADR-0002, RFC-0005 §1.

## ADDED Requirements

### Requirement: R1 — Todo evento de execução carrega visibility e importance
O sistema SHALL associar a cada log/evento de uma `Execution` um `visibility`
(`human | audit | debug`) e um `importance` (`low | normal | high | blocking`), persistidos e
expostos na resposta da API em snake_case.

#### Scenario: Log humano de conclusão
- **WHEN** o daemon reporta a conclusão de uma execução
- **THEN** o evento é persistido com `visibility = "human"` e `importance = "high"`
- **AND** aparece no fluxo padrão do dashboard

#### Scenario: Log interno de ferramenta
- **WHEN** o daemon emite um log de ferramenta/planejamento interno
- **THEN** o evento é persistido com `visibility` em `audit` ou `debug`
- **AND** não aparece no modo silencioso do dashboard

### Requirement: R2 — Defaults por tipo de evento
O sistema SHALL derivar `visibility`/`importance` a partir do tipo do evento por um mapa único, e
SHALL mapear a severidade de review existente (`blocker/major/minor/nit`) para `importance`
(`blocking/high/normal/low`), com apenas `blocker` como `visibility = "human"`.

#### Scenario: Finding de review blocker
- **WHEN** o reviewer registra um finding com severidade `blocker`
- **THEN** o evento recebe `importance = "blocking"` e `visibility = "human"`

#### Scenario: Finding de review nit
- **WHEN** o reviewer registra um finding com severidade `nit`
- **THEN** o evento recebe `importance = "low"` e `visibility = "audit"`

### Requirement: R3 — Modo silencioso e compatibilidade
O sistema SHALL, por padrão, exibir no dashboard apenas eventos com `visibility = "human"`, com
opção de alternar para o modo auditoria (todos). Quando um client não envia os metadados, o sistema
SHALL aplicar o default por tipo; tipo desconhecido SHALL receber `visibility = "human"`,
`importance = "normal"`.

#### Scenario: Toggle de auditoria
- **WHEN** o usuário ativa o modo auditoria na tela de logs
- **THEN** eventos `audit` e `debug` passam a ser exibidos junto dos `human`

#### Scenario: Client antigo sem metadados
- **WHEN** um log chega sem `visibility`/`importance`
- **THEN** o sistema aplica o default por tipo do evento
- **AND** se o tipo é desconhecido, usa `human`/`normal` (não esconde por engano)
