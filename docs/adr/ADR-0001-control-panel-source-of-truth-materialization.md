# ADR-0001 — Control Panel como fonte de verdade + materialização híbrida no Git

## Status
Aceito — 2026-06-09.  
**Reclassificado 2026-08-26 (T-0011-3 do ADR-0011):** spec vigente do Control Panel, em implementação — backend em `backend/src/main/java/dev/squadx/controlpanel/` e telas em `frontend/src/app/(dashboard)/{changes,validations,harnesses}`. Não confundir com código descontinuado.


## Contexto

No fluxo atual de desenvolvimento, a discussão vive no chat, as tarefas no board e o código no
repositório — três lugares desconectados. O contexto se perde: tarefas chegam órfãs, sem vínculo
com o requisito que as originou, e o "porquê" some. O SquadX.dev Spec quer que a **especificação
seja a unidade de trabalho**: requisitos com cenários de aceite geram as tarefas, e o estado
reflete a execução real.

O risco clássico de uma ferramenta que "é dona" da spec é duplo:
- **Drift**: a spec na ferramenta diverge do código no repositório.
- **Lock-in**: a spec fica presa num banco proprietário; o time perde a portabilidade.

## Decisão

O **Control Panel é o dono da autoria e do versionamento semântico da spec** (quem mudou qual
requisito, quando e por quê). Porém, a cada **versão aprovada**, ele **materializa** os arquivos
da spec **no próprio repositório Git** via commit (ver RFC-0002). O PR de uma mudança carrega
**spec + código no mesmo diff**, e o **Git é o registro reconciliado**.

O Control Panel é a **única ponta que escreve a spec** — nas duas direções (no seu modelo
versionado e no repositório) — de modo que elas **não divergem por construção**. A spec
materializada continua sendo **markdown que o time possui** (formato OpenSpec em `openspec/`),
sem lock-in.

## Alternativas consideradas

1. **Spec só no banco do Control Panel.** Simples, mas reintroduz lock-in e drift (o código no
   Git não enxerga a spec vigente). Rejeitada.
2. **Spec só no Git, editada à mão (sem painel).** Portável, mas perde o versionamento semântico
   por requisito, a rastreabilidade requisito→tarefa e a autoria assistida. Rejeitada.
3. **Materialização híbrida (escolhida).** Painel é dono da autoria/versionamento; Git é o
   registro materializado e reconciliado. Combina rastreabilidade + portabilidade.

## Consequências

- **Positivas:** sem drift (uma fonte escreve nas duas pontas); sem lock-in (markdown no repo);
  PR audita spec + código juntos; "porquê" sempre a um clique do requisito.
- **Custos:** é preciso um mecanismo de materialização confiável (RFC-0002) e idempotente; o
  Control Panel precisa de credenciais/escopo para commitar; conflitos de materialização
  precisam de política (resolvida no RFC-0002).
- **Relacionado:** ADR-0002 (estado como projeção), ADR-0006 (onde isso roda na stack).
