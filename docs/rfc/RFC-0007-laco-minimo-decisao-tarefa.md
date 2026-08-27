# RFC-0007 — Laço mínimo decisão → tarefa (Git-first, ADR-0010)

> Realiza o **laço mínimo decisão→tarefa** (ADR-0011 T-0011-6, ADR-0010 Opção C) e as tarefas
> T-0010-1..5. Define como uma decisão registrada como ADR/RFC (ou change OpenSpec) no
> repositório vira **tarefa rastreável** no Control Panel, sem o sistema escrever spec (fase
> interna, Git é fonte única de autoria). Fecha em `concluida` pelo Pass 5 (RFC-0004, ADR-0004).

## 1. Objetivo

Transformar "decisão escrita" → "tarefa derivada" → "execução (humano/agente)" → "concluída", de
forma **mensurável por agente** (ADR-0011 T-0011-8) e **sem drift**: a tarefa carrega a âncora
(arquivo + linha) da decisão que a originou, e só entra no board se tiver origem (T-0010-5).

A autoria é **Git-only** nesta fase (ADR-0010): o Control Panel lê o corpus e projeta estado;
não escreve spec. `SpecVersion` do modelo de domínio continua válida — quem a produz é o Git
(commit), não o painel.

## 2. Gramática mínima de decisão parseável (T-0010-1)

Só o que é necessário para derivar tarefas; nada mais. Um documento de decisão (ADR/RFC/change)
é parseável se tiver:

- **front-matter YAML** (aberto por `---`):
  ```yaml
  ---
  id: ADR-0012
  status: Proposto | Aceito | Supersedido
  data: 2026-08-10
  ---
  ```
- Seção semântica **`## Tarefas derivadas`** com tabela de tarefas:
  ```markdown
  ## Tarefas derivadas
  | # | Tarefa | Prioridade |
  |---|---|---|
  | T-0011-1 | Descrição da tarefa | P0 |
  ```
  Colunas mínimas: **id**, **título**, **prioridade** (opcional). Qualquer ADR/RFC que tenha a
  seção `## Tarefas derivadas` vira fonte; quem não tem, não produz tarefas (é só decidido).

### Comportamento do parser (T-0010-2)

```
parse(arquivo):
  se front-matter inválido           → TAREFA_DESCARTADA(id, erro="front-matter")  # ruído, não silêncio
  se falta seção "## Tarefas derivadas" → nenhuma tarefa deste arquivo
  senão:
     para cada linha de tarefa válida:
        TarefaCandidata(
          origem = { arquivo, ancoragem: linha/âncora do header da seção },
          titulo, idTarefa, prioridade
        )
```

- Parser **falha ruidosamente**: arquivo markdown malformado aparece como erro visível no
  Control Panel, nunca é ignorado em silêncio (ADR-0010 §Riscos).
- Re-parse é **idempotente**: mesmas âncoras ⇒ mesmas tarefas candidatas (dica de chave de
  deduplicação: `origem.arquivo + "#" + idTarefa`).

## 3. Campo de origem na tarefa (T-0010-4)

A entidade `Task` ganha um par orientado a origem da decisão:

```
Task.origin {
  source_ref : string   // ex.: "docs/rfc/RFC-0007-laço-mínimo.md#T-0011-6"
  source_kind: enum     // ADR | RFC | CHANGE | NONE
}
```

- `source_ref` é o vínculo estável à decisão; é o que garante rastreabilidade e impede dublety
  (dois parses do mesmo arquivo não criam tarefas duplicadas).
- Tarefas de outro sistema (ex.: `TaskController` clássico) podem ter `source_kind = NONE`.

## 4. Portão de entrada (T-0010-5)

Regra de admissão no board do Control Panel:

```
entra_no_board(task):
  return task.source_ref != null        // tem decisão de origem
      && task.status != CANCELADO
```

- A tarefa `NONE` (sem origem) **não** entra no board spec-native — ela é gerida pelo fluxo
  clássico (`Task`) fora do laço decisão→tarefa.
- O portão vive no serviço que materializa tarefas a partir do parse (`TaskMaterializationService`),
  não em controller — segue a convenção de authz no service layer (CLAUDE.md).

## 5. Fluxo do laço (fim a fim)

```
[push no repo] ──webhook Git──▶ IntegrationWebhookController (T-0010-3)
        │  dispara reparse
        ▼
   Parser de corpus ──▶ lista de TarefaCandidata (idempotente)
        │  para cada candidata não existente
        ▼
   TaskMaterializationService
        │   cria Task com origin.source_ref
        │   (portão T-0010-5: sem origem não entra)
        ▼
   [Board spec-native]
   a_fazer → em_curso → em_validacao → concluida
        │                                    ▲  ajustes ─┐
        └── agente/humano reporta ───────────┘           │
           Pass 5 (RFC-0004): aprovado=concluida, reprovado=ajustes──┘
```

- **Autoria**: humano ou agente (ADR-0003/0004).
- **Conclusão**: exclusivamente Pass 5 (ADR-0004) → `concluida`.
- **Métrica**: cada `Task` concluída fica rastreável ao agente executor; a taxa por agente é
  exposta por `ExecutionService.getOrganizationMetrics().agents[].success_rate` (T-0011-8,
  já implementado no RFC-0007/commit `c9571ce`).

## 6. Reavaliação em push

- A cada push, re-parse de **arquivos de decisão alterados** (deltas), não o corpus inteiro —
  desempenho e idempotência.
- Mudou o título/prioridade de uma tarefa de decisão já materializada → a materialização
  **atualiza** a Task se a âncora for a mesma (upsert); nunca cria duplicata.

## 7. Fora de escopo (fase interna)

- Autoria de spec no Control Panel (ADR-0001, **adiado** — reativação por evento, ver ADR-0010
  §gate).
- Fluxo de aprovação de spec em UI (aprovação = PR do Git).
- Editor rico de spec (a tela do workspace fica somente leitura; "Gerar testes" lê cenários,
  não escreve).

## 8. Critérios de aceite

1. ADR/RFC com `## Tarefas derivadas` válida gera exatamente uma Task por linha, com `source_ref`
   apontando arquivo+id.
2. Mesmo arquivo parseado duas vezes não duplica tasks (mesma âncora ⇒ mesmo registro).
3. Task sem `source_ref` não entra no board spec-native (portão).
4. Front-matter inválido ou markdown malformado produz erro visível (não é ignorado).
5. Push de arquivo de decisão alterado re-parseia apenas o delta e faz upsert.
6. Tarefa só alcança `concluida` via Pass 5 (ADR-0004); retrabalho = `ajustes → em_curso`.
7. A taxa de sucesso por agente é computável via metrics da org (T-0011-8).

## 9. Componentes afetados

- **Backend**: `controlpanel` (parser de corpus, `TaskMaterializationService`, campo `Task.origin`,
  `IntegrationWebhookController`), `repository`.
- **Frontend**: workspace da change (somente leitura) — já parcialmente presente
  (`/changes/[id]`), passa a consumir `origin`.
- **Client**: sem mudanças de contrato relevante nesta fase.

## Referências

- ADR-0010 (Opção C, Git-first), ADR-0011 (T-0011-6, critério de saída)
- ADR-0001 (adiado), ADR-0004 (Pass 5), RFC-0002 (materialização), RFC-0003 (estado), RFC-0004 (Pass 5)
- `SquadX-dev-Spec-Dominio.md` §4 (versionamento), §1 (entidades)
- T-0011-8 realizado (taxa por agente) — `ExecutionService.getOrganizationMetrics`
