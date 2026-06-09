# Spec (delta) — pass5-validation

Portão de conformidade (Pullwise): cobertura cenário↔teste e desfechos. Realiza ADR-0005,
RFC-0004 (e o gate do ADR-0004).

## ADDED Requirements

### Requirement: R1 — Cobertura cenário↔teste obrigatória
O sistema SHALL exigir que todo cenário de aceite de um requisito tenha ao menos um teste que o
cubra; um cenário sem teste SHALL reprovar a validação.

#### Scenario: Cenário sem teste reprova
- **WHEN** o Pass 5 roda para uma tarefa cujo requisito tem um cenário sem teste mapeado
- **THEN** o resultado é reprovado
- **AND** a crítica indica o cenário descoberto

#### Scenario: Todos os cenários cobertos passam na etapa de cobertura
- **WHEN** todos os cenários do requisito têm ao menos um teste mapeado e presente
- **THEN** a etapa de cobertura é satisfeita

### Requirement: R2 — Teste derivado falhando reprova
O sistema SHALL reprovar a validação quando um teste mapeado a um cenário da tarefa falha na
execução.

#### Scenario: Teste mapeado quebrado
- **WHEN** um teste mapeado a um cenário da tarefa falha ao ser executado
- **THEN** o Pass 5 reprova com a referência ao teste que falhou

### Requirement: R3 — Conformidade comportamental (revisão semântica)
O sistema SHALL conferir, via revisor plugável (Pullwise por padrão), se o código corresponde ao
comportamento descrito nos cenários, reprovando quando diverge.

#### Scenario: Código diverge dos cenários
- **WHEN** o revisor conclui que o código diverge do comportamento WHEN/THEN
- **THEN** o Pass 5 reprova
- **AND** anexa a crítica retornada pelo revisor

### Requirement: R4 — Desfechos do Pass 5
O sistema SHALL produzir exatamente dois desfechos: aprovado (→ `concluida`, `pass5 = pass`) ou
reprovado (→ `ajustes`, `pass5 = fail`, com a crítica em `revise_reason`, reabrindo para
`em_curso`).

#### Scenario: Aprovação conclui a tarefa
- **WHEN** o Pass 5 aprova a tarefa
- **THEN** a tarefa transita para `concluida` e `pass5 = pass`

#### Scenario: Reprovação reabre com a crítica
- **WHEN** o Pass 5 reprova a tarefa
- **THEN** a tarefa registra `pass5 = fail` e a crítica em `revise_reason`
- **AND** reabre para `em_curso`

### Requirement: R5 — Disparo no merge e idempotência
O sistema SHALL disparar o Pass 5 no merge do PR da tarefa e SHALL produzir resultado idempotente
por (tarefa, sha do PR).

#### Scenario: Reexecução não duplica desfecho
- **WHEN** o Pass 5 é reexecutado para a mesma tarefa e o mesmo sha de PR
- **THEN** o sistema produz o mesmo desfecho sem registrar um segundo resultado divergente

### Requirement: R6 — Exibição de cobertura
O sistema SHALL exibir, na tela de validação, o mapa de cobertura cenário→teste (✓ coberto /
✕ sem teste) e os critérios de reprovação.

#### Scenario: Fila de validação mostra cobertura
- **WHEN** um usuário abre a tela de validação de uma tarefa
- **THEN** o sistema mostra cada cenário com seu estado de cobertura e o desfecho/crítica
