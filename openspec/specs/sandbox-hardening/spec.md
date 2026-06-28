# Spec — sandbox-hardening

Verdade atual. Reduzir vazamento de segredo e poluição de commits no caminho de execução. Realiza
ADR-0007 (espelha `opentag/packages/runner/src/security.ts` e `git.ts`). Materializado a partir de
`changes/agent-work-governance`.

## Requirements

### Requirement: R1 — Allowlist e scrub de variáveis de ambiente
O sistema SHALL, ao montar o ambiente do processo do External CLI, restringir as variáveis a uma
allowlist segura e SHALL remover variáveis que casem com padrões sensíveis
(`TOKEN/SECRET/PASSWORD/API_KEY/CREDENTIAL/ANTHROPIC_/GITHUB_TOKEN`…), exceto as explicitamente
necessárias à execução.

#### Scenario: Segredo não necessário é removido
- **WHEN** o ambiente do host contém uma variável sensível não exigida pela execução
- **THEN** ela não é repassada ao processo do External CLI

### Requirement: R2 — Detecção de prompt-injection
O sistema SHALL avaliar o prompt final por padrões de injeção (override de instrução, exfiltração de
segredo, leitura de arquivos sensíveis como `~/.ssh`/`.env`) em um modo configurável
(`enforce | audit | off`). Em `audit`, SHALL registrar os achados sem bloquear; em `enforce`, SHALL
bloquear a execução e escalar.

#### Scenario: Padrão de injeção em modo audit
- **WHEN** o prompt contém um pedido de exfiltração de segredo e o modo é `audit`
- **THEN** a execução prossegue
- **AND** os achados são registrados

#### Scenario: Padrão de injeção em modo enforce
- **WHEN** o prompt contém um override de instrução e o modo é `enforce`
- **THEN** a execução é bloqueada

### Requirement: R3 — Limpeza de artefatos internos antes do commit
O sistema SHALL excluir do conjunto commitado os artefatos internos de CLIs de agente
(raízes `.claude`, `.codex`, `.omx`), de modo que não poluam o branch da tarefa, sem afetar arquivos
do usuário.

#### Scenario: Artefatos internos não são commitados
- **WHEN** o agente deixa um diretório `.claude/` no workspace após executar
- **THEN** o commit da tarefa não inclui `.claude/`
- **AND** os arquivos de código do usuário permanecem no commit
