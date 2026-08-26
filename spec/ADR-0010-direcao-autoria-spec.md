# ADR-0010 — Direção de autoria da spec: Git-first na fase interna

- **Status:** Proposto
- **Data:** 2026-08-10
- **Decisores:** Edson Martins · **Neimar Chagas (assinatura requerida)**
- **Relaciona-se com:** ADR-0001 (supersedido em parte), ADR-0002, ADR-0003, RFC-0002
- **Toca invariante constitucional:** sim — Princípio 2 (`CONSTITUTION.md:19-53`, "materialização híbrida"). **Exige dupla assinatura.**

---

## Contexto

`ADR-0001` (Aceito em 2026-06-09) estabelece que **o Control Panel é dono da autoria da spec** e materializa arquivos no repositório a cada versão aprovada. É a implementação do Princípio 2 da constituição: *"sem drift, sem lock-in"*.

Estado real em 2026-08-10, verificado por auditoria `[confirmado]`:

- `git grep -ril "SpecVersion\|materializ"` em `backend/src`, `frontend/src`, `client/squadx_client` → **zero hits**. Dois meses após o aceite, zero linhas de implementação.
- A change `openspec/changes/add-control-panel/` está ativa e não aplicada; último commit em `openspec/` foi 2026-06-28 — **parada há ~6 semanas**.
- O repositório tem **276 arquivos markdown / 44.194 linhas** de corpus, contra 34.984 de Java e 25.614 de Python. O corpus documental já é maior que qualquer runtime individual.
- `IntegrationWebhookController` existe no backend, mas **não alimenta estado de spec** (`[confirmado]`, RFC-0003 marcado como parcial por esta razão).

Três fatos mudaram entre junho e agosto e não estavam disponíveis quando ADR-0001 foi escrito:

1. **O corpus passou a existir.** Em junho o repositório não tinha `CONSTITUTION.md`, `openspec/` nem ADRs numerados — o bootstrap de governança foi feito naquele mesmo dia. Hoje há corpus real, escrito à mão e comitado, e esse fluxo funciona.
2. **O tenant da fase atual é a IntegrAllTech, e é um só** (`[decisão]`, ver ADR-0011). Todos os autores de spec são técnicos com acesso de escrita ao repositório.
3. **ADR-0001 foi derivado de um protótipo, não de código.** O pacote de 2026-06-09 (`PROMPT-ControlPanel-spec.md`, `SquadX-dev-Spec-Dominio.md`, `squadx-control-panel.html`) trazia aviso explícito de que o protótipo era mock client-side e não arquitetura-alvo. O aviso estava correto; a consequência é que a decisão de autoria foi tomada sem custo de implementação conhecido.

## Drivers da decisão

- **Custo de caminho crítico.** "Control Panel escreve" exige editor de spec, versionamento, fluxo de aprovação e materialização antes de qualquer tarefa derivar de qualquer decisão. Isso posterga o laço mínimo em meses.
- **Risco de drift.** O objetivo do Princípio 2 é eliminar divergência entre spec e repositório. Duas fontes de escrita é precisamente o que cria drift.
- **Reúso.** O webhook de Git já existe; falta ligá-lo.
- **Perfil dos autores na fase interna.** Cem por cento técnicos, cem por cento com Git.

## Opções consideradas

### Opção A — Ratificar ADR-0001 como está (Control Panel escreve, materializa no Git)

- ✅ Autoria acessível a não-técnicos; fluxo de aprovação nativo; UI rica de spec.
- ❌ Bloqueia o laço decisão→tarefa atrás de meses de infraestrutura de autoria.
- ❌ Duas fontes de escrita sobre os mesmos arquivos → conflito e drift a resolver.
- ❌ Nenhum autor da fase interna precisa disso.

### Opção B — Inverter: Git é dono, Control Panel lê e projeta

- ✅ O corpus já existe e já é escrito assim; custo de migração zero.
- ✅ Fonte de escrita única — drift impossível por construção, não por sincronização.
- ✅ O laço decisão→tarefa fica a três peças de distância (parser, campo de origem, portão).
- ❌ Autoria exige Git; não serve a stakeholder não-técnico.
- ❌ Sem fluxo de aprovação em UI; aprovação é o PR.

### Opção C — Git-first agora, autoria no Control Panel diferida atrás de gate

- ✅ Todos os benefícios de B na fase interna.
- ✅ ADR-0001 não é revogado, é **adiado** — a decisão de junho continua válida como alvo.
- ❌ Exige que o modelo de leitura seja desenhado sem impedir a escrita futura.

## Decisão

**Opção C.** `[decisão]`

Na fase interna, **o repositório Git é a fonte única de autoria do corpus SDD**. O Control Panel **lê** o corpus, deriva tarefas e projeta estado. Não escreve spec.

`ADR-0001` fica **supersedido em parte**: sua direção de autoria é suspensa para a fase interna; sua intenção (eliminar drift e lock-in) é preservada e, na prática, mais bem atendida — porque com uma única fonte de escrita não existe o que sincronizar.

O gate de reativação de ADR-0001 é o mesmo de ADR-0012: **o primeiro autor de spec que não tenha acesso de escrita ao repositório.** Não é uma data nem um threshold em arquivo de configuração — é um evento que uma pessoa observa.

### Restrição de desenho

O parser e o modelo de leitura **não podem** assumir que o Git é a única origem possível. A entidade `SpecVersion` do modelo de domínio (`SquadX-dev-Spec-Dominio.md`) permanece válida; muda apenas quem a produz. Uma implementação que torne a escrita futura impossível viola esta ADR.

## Consequências

**Positivas**

- O laço decisão→tarefa fica implementável em semanas, não meses.
- Drift entre spec e repositório deixa de ser risco gerenciado e passa a ser impossível.
- O corpus existente (276 arquivos) vira insumo imediato, não legado a migrar.
- Aprovação de spec reusa o mecanismo de PR, que já tem histórico, revisão e autoria.

**Negativas, assumidas**

- A tela de workspace da spec do protótipo (`squadx-control-panel.html`) fica **somente leitura** no v1. A capacidade "Gerar testes" a partir dos cenários permanece — ela lê, não escreve.
- Stakeholder não-técnico não autora spec na fase interna. Aceito: não existe stakeholder não-técnico na fase interna.
- Se o gate disparar, haverá trabalho de retrofit de autoria. Mitigado pela restrição de desenho acima.

**Riscos**

- `[inferência]` Corpus escrito à mão sem validação de esquema pode produzir markdown que o parser não consegue ler. Mitigação: o parser falha ruidosamente e o arquivo malformado aparece como erro no Control Panel, nunca é ignorado em silêncio.

## Tarefas derivadas

| # | Tarefa | Depende de |
|---|---|---|
| T-0010-1 | Definir gramática mínima de ADR/RFC parseável (front-matter YAML + seção "Tarefas derivadas") | — |
| T-0010-2 | Parser de corpus → conjunto de tarefas candidatas | T-0010-1 |
| T-0010-3 | Ligar `IntegrationWebhookController` para disparar reparse em push | T-0010-2 |
| T-0010-4 | Campo de origem na entidade `Task` (arquivo + âncora da decisão) | — |
| T-0010-5 | Portão de entrada: `Task` sem decisão de origem não entra no board | T-0010-4 |
| T-0010-6 | Reclassificar ADR-0001 para "Supersedido em parte por ADR-0010" | — |

## Referências

- `CONSTITUTION.md:19-53` — Princípio 2
- ADR-0001, RFC-0002 — corpus de 2026-06-09
- `SquadX-dev-Spec-Dominio.md` — entidades e máquina de status
- `DIAGNOSTIC-SQUADX-2026-08-10.md` §B.2, §B.4, §B.6
