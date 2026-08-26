# Spec (delta) — code-intelligence-gateway

## ADDED Requirements

### Requirement: R1 — Identificar fatos por snapshot imutável
O sistema SHALL vincular todo fato de code intelligence a organização, repositório e revisão Git.

#### Scenario: consulta por revisão
- **WHEN** um consumidor consulta um símbolo no snapshot de uma revisão
- **THEN** a resposta identifica a mesma revisão e o provider que produziu o fato
- **AND** fatos de outra revisão não são misturados.

### Requirement: R2 — Manter contrato independente de provider
O sistema SHALL oferecer tipos e operações canônicas sem expor contratos RepoWise, Pullwise ou Sourcebot.

#### Scenario: troca de provider
- **WHEN** a policy seleciona outro provider para a mesma operação
- **THEN** o consumidor recebe o mesmo schema canônico
- **AND** a resposta informa provider, versão, confiança e evidências.

### Requirement: R3 — Isolar provider e aplicar fallback
O sistema SHALL isolar índices por tenant e aplicar timeout/fallback configurado sem vazar credenciais.

#### Scenario: RepoWise indisponível
- **WHEN** o RepoWise excede o timeout ou abre o circuit breaker
- **THEN** o Gateway usa o fallback permitido ou retorna indisponibilidade explícita
- **AND** não apresenta dados antigos como se fossem da revisão solicitada.

### Requirement: R4 — Entregar contexto progressivo aos agentes
O sistema SHALL expor busca, símbolo, dependências e impacto pelo workspace MCP.

#### Scenario: agente investiga uma mudança
- **WHEN** o agente consulta uma tool com snapshot e escopo válidos
- **THEN** recebe resultados paginados com localização e evidência citável
- **AND** não recebe credenciais ou detalhes internos do provider.

### Requirement: R5 — Comparar RepoWise em modo shadow
O sistema SHALL medir RepoWise contra o provider nativo sem alterar gates ou verdict durante o piloto.

#### Scenario: review no conjunto piloto
- **WHEN** o Pullwise calcula impacto para um PR piloto
- **THEN** o Gateway calcula o resultado RepoWise em paralelo
- **AND** registra divergências, latência e cobertura sem bloquear o PR.

### Requirement: R6 — Fundamentar mapas com evidência canônica
O sistema SHALL permitir que Maps consuma arquitetura e relações fixadas à revisão.

#### Scenario: gerar Architecture Delta
- **WHEN** Maps solicita a arquitetura base e head
- **THEN** recebe componentes, relações e evidências das revisões correspondentes
- **AND** preserva provider e confiança no artefato.

### Requirement: R7 — Separar fatos de memória curada
O sistema SHALL enviar ao BrainSentry apenas decisões candidatas ou feedback com proveniência.

#### Scenario: decisão inferida pelo provider
- **WHEN** um provider infere uma possível regra arquitetural
- **THEN** ela é registrada como candidata pendente de aprovação
- **AND** não vira automaticamente memória canônica.

