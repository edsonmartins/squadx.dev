# ADR-0011 — Escopo do SquadX v2: preservar, descartar, construir

- **Status:** Proposto
- **Data:** 2026-08-10
- **Decisores:** Edson Martins · Neimar Chagas
- **Relaciona-se com:** ADR-0010, ADR-0012, ADR-0006, `documentos/ARQUITETURA-RUNTIME.md`

---

## Contexto

O SquadX.dev foi concebido como produto de mercado ("AI Development Squads as a Service"). A auditoria de 2026-08-10 estabeleceu o estado real `[confirmado]`:

- **866 arquivos rastreados**, primeiro commit 2026-02-09, **250 de 250 commits de autor único**.
- Backend Spring: **33 controllers**, ~36 mil linhas, incluindo billing/Stripe, calendário, reuniões, gravações, highlights, white-label, templates de equipe, notificações e autopilot.
- `client/` (daemon Python): 28 mil linhas, **338 toques em 90 dias** — o componente mais quente do repositório.
- `mobile/` parado há ~3,5 meses; `desktop/` parado há ~5 meses com **54 linhas de Rust** em 3 arquivos.
- Billing: backend ativo com webhook público (`SecurityConfig.java:63`), **sem nenhuma UI** — `src/lib/api.ts` tem 22 objetos `*Api` e nenhum de billing.
- CI de `main` vermelho desde 2026-07-30 por 3 erros de import-sort; registry defasado.

**Decisão de estratégia de produto** `[decisão]`: o SquadX será **desenvolvido, testado e consolidado dentro da IntegrAllTech** e só depois levado ao mercado — mesma trajetória de SquadX Live, SquadX Maps e PullWise.ai, e a mesma que originou Mentors IPaaS.

Consequência direta: **a metade SaaS do backend responde a uma pergunta que não está sendo feita agora.** Ela não é código ruim — é monetização e multi-tenancy construídas antes da validação, e hoje compete por atenção e manutenção com o componente que realmente evolui.

Consequência segunda: a funcionalidade que **falta** é justamente o Control Panel — "consolidar ADRs/RFCs/Spec, tarefas derivam disso, nada é feito sem mudança de decisão". Isso já está especificado no corpus de 2026-06-09 (ADRs 0001–0006, RFCs 0001–0004, `SquadX-dev-Spec-Dominio.md`, protótipo navegável). Não precisa ser reespecificado.

## Drivers da decisão

- Superfície desproporcional a um autor único (bus factor = 1, nenhuma revisão independente jamais ocorreu no código de segurança).
- Manutenção, CI e auditoria de licença incidem sobre código sem uso.
- Superfície de ataque sem contrapartida (webhook Stripe público sem produto).
- O corpus de junho descreve exatamente a funcionalidade pendente.

## Decisão

`[decisão]` O SquadX v2 é definido por três colunas. **"Reescrever o SquadX" não é o verbo correto** — o verbo é descartar a metade que responde à pergunta errada e preservar a que responde à certa.

### Preservar — não tocar sem ADR próprio

| Ativo | Justificativa |
|---|---|
| `client/` **integralmente** | 744 testes verdes; refatorado ativamente em julho; é o produto |
| Egress sidecar (`egress_sidecar.py`, `network_policy.py`, DNS proxy com ipset, política por squad) | Default-deny com falha fechada em três pontos. Ativo raro, bem construído |
| `test_architecture_guards.py` | Padrão que impede um controle de segurança de voltar a ficar morto e silencioso. **Replicar nos demais produtos da IntegrAllTech** |
| Loop LangGraph de 8 nós + 7 especialistas | `graph.py:72-128` |
| `hardening.py`, `lifecycle.py`, `sandbox/paths.py` | Inclusive o que nunca foi instanciado — o código está certo, falta ligar |
| Corpus de 2026-06-09 (ADRs 0001–0006, RFCs 0001–0004) | **Não é corpus morto. É a spec vigente do Control Panel, não implementada** |
| `SquadX-dev-Spec-Dominio.md` | Modelo de domínio autoritativo: entidades, máquina de status, schemas MCP, regras de Pass 5 |
| `squadx-control-panel.html` | Referência de UX. **Não é decisão técnica** (aviso original preservado) |
| Backend: `ExecutionController`, `WebSocketEventService`, `RunAdmissionService`, `TaskController`, `AuthController`/RBAC, `StompSubscriptionAuthorizer` | Núcleo do control plane + execution dispatch |
| Postgres + Flyway (V1–V37), Redis | Persistência |
| `DIAGNOSTIC-SQUADX-2026-08-10.md` | Registro histórico. É a evidência de por que o v2 existe |

### Descartar

| Item | Ação | Justificativa |
|---|---|---|
| Billing / Stripe (`BillingController`, `Subscription`, `V8`, `stripe-java`) | Remover ou **feature flag off por default**; webhook público **desligado** | Backend vivo, produto morto. Mercado vem depois |
| Calendário + Google Calendar API | Remover | Não serve a uso interno |
| Reuniões, gravações (S3), highlights, `AiAnalysisService` | Remover | Idem |
| White-label (`BrandController`, `BrandConfig`, `V14`) | Remover | Um tenant não tem marca a customizar |
| Templates de equipe | Remover | Idem |
| `mobile/` (Expo) | **Arquivar** — sai do escopo de manutenção e de auditoria de licença | Parado há 3,5 meses |
| `desktop/` (Tauri) | **Arquivar** | 54 linhas de Rust; parado há 5 meses |
| `documentos/KanbanBoard.tsx` | Remover | Componente React solto em pasta de docs |
| Fontes de verdade paralelas em `documentos/` | Consolidar em um único doc ou promover a ADR | Três documentos declaram precedência própria e conflitante |

**Regra de descarte:** migrações Flyway aplicadas **não são removidas** — novas migrações fazem `DROP` explícito, preservando a sequência. Código removido sai em commit próprio, referenciando esta ADR.

### Construir

O laço mínimo decisão→tarefa, conforme ADR-0010 e o modelo de domínio de junho. Detalhamento em RFC próprio (T-0011-6).

## Critério de saída da fase interna

`[decisão]` Sem este critério, "consolidar" vira estado permanente — o modo de falha conhecido do dogfooding.

> A fase interna termina quando o SquadX tiver entregue, em produção real da IntegrAllTech, **N tarefas fechadas por agente aprovadas em revisão humana sem retrabalho — medidas, não estimadas.**

O valor de N fica em aberto nesta ADR e deve ser fixado na sessão de decisão T-000 (T-0011-1). Medir isso exige instrumentação que hoje não existe (auditoria forense de execução — blocker B14) e é a mesma métrica necessária para vender depois.

## Consequências

**Positivas**

- Superfície de manutenção cai de 33 controllers para ~12.
- Atenção concentrada no `client/` e no Control Panel.
- Auditoria de licença e CI incidem só sobre código vivo.
- Webhook público sem produto deixa de existir.

**Negativas, assumidas**

- Retrabalho futuro se o SquadX for a mercado com billing próprio. Aceito: reconstruir billing sobre um produto validado é mais barato que manter billing sobre um produto não validado.
- Perda de trabalho já feito (calendário, reuniões, gravações). Aceito conscientemente.
- `[inferência]` Acoplamento JPA `Task → Project → Organization` atravessa quase toda entidade — a remoção pode ter efeito cascata maior que o estimado. Mitigação: remover um domínio por vez, com CI verde entre cada um.

**Riscos**

- **Reescrever o corpus antes de escrever código.** É o modo de falha de junho, com mais convicção. O padrão que funcionou no próprio repositório foi ADR-0007/0008/0009 — decisão pequena, escrita na semana da implementação. Esta ADR **proíbe** produzir CONSTITUTION + ADRs + RFCs + OpenSpec completos para o v2 antes de haver código correspondente.
- **Mudar paradigma e código ao mesmo tempo.** Se o motor decisão→tarefa for construído sobre um backend em reescrita, não será possível distinguir "a disciplina não funciona" de "o código não está pronto". Mitigação: **o `client/` fica de pé como está e é o primeiro alvo do motor.**

## Tarefas derivadas

| # | Tarefa | Prioridade |
|---|---|---|
| T-0011-1 | Sessão de decisão T-000: fixar N do critério de saída; decidir disposição de `mobile/`, `desktop/`, billing, `squad-maps/`; ausência de `LICENSE` apesar do badge MIT | P0 |
| T-0011-2 | `ruff check --fix` → CI de `main` verde; republicar imagens | P0 |
| T-0011-3 | Reclassificar ADRs 0001–0006 de "Aceito" para "Aceito — não implementado" com nota de vigência | P0 |
| T-0011-4 | Corrigir `CLAUDE.md`: `@PreAuthorize` existe nos controllers; `validateUserAccess` está em 23 de 41 services; Python é 3.11 no CI | P0 |
| T-0011-5 | Remover domínios descartados, um commit por domínio, CI verde entre cada | P1 |
| T-0011-6 | RFC do laço mínimo decisão→tarefa | P1 |
| T-0011-7 | Fechar `IN_REVIEW → DONE` (`TaskStatusTransition.java:15-20`) — contradiz ADR-0004 | P1 |
| T-0011-8 | Instrumentar taxa de sucesso por tarefa (insumo do critério de saída) | P2 |

## Referências

- `DIAGNOSTIC-SQUADX-2026-08-10.md` — íntegro
- Corpus de 2026-06-09: ADRs 0001–0006, RFCs 0001–0004, `SquadX-dev-Spec-Dominio.md`
- ADR-0010 (autoria), ADR-0012 (isolamento)
