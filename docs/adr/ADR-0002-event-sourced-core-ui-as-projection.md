# ADR-0002 — Núcleo event-sourced; UI/estado como projeção

## Status

Aceito — 2026-06-09.

## Contexto

O "onde estamos" precisa ser **sempre coerente** com a execução real. Se o status das tarefas
for um campo digitado à mão, ele descola da realidade (alguém esquece de atualizar, ou mente).
A execução acontece em duas pistas — humano (IDE, commits, PR) e agente (sessão no harness) — e
ambas emitem sinais: **webhooks de Git** e **eventos MCP**.

## Decisão

O núcleo do Control Panel é **orientado a eventos**. O estado de uma tarefa (e os dashboards) é
uma **projeção** de uma sequência append-only de eventos, não um dado editável. As fontes de
evento são:
- **Webhooks de Git** (branch criada, PR aberto, merge) — pista humana.
- **Eventos MCP** (`update_task_status`, `report_blocker`, etc.) — pista do agente.
- **Resultados do Pass 5** (aprovado / ajustes) — portão de validação.

A UI **observa** a execução; o painel **não é dono do código**. Transições de estado derivam de
eventos segundo a máquina de estados (ADR-0004, RFC-0003). Toda transição é auditável (LGPD).

## Alternativas consideradas

1. **Estado mutável digitado.** Simples, mas drift garantido e sem trilha confiável. Rejeitada.
2. **Event sourcing "puro"** (event store dedicado, CQRS completo, snapshots agressivos).
   Poderoso, mas pesado para o estágio atual e estranho à stack em camadas. Adotamos o
   **princípio** (estado = projeção de eventos persistidos) sem o maquinário pesado: eventos
   persistidos em tabela append-only + projeção materializada na própria tarefa (ADR-0006).
3. **Núcleo event-driven leve (escolhido).** Eventos persistidos e ordenados por tarefa;
   projeção determinística; reprocessável. Equilíbrio entre coerência e simplicidade.

## Consequências

- **Positivas:** "onde estamos" é derivado e reprodutível; auditoria nativa; as duas pistas
  convergem no mesmo modelo; reprocessar eventos reconstrói o estado.
- **Custos:** exige idempotência e ordenação por tarefa na ingestão (RFC-0003); duplicação de
  webhook/evento precisa ser tolerada; a projeção precisa ser determinística.
- **Relacionado:** ADR-0004 (transições), ADR-0001 (materialização), RFC-0003 (ingestão).
