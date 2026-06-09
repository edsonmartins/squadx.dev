# Spec (delta) — execution-tracking

Ingestão das duas pistas (webhooks Git + eventos MCP) e projeção do estado. Realiza ADR-0002,
RFC-0003.

## ADDED Requirements

### Requirement: R1 — Ingestão de webhooks de Git (pista humana)
O sistema SHALL ingerir webhooks de Git e traduzir branch/PR/merge em eventos de tarefa,
associando-os à tarefa pela referência ao seu `id` no nome de branch/commit/PR.

#### Scenario: Branch inicia a tarefa
- **WHEN** chega um webhook de criação de branch referenciando o `id` de uma tarefa
- **THEN** o sistema registra um evento `started` para aquela tarefa

#### Scenario: PR aberto move para validação
- **WHEN** chega um webhook de PR aberto referenciando o `id` de uma tarefa
- **THEN** o sistema registra um evento `pr_opened`
- **AND** a projeção transita a tarefa para `em_validacao`

#### Scenario: Merge dispara validação
- **WHEN** chega um webhook de merge do PR de uma tarefa
- **THEN** o sistema dispara o Pass 5 para aquela tarefa

### Requirement: R2 — Ingestão de eventos MCP (pista do agente)
O sistema SHALL transformar chamadas MCP (`update_task_status`, `report_blocker`) em eventos de
tarefa.

#### Scenario: Evento a partir do reporte do agente
- **WHEN** um agente chama `update_task_status(task_id, "implementado")`
- **THEN** o sistema registra um evento `implemented` para a tarefa
- **AND** a projeção não altera o estado de board só por isso

### Requirement: R3 — Estado como projeção determinística
O sistema SHALL derivar o estado da tarefa aplicando os eventos ordenados por ocorrência,
segundo a máquina de estados, de forma reprodutível.

#### Scenario: Reprocessar eventos reconstrói o estado
- **WHEN** a projeção é recomputada a partir de todos os eventos de uma tarefa
- **THEN** o estado resultante é idêntico ao estado corrente

#### Scenario: `implementado` não conclui sozinho
- **WHEN** existe um evento `implemented` mas nenhum `pass5_approved`
- **THEN** a tarefa não está em `concluida`

### Requirement: R4 — Idempotência da ingestão
O sistema SHALL descartar eventos duplicados com base em uma chave de deduplicação derivada de
(fonte, referência, tipo, tarefa).

#### Scenario: Webhook reentregue não duplica
- **WHEN** o mesmo webhook de Git é entregue duas vezes
- **THEN** o sistema registra o evento apenas uma vez

### Requirement: R5 — Tolerância a fora de ordem
O sistema SHALL produzir o estado correto mesmo que eventos cheguem fora de ordem, ordenando por
instante de ocorrência na projeção.

#### Scenario: Evento atrasado não corrompe o estado
- **WHEN** um evento `started` chega depois de um `pr_opened` já processado
- **THEN** a projeção, ordenando por ocorrência, mantém o estado coerente com a máquina de estados

### Requirement: R6 — Trilha de auditoria das transições
O sistema SHALL manter os eventos de forma append-only, servindo de trilha de auditoria das
transições de estado.

#### Scenario: Auditar transições de uma tarefa
- **WHEN** um usuário consulta o histórico de execução de uma tarefa
- **THEN** o sistema lista os eventos que produziram cada transição, com fonte e instante
