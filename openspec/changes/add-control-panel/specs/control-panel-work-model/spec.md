# Spec (delta) — control-panel-work-model

Modelo de trabalho do Control Panel: projeto → mudança → requisito → tarefa; máquina de estados;
projeção "onde estamos". Realiza ADR-0004, ADR-0006, RFC-0003.

## ADDED Requirements

### Requirement: R1 — Hierarquia de trabalho escopada por projeto
O sistema SHALL organizar o trabalho como projeto → mudança (change) → requisito → tarefa, com
tudo escopado por um projeto.

#### Scenario: Criar mudança dentro de um projeto
- **WHEN** um usuário cria uma mudança em um projeto
- **THEN** a mudança é vinculada àquele projeto
- **AND** a mudança nasce sem requisitos e sem tarefas, na fase inicial (`spec`)

#### Scenario: Isolamento por projeto
- **WHEN** um usuário lista mudanças de um projeto
- **THEN** apenas mudanças daquele projeto são retornadas
- **AND** mudanças de outros projetos da mesma organização não aparecem

### Requirement: R2 — Requisito como delta com cenários de aceite
O sistema SHALL representar cada requisito como um delta de tipo `ADDED`, `MODIFIED` ou
`REMOVED`, e cada requisito SHALL ter ao menos um cenário de aceite em formato WHEN/THEN.

#### Scenario: Requisito sem cenário é inválido
- **WHEN** um usuário tenta salvar um requisito sem nenhum cenário
- **THEN** o sistema rejeita o salvamento
- **AND** informa que todo requisito precisa de ao menos um cenário de aceite

#### Scenario: Requisito ADDED com cenário
- **WHEN** um usuário cria um requisito `ADDED` com um cenário WHEN/THEN
- **THEN** o requisito é salvo com o cenário associado
- **AND** o cenário inicia com `covered = false`

### Requirement: R3 — Rastreabilidade requisito↔tarefa
O sistema SHALL gerar tarefas vinculadas a um requisito de origem, e toda tarefa SHALL apontar
para o requisito que a originou.

#### Scenario: Tarefa referencia o requisito de origem
- **WHEN** uma tarefa é criada a partir de um requisito
- **THEN** a tarefa registra `requirementRef` apontando para aquele requisito
- **AND** o requisito passa a listar a tarefa entre suas `taskRefs`

#### Scenario: Navegar da tarefa ao "porquê"
- **WHEN** um usuário abre o detalhe de uma tarefa
- **THEN** o sistema exibe o requisito de origem e seus cenários de aceite

### Requirement: R4 — Máquina de estados da tarefa
O sistema SHALL manter o estado de cada tarefa em um de seis estados de board
(`a_fazer`, `em_curso`, `em_validacao`, `concluida`, `bloqueada`, `ajustes`) e SHALL permitir
apenas as transições válidas definidas em ADR-0004.

#### Scenario: Início do trabalho
- **WHEN** a tarefa está em `a_fazer` e o responsável inicia o trabalho
- **THEN** a tarefa transita para `em_curso`

#### Scenario: Abertura de PR move para validação
- **WHEN** a tarefa está em `em_curso` e um PR referenciando a tarefa é aberto
- **THEN** a tarefa transita para `em_validacao`

#### Scenario: Transição inválida é rejeitada
- **WHEN** há tentativa de mover uma tarefa diretamente de `a_fazer` para `concluida`
- **THEN** o sistema não aplica a transição
- **AND** o estado permanece `a_fazer`

### Requirement: R5 — `concluida` e `ajustes` só pelo Pass 5
O sistema SHALL atribuir os estados `concluida` e `ajustes` exclusivamente como resultado do
Pass 5; agentes e desenvolvedores SHALL NOT definir `concluida`.

#### Scenario: Agente não pode concluir
- **WHEN** um agente reporta status para uma tarefa
- **THEN** o sistema aceita apenas `em_curso` ou `implementado` (e bloqueio via tool própria)
- **AND** qualquer tentativa de definir `concluida` é rejeitada

#### Scenario: Conclusão vem da aprovação
- **WHEN** o Pass 5 aprova a tarefa
- **THEN** a tarefa transita para `concluida` e `pass5 = pass`

### Requirement: R6 — Responsável humano ou agente
O sistema SHALL permitir que cada tarefa tenha um responsável do tipo humano (nome) ou agente
(harness + modelo resolvido).

#### Scenario: Atribuir a um humano
- **WHEN** uma tarefa é atribuída a um responsável humano
- **THEN** o detalhe da tarefa exibe o responsável como pessoa

#### Scenario: Atribuir a um agente
- **WHEN** uma tarefa é atribuída a um responsável agente
- **THEN** o detalhe exibe o harness e o modelo LLM resolvido do harness

### Requirement: R7 — Projeção "onde estamos"
O sistema SHALL derivar o estado e os indicadores de progresso ("onde estamos") a partir dos
eventos, sem campos digitados à mão.

#### Scenario: Dashboard reflete eventos
- **WHEN** eventos de execução chegam para tarefas de um projeto
- **THEN** o dashboard recalcula progresso, contagem por status e bloqueios a partir dos eventos

#### Scenario: Barra "onde estamos" por mudança
- **WHEN** um usuário abre o workspace de uma mudança
- **THEN** o sistema exibe fase, progresso, próxima ação e bloqueio, todos derivados dos eventos
