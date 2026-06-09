# SquadX.dev Spec — Modelo de domínio e comportamentos

> Referência destilada do protótipo (`squadx-control-panel.html`). Descreve **domínio e
> comportamento** de forma agnóstica de tecnologia. **Não** dita arquitetura: a stack real
> vem do repositório do SquadX.dev. Precedência em caso de divergência:
> **repositório > "Contexto de design" do prompt > este documento > protótipo > proposta.**
> Identificadores em inglês; rótulos de UI em português.

## 1. Entidades

- **Project** — `id`, `name`, `client`. Tudo é escopado por projeto.
- **Change** (mudança / change folder OpenSpec) — `id`, `module`, `phase`
  (`spec` | `implementação` | …), `versions[]`, `requirements[]`, `tasks[]`.
- **SpecVersion** — `version` (ex.: `v3`), `current` (bool), `summary` (o delta daquela
  versão), `author`, `when`, `commit` (ref do commit em que foi **materializada** no repo).
- **Requirement** — `id` (ex.: `R1`), `type` (`ADDED` | `MODIFIED` | `REMOVED`), `title`,
  `description`, `scenarios[]`, `taskRefs[]`.
- **Scenario** (cenário de aceite) — `name`, `when` (condição/Given+When), `then`
  (resultado esperado), `covered` (bool: existe teste cobrindo).
- **Task** — `id` (ex.: `2.1`), `title`, `requirementRef`, `status`, `assignee`,
  `events[]`, `commits[]`, `pass5` (`pending` | `pass` | `fail`), `blockerReason?`,
  `reviseReason?` (crítica do Pass 5 quando reprovada).
- **Assignee** — `kind` (`human` | `agent`).
  - human: `name`, `avatar`.
  - agent: `harness` (ref), `model` (resolvido do harness).
- **Harness** — `key`, `name`, `vendor`, `status` (`conectado` | `disponível`),
  `model` (escolhido), `models[]` (disponíveis). Ex.: Claude Code, Codex, Gemini CLI, Cursor.
- **Event** — `when`, `type`/`status`, `source` (`Claude Code` | `Codex` | `Pullwise · Pass 5`
  | `Dev + IDE` | …). Eventos são a base de tudo — o estado é **projeção** deles.

## 2. Máquina de status

Estados do **board** (visíveis):

| status (interno) | rótulo (UI) | significado |
|---|---|---|
| `a_fazer` | A fazer | não iniciada |
| `em_curso` | Em execução | dev ou agente trabalhando |
| `em_validacao` | Em validação | PR aberto; Pass 5 conferindo |
| `concluida` | Concluída | aprovada no Pass 5 |
| `bloqueada` | Bloqueada | impedida; sempre com motivo |
| `ajustes` | Ajustes necessários | reprovada no Pass 5; volta à execução com a crítica |

Vocabulário **só de evento** (não é estado de board): `implementado` — a *afirmação* do
agente/dev de que terminou de codar. Aparece no histórico de execução; quem move a tarefa
para `em_validacao` é a abertura do PR.

Transições válidas:

```
a_fazer ─▶ em_curso ─▶ (implementado, evento) ─▶ em_validacao ─▶ concluida
                                                       │
                                                       └─▶ ajustes ─▶ em_curso (reabre)
qualquer estado ativo ◀─▶ bloqueada (com motivo)
```

Regras de autoria do estado:
- O agente/dev reporta `em_curso` e `implementado`; pode reportar `bloqueada` (com motivo).
- A abertura do PR leva a `em_validacao`.
- **`concluida` e `ajustes` são definidos exclusivamente pelo Pass 5.** O agente/dev nunca
  marca `concluida`. É isso que impede o drift spec↔código.

## 3. Contrato do MCP server `workspace`

Contrato harness-agnóstico: Claude Code, Codex, Gemini CLI, Cursor consomem o mesmo. Schemas
ilustrativos (formalizar no RFC do MCP server):

```
get_change(change_id)
  → { id, proposal, phase, requirements:[{id,type,title,scenarios:[{name,when,then}]}],
      tasks:[{id,title,requirementRef,status}] }

get_tasks(change_id)
  → [{ id, title, requirementRef, status }]

update_task_status(task_id, status, note?)        // status ∈ {em_curso, implementado}
  → { ok, task_id, status }

report_blocker(task_id, reason)
  → { ok, task_id, status:"bloqueada" }

materialize_change(change_id)                      // grava o change folder no repo
  → { ok, change_id, version, commit }

scaffold_tests(change_id | requirement_id)         // gera esqueleto a partir dos cenários
  → { class_name, file, methods:[{scenario_name, method_name}], coverage:{total, covered} }
```

Notas de comportamento:
- O agente abre a sessão chamando `get_change`/`get_tasks` (briefing), trabalha, e reporta
  cada item concluído via `update_task_status` — **uma chamada por tarefa, na ordem em que
  conclui** (é isso que mantém o "onde estamos" em tempo real).
- `materialize_change` é chamada na **aprovação de uma versão da spec**: commita o change
  folder e devolve o `commit` que alimenta o `SpecVersion`.

## 4. Versionamento e materialização

- O Control Panel é dono da **autoria e do versionamento** da spec (histórico semântico por
  requisito: quem mudou o quê, quando).
- A cada versão aprovada, a spec é **materializada no repositório** (commit). O PR carrega
  spec + código no mesmo diff. O Git é o registro reconciliado.
- O painel é a **única** ponta que escreve a spec nas duas direções → sem divergência.
  Sem lock-in: a spec continua sendo markdown no repo.

## 5. Validação (Pass 5)

- Entradas: o PR de uma tarefa, os cenários de aceite do requisito e os testes.
- **Cobertura obrigatória:** todo cenário de aceite precisa de ≥1 teste cobrindo
  (`Scenario.covered`). Cenário sem teste **reprova**.
- Desfechos:
  - aprovado → `task.status = concluida`, `pass5 = pass`.
  - reprovado → `task.status = ajustes`, `pass5 = fail`, com a crítica em `reviseReason`,
    e a tarefa reabre para `em_curso`.
- Critérios de reprovação: (1) cenário sem teste; (2) teste derivado falhando; (3) código
  diverge do comportamento descrito nos cenários.

## 6. Spec → testes

- Cada `Scenario` (WHEN/THEN) vira **um método de teste**, nomeado de forma rastreável e
  citando requisito + cenário. A linguagem/framework segue a stack do repo (no protótipo,
  JUnit 5).
- `scaffold_tests` gera o esqueleto (corpo `TODO`/falha proposital) para guiar o
  desenvolvimento; os primeiros testes nascem do `spec.md`, os demais conforme tasks/design.
- O mapa de cobertura (`covered` por cenário) alimenta o Pass 5. **A spec sempre manda;
  testes são derivados dela, nunca escritos em paralelo.**

## 7. Duas pistas de execução

- **Humano:** trabalha no IDE; branch/commit/PR referenciam o `id` da tarefa. O status
  avança por **webhook de Git** (branch → `em_curso`; PR aberto → `em_validacao`;
  merge → dispara Pass 5).
- **Agente:** sessão no harness escolhido, briefing injetado via MCP, status reportado via
  MCP. Harness **plugável**; **modelo LLM escolhido pelo usuário** por harness.
- O workspace **observa** a execução (não é dono do código). Estado e dashboards são
  **projeção** dos eventos (webhooks + MCP) — núcleo orientado a eventos.

## 8. Mapa de telas (comportamento de UX)

- **Dashboard (por projeto):** métricas (progresso, concluídas, em andamento, bloqueios),
  distribuição por status (barra + contagem de todos os estados), lista de mudanças com
  progresso, atividade ao vivo (feed de eventos).
- **Specs / Workspace (por mudança):** barra "onde estamos" (fase, progresso, próxima ação,
  bloqueio); histórico de versões da spec com o commit materializado; requisitos
  (ADDED/MODIFIED) → tarefas vinculadas; ação **Gerar testes** por requisito.
- **Tarefas:** visão **lista** e **kanban** (coluna por status); filtro por
  Todos / Humanos / IA / pessoa-agente.
- **Detalhe da tarefa:** briefing (descrição + cenários WHEN/THEN + restrições); histórico de
  execução (eventos); card de execução do agente (harness + modelo); portão Pass 5; commits
  referenciando o id; ação contextual (abrir sessão no harness / abrir no IDE / reabrir com a
  crítica / resolver bloqueio).
- **Conectores:** card por harness com status e **seletor de modelo LLM**; cada um fala MCP
  com o workspace.
- **Validação · Pass 5:** fila de validação (resultado + crítica); cobertura cenário→teste
  (✓ coberto / ✕ sem teste); critérios de reprovação.

---

*Mantenha este documento em sincronia com o protótipo: se o comportamento mudar lá, atualize
aqui (ou regenere a partir do protótipo).*
