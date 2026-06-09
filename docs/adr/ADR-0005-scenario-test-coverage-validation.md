# ADR-0005 — Cobertura cenário↔teste como critério de validação

## Status

Aceito — 2026-06-09.

## Contexto

Cenários de aceite (WHEN/THEN) já são, na prática, especificações de teste. Se a validação
conferir só "os testes passam", um time pode passar testes que não cobrem o comportamento
especificado — e o drift volta pela porta dos fundos. Precisamos amarrar **cada cenário** a
**pelo menos um teste**.

## Decisão

A validação (Pass 5) exige **cobertura cenário↔teste**: todo `Scenario` de um requisito precisa
de **≥1 teste** que o cubra (`Scenario.covered = true`). **Cenário sem teste reprova.**

Mecanismo de derivação (spec → testes):
- Cada cenário WHEN/THEN vira **um método de teste**, nomeado de forma **rastreável** (cita
  requisito + cenário). A linguagem/framework segue a stack do repo.
- A tool MCP `scaffold_tests` gera o **esqueleto** (corpo `TODO`/falha proposital) a partir dos
  cenários, para guiar o desenvolvimento. Os primeiros testes nascem do `spec.md`.
- O mapa de cobertura (`covered` por cenário) alimenta o Pass 5 (RFC-0004).

**A spec sempre manda; testes são derivados dela, nunca escritos em paralelo.**

## Alternativas consideradas

1. **Só "testes verdes".** Não garante que o comportamento especificado foi exercido. Rejeitada.
2. **Cobertura de linha/branch (%) como gate.** Mede execução de código, não conformidade com a
   spec; um cenário pode ficar sem teste mesmo com 90% de cobertura de linha. Rejeitada como
   critério principal (pode ser complementar).
3. **Cobertura cenário↔teste (escolhida).** Liga a unidade de aceite (cenário) à evidência
   (teste); rastreável e legível.

## Consequências

- **Positivas:** garante que cada comportamento aceito é exercido; testes rastreáveis ao requisito;
  o esqueleto acelera o início e evita "esquecer" cenários.
- **Custos:** exige um mapeamento confiável cenário→teste (convenção de nomes + parsing — RFC-0004);
  manter o esqueleto em sincronia quando cenários mudam.
- **Relacionado:** ADR-0004 (gate), RFC-0004 (algoritmo), capability `pass5-validation`.
