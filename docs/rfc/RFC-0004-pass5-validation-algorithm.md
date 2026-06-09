# RFC-0004 — Algoritmo do Pass 5 (cobertura, desfechos, Pullwise)

> Realiza ADR-0005 e o gate do ADR-0004. Define como o portão de conformidade confere o código
> contra os cenários de aceite e decide `concluida` vs `ajustes`.

## 1. Entradas

- O **PR** de uma tarefa (diff de código + testes).
- Os **cenários de aceite** (WHEN/THEN) dos requisitos vinculados à tarefa.
- Os **testes** do repositório e o resultado da sua execução.
- (Integração) **Pullwise** como executor/revisor do Pass 5.

## 2. Mapa de cobertura cenário↔teste

Cada cenário deve ter ≥1 teste que o cubra (`Scenario.covered`). O vínculo é por **convenção de
nome rastreável** gerada por `scaffold_tests` (RFC-0001 §4.6, ADR-0005):

```
método de teste cita requisito + cenário, ex.:
  R1_scenario_login_invalido()        // requirementRef=R1, scenario="login inválido"
```

`coverage(requirement)`:
```
total   = #cenários do requisito
covered = #cenários com ≥1 teste mapeado E presente na suíte
map     = { scenario_name -> [test_method...] }   // ✓ coberto / ✕ sem teste
```

## 3. Algoritmo

```
pass5(task):
  reqs = requisitos vinculados à task
  # (a) cobertura
  for r in reqs, for s in r.scenarios:
     if not exists test mapeado a (r, s): FAIL(reason="cenário sem teste", scenario=s)
  # (b) testes derivados passam
  run = executar a suíte (ao menos os testes mapeados aos cenários da task)
  if run tem falhas em testes mapeados: FAIL(reason="teste derivado falhando", details=run)
  # (c) conformidade comportamental (Pullwise)
  verdict = pullwise.review(pr_diff, scenarios)   # confere se o código corresponde ao WHEN/THEN
  if verdict.diverges: FAIL(reason="código diverge dos cenários", critique=verdict.critique)
  return PASS
```

A ordem é barata→cara: cobertura (estática) → testes (execução) → revisão semântica (Pullwise).
Qualquer FAIL curto-circuita com a crítica anexada.

## 4. Critérios de reprovação

1. **Cenário sem teste** (cobertura incompleta).
2. **Teste derivado falhando.**
3. **Código diverge** do comportamento descrito nos cenários (julgamento do Pullwise).

## 5. Desfechos (emitem evento — RFC-0003)

- **Aprovado:** `task.pass5 = pass`; emite `pass5_approved` → projeção leva a `concluida`.
- **Reprovado:** `task.pass5 = fail`; grava a crítica em `revise_reason`; emite `pass5_changes`
  → projeção leva a `ajustes` e **reabre** para `em_curso`.

`concluida`/`ajustes` são definidos **exclusivamente** aqui (ADR-0004). O agente/dev nunca os
define.

## 6. Gatilho

Disparado no **merge do PR** da tarefa (webhook Git → RFC-0003). Pode também ser reexecutado sob
demanda (reprocessável). Resultado é idempotente por `(task_id, pr_sha)`.

## 7. Contrato de integração com Pullwise

```jsonc
pullwise.review(input) -> verdict
// input
{ "pr": { "repo": "string", "number": number, "head_sha": "string" },
  "scenarios": [ { "requirement": "R1", "name": "string", "when": "string", "then": "string" } ] }
// verdict
{ "diverges": boolean,
  "critique": "string?",            // presente quando diverges=true
  "per_scenario": [ { "name": "string", "ok": boolean, "note": "string?" } ] }
```
A integração é **plugável** (Pullwise é o default); a interface acima isola o Control Panel do
fornecedor específico.

## 8. Itens em aberto

- Severidade/limiar do julgamento semântico (quando "diverge" o suficiente para reprovar).
- Caching do `verdict` por `pr_sha` para reexecuções.
- Política para cenários `MODIFIED`/`REMOVED` (recobrir/retirar testes correspondentes).
