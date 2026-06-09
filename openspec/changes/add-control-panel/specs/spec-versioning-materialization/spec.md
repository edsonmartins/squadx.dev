# Spec (delta) — spec-versioning-materialization

Versionamento semântico da spec e materialização no Git. Realiza ADR-0001, RFC-0002.

## ADDED Requirements

### Requirement: R1 — Versionamento semântico por mudança
O sistema SHALL manter um histórico de versões (`SpecVersion`) por mudança, registrando o delta
(`summary`), o autor e o instante de cada versão, com exatamente uma versão marcada como
`current`.

#### Scenario: Nova versão ao aprovar alterações
- **WHEN** alterações de requisitos de uma mudança são aprovadas
- **THEN** o sistema cria uma nova `SpecVersion` monotônica com `summary`, `author` e `when`
- **AND** marca a nova versão como `current` e desmarca a anterior

#### Scenario: Histórico preserva autoria
- **WHEN** um usuário abre o histórico de versões de uma mudança
- **THEN** o sistema lista as versões com quem alterou o quê e quando

### Requirement: R2 — Materialização da versão aprovada no Git
O sistema SHALL materializar, a cada versão aprovada, o change folder (markdown OpenSpec) no
repositório por meio de um commit, e SHALL registrar a referência do commit na `SpecVersion`.

#### Scenario: Versão aprovada vira commit
- **WHEN** uma versão é aprovada e materializada
- **THEN** o sistema grava `proposal.md`, `design.md`, `tasks.md` e `specs/<capability>/spec.md`
- **AND** cria um commit e registra seu sha em `SpecVersion.commit`

#### Scenario: PR carrega spec + código
- **WHEN** a materialização ocorre na branch da mudança onde o código também é desenvolvido
- **THEN** o PR exibe spec e código no mesmo diff

### Requirement: R3 — Materialização determinística e idempotente
O sistema SHALL renderizar o change folder de forma determinística, de modo que materializar a
mesma versão duas vezes não produza um novo commit.

#### Scenario: Rematerializar a mesma versão é no-op
- **WHEN** `materialize_change` é chamada novamente para uma versão já materializada sem mudanças
- **THEN** o sistema detecta diff vazio
- **AND** retorna o commit já existente sem criar um novo

### Requirement: R4 — Sem lock-in (markdown no repo)
O sistema SHALL manter a spec materializada como markdown no formato OpenSpec dentro do
repositório, de forma legível e versionada pelo Git.

#### Scenario: Spec legível fora do painel
- **WHEN** um usuário inspeciona `openspec/` no repositório
- **THEN** encontra a spec da mudança em markdown, sem depender do Control Panel para lê-la

### Requirement: R5 — Detecção de conflito de materialização
O sistema SHALL detectar quando os arquivos sob seu controle divergiram no repositório e SHALL
sinalizar o conflito em vez de sobrescrever cegamente.

#### Scenario: Divergência sinalizada
- **WHEN** a materialização encontra, na branch, alterações inesperadas em arquivos que o painel
  controla
- **THEN** o sistema registra um conflito de materialização
- **AND** não sobrescreve as alterações automaticamente
