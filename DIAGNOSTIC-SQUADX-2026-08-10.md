# DIAGNÓSTICO SQUADX.DEV — 2026-08-10

> Auditoria de estado real, **read-only**. Nenhum arquivo do repositório foi criado, editado ou
> removido; nenhum comando de escrita (`commit`, `checkout`, `install`, `migrate`) foi executado.
> Único artefato gerado: este arquivo, na raiz do workspace (`/mnt/user-data/outputs/` não existe
> neste ambiente — verificado com `ls -d /mnt/user-data/outputs` → `No such file or directory`).
>
> Comandos executados foram de leitura (`git log/grep/ls-files`, `grep`, `cat`, `wc`, `docker info`,
> `docker images`, `docker ps`, `gh run list`, `gh pr list`) mais **dois** que executam código já
> presente no repo sem alterar estado: `pytest` (client, com o `.venv` já instalado) e
> `ruff check` (lint). Ambos são idempotentes e não escrevem no repo.

**Tags de proveniência:** `[confirmado]` = li no código/config e aponto arquivo:linha ·
`[inferência]` = deduzido de sinal indireto, explicitado · `[não encontrado]` = procurado e ausente.

---

## Sumário Executivo

1. **O que o SquadX é hoje, de fato:** um monorepo de **plataforma fim-a-fim**, não um execution
   plane isolado. O backend Spring Boot (283 arquivos Java em `main`) carrega Kanban, Billing
   (Stripe), RBAC/SSO, white-label, calendário, reuniões e gravações — tudo ativo e roteado
   (`backend/src/main/java/dev/squadx/controller/`, 33 controllers). O runtime de agentes é o
   `client/` Python (28k linhas): um **daemon que roda no host do usuário**, recebe tarefas por
   STOMP e cria sandboxes Docker irmãos via o socket Docker do host.
   **Não encontrei nenhuma evidência no repositório do re-escopo descrito no prompt** — nenhum ADR,
   RFC, issue ou doc declara a saída de Kanban/billing/RBAC para um Control Plane separado; ao
   contrário, `CONSTITUTION.md:9-17` e `openspec/project.md:8-14` afirmam explicitamente que o
   produto tem "duas faces" e que o Control Panel é um **novo bounded context sobre** o runtime.
2. **Qual é a real camada de isolamento em produção:** **Docker + runc, hardened**, mais um sidecar
   privilegiado de egress que dona a netns do agente. gVisor e Firecracker existem apenas como
   *strings de configuração* (`hardening.py:69-78`) — nenhum host testado os tem, e o auto-upgrade
   por threshold declarado em config **nunca é lido** (admitido por escrito em
   `client/tests/test_architecture_guards.py:66-71`). O host de execução hoje é um Mac mini M4 com
   Colima (VM ARM64, Ubuntu 24.04, runc, 2 vCPU / 3.8 GiB) — **sem `/dev/kvm`, sem virtualização
   aninhada**: Firecracker é inviável neste host, hoje.
3. **Três blockers mais graves:**
   - **P0-1** — o daemon roda como membro do grupo `docker` no host (root-equivalente:
     `client/deploy/squadx-client.service:19` + comentário em `:13-15`) e monta um diretório do host
     como `/workspace` rw no container do agente (`sandbox_start.py:75-81`). Um escape de container
     ou um bug de path entrega o host inteiro. Não há segunda barreira.
   - **P0-2** — as chaves de LLM do operador entram **dentro do sandbox** como env de exec
     (`daemon.py:407-415` → `sandbox_exec.py:53`), num container que executa código gerado por LLM.
     Não há proxy de credencial. Exfiltração de chave depende só de o allowlist de egress permitir
     um destino gravável — e o allowlist **precisa** permitir as APIs de LLM.
   - **P0-3** — nenhum isolamento entre tenants no plano de execução: um daemon serve todas as
     tarefas do usuário que o registrou, o VNC do sandbox sobe **sem senha** (`start-agent.sh:94`,
     `-nopw`) e o warm pool (opt-in) recicla containers entre tarefas com `stop`/`start`, sem limpar
     o filesystem, todos apontando para o **mesmo** diretório de workspace do host
     (`pool.py:379`, `config.py:138-140`).

**Veredito direto:** o estado atual do isolamento **não sustenta um cliente pagante executando
código de terceiros**. Sustenta um piloto single-tenant onde o operador confia no próprio código e
nas próprias chaves — que é exatamente o que o repositório declara ter homologado
(`documentos/PILOTO-ESCOPO.md:13-24`: GO local, **NO-GO** para staging).

**O que ficou de fora e por quê:** não instalei `frontend/node_modules` (ausente — verificado), logo
não rodei `vitest`/`tsc`/`playwright`; não rodei o build do backend (Maven exigiria download de
dependências, escrita em `target/` e JDK 21). Cobertura desses runtimes foi avaliada por contagem
de arquivos + status do CI remoto. `mobile/` e `desktop/` foram inventariados mas não auditados em
profundidade (parados há 3–5 meses). A ordem de prioridade pedida (D → F → M → E → C → H → B) foi
seguida na profundidade da investigação.

---

## A. Inventário

### A.1 Repositórios e atividade

| Item | Valor | Evidência |
|---|---|---|
| Repositórios | **1** (monorepo), sem submódulos | `cat .gitmodules` → `No such file`; `find . -maxdepth 3 -name .git` → só `./.git` `[confirmado]` |
| Remote | `git@github.com:edsonmartins/squadx.dev.git` | `git remote -v` `[confirmado]` |
| Branch atual | `main` | `git branch` `[confirmado]` |
| Último commit | `b0063a8` — 2026-08-04 18:09 -0300 | `git log -1` `[confirmado]` |
| Primeiro commit | 2026-02-09 — "Initial commit - SquadX.dev Platform" | `git log --reverse` `[confirmado]` |
| Commits em `main` | 187 | `git rev-list --count HEAD` `[confirmado]` |
| Commits nos últimos 90 dias | 140 (75% do total) | `git log --since='90 days ago' --oneline \| wc -l` `[confirmado]` |
| Branches remotas | 50 | `git branch -r \| wc -l` `[confirmado]` |
| Autor dominante | Edson Martins — 250/250 commits (221 + 29 sob grafia alternativa). **Autor único.** | `git shortlog -sn --all` `[confirmado]` |

**Working tree sujo na hora da auditoria:** 18 arquivos modificados e 10 caminhos não rastreados,
incluindo `backend/src/main/java/dev/squadx/observability/` (novo, não commitado),
`docs/OBSERVABILITY.md` e `squad-maps/` inteiro. `git status --short` `[confirmado]`.
Isso significa que parte da observabilidade descrita adiante **existe só no disco do dev**.

### A.2 Árvore e volume (apenas arquivos rastreados pelo git)

| Diretório | Arquivos | Linhas | Último commit | Status |
|---|---:|---:|---|---|
| `backend/` (Spring Boot) | 403 | 36.809 | 2026-08-04 | **ativo** |
| `client/` (daemon Python) | 155 | 28.087 | 2026-08-04 | **ativo** (338 toques em 90d — o mais quente) |
| `frontend/` (Next.js) | 137 | 27.393 | 2026-08-04 | **ativo** |
| `infra/` (helm, k8s, nginx, monitoring) | 45 | 2.318 | 2026-07-29 | ativo |
| `scripts/` | 9 | 1.429 | 2026-07-30 | ativo |
| `mobile/` (Expo) | 17 | 2.008 | **2026-04-28** | **parado (~3,5 meses)** |
| `desktop/` (Tauri) | 14 | 397 | **2026-03-20** | **parado (~5 meses)** |
| `platform/` (compose unificado) | 2 | 422 | 2026-08-04 | ativo |
| `documentos/` (docs informais) | 34 arquivos `.md`+assets | — | 2026-07-30 | ativo |
| `openspec/` | 29 commits | — | 2026-06-28 | **parado (~1,5 mês)** |
| `spec/` (Control Panel) | 4 | — | 2026-06-09 | **parado (2 meses)** |
| `squad-maps/` | **não rastreado** | — | — | vendorizado, fora do git |

Total git-tracked: **866 arquivos** (`git ls-files | wc -l`) `[confirmado]`.

**LOC por linguagem** (`cloc`/`tokei`/`scc` não instalados — verificado; usado `git ls-files` + `wc -l`):

| Linguagem | Arquivos rastreados | Linhas |
|---|---:|---:|
| Java | 354 | 34.984 |
| Python | 132 | 25.614 |
| TypeScript `.tsx` | 73 | 12.304 |
| TypeScript `.ts` | 42 | 7.048 |
| Markdown | 276 | 44.194 |
| YAML/YML | 139 | 21.114 |
| SQL | 39 | 1.326 |
| Shell | 16 | 1.865 |
| Rust (`desktop/`) | 3 | **54** |

> **Sinal:** 44 mil linhas de markdown para 35 mil de Java e 26 mil de Python. O corpus documental é
> maior que qualquer runtime individual. `[confirmado]`
> **Sinal 2:** `desktop/` tem 54 linhas de Rust em 3 arquivos — é um esqueleto Tauri, não um produto.

**Commits por diretório nos últimos 90 dias** (`git log --since='90 days ago' --name-only`):
`client` 338 · `backend` 181 · `frontend` 88 · `docs` 42 · `documentos` 38 · `openspec` 29 ·
`infra` 20 · `scripts` 10. **`mobile` e `desktop`: zero.** `[confirmado]`

---

## B. Corpus SDD vs. realidade

### B.1 `CONSTITUTION.md` — existe, 8 invariantes

`CONSTITUTION.md` `[confirmado]`. Declara precedência:
**repositório (código) > CONSTITUTION > ADRs > RFCs > OpenSpec > `documentos/`** (`:3-5`).

Princípios, literais (`:19-53`):

1. **A spec é a fonte de verdade.** Requisitos como deltas ADDED/MODIFIED/REMOVED com cenários
   WHEN/THEN; rastreabilidade bidirecional.
2. **Sem drift, sem lock-in (materialização híbrida).** Control Panel dona da autoria da spec;
   materializa arquivos no repositório a cada versão aprovada.
3. **Estado é projeção de eventos.** Derivado de webhooks Git + eventos MCP.
4. **`concluída` só pela validação (Pass 5).** Estado terminal nunca definido pelo agente nem pelo dev.
5. **Cobertura cenário↔teste é obrigatória.** Cenário sem teste reprova.
6. **Contrato único e harness plugável.** Harnesses falam o mesmo contrato MCP.
7. **Humanos e agentes são cidadãos de primeira classe.** Duas pistas de execução.
8. **Reúso da stack existente.** Control Panel é bounded context **sobre** o runtime, não reescrita.

**Aderência real:** dos 8 princípios, **1, 2, 3, 4, 5 e 6 não têm nenhuma implementação**
(ver B.2). O princípio 7 é parcial (Task tem `assigned_agent` e `assignedSquad`, mas não há pista de
"humano via webhook Git"). O 8 é o único plenamente respeitado — porque nada foi construído.

### B.2 ADRs — decisão × implementação

| # | Título | Status declarado | Implementado? | Evidência |
|---|---|---|---|---|
| 0001 | Control Panel como fonte de verdade + materialização no Git | Aceito (2026-06-09) | **não implementado** | `git grep -ril "SpecVersion\|materializ" -- backend/src frontend/src client/squadx_client` → **zero hits** `[confirmado]` |
| 0002 | Núcleo event-sourced; UI como projeção | Aceito (2026-06-09) | **não implementado** | Não há event store; `backend/.../event/DomainEvent.java` é evento Spring in-process, não sourcing. Estado é lido de tabelas JPA (`TaskService`, `ExecutionService`) `[confirmado]` |
| 0003 | MCP `workspace` como contrato único de harness | Aceito (2026-06-09) | **não implementado** | `client/squadx_client/mcp/` contém **apenas `__pycache__`** — nenhum `.py` rastreado (`git ls-files client/squadx_client/mcp/` → vazio). O único uso é um import defensivo que loga `mcp_bridge_absent_skipping_report_blocker` (`orchestrator/nodes.py:768-773`) `[confirmado]` |
| 0004 | Máquina de estados com Pass 5 como único caminho para "concluída" | Aceito (2026-06-09) | **contradito pelo código** | `TaskStatusTransition.java:15-20` permite `IN_REVIEW → DONE` sem portão de validação; `git grep -i pass5` → zero hits. O agente/daemon fecha a task via `handleTaskCompleted` (`ExecutionService.java:490+`) `[confirmado]` |
| 0005 | Cobertura cenário↔teste como critério de validação | Aceito (2026-06-09) | **não implementado** | Nenhum validador de cobertura cenário↔teste; `git grep -ril "pass_5\|scenario.*coverage"` → zero `[confirmado]` |
| 0006 | Control Panel como bounded context na stack | Aceito (2026-06-09) | **não implementado** | Nenhum pacote/tabela de Control Panel; `openspec/changes/add-control-panel/` continua como *change* não aplicada (não está em `archive/`) `[confirmado]` |
| 0007 | Governança de trabalho de agentes | **Proposto** (2026-06-27) | **parcial** | Implementado: Attention Budget (`ExecutionLog.java:23-28`, `V33__add_execution_log_visibility.sql`), Run Admission (`RunAdmissionService.java`, `V34__add_run_admission.sql`), Context Packet (`client/squadx_client/orchestrator/context_packet.py`, 122 linhas). Sandbox-hardening de policy: parcial (ver D) `[confirmado]` |
| 0008 | Enforcement de egress no nível de rede | Aceito; header diz "Fase 1 implementada e ligada por default (2026-07-17)" | **implementado** | Sidecar: `docker/egress_sidecar.py`, `docker/network_policy.py` (384 linhas), imagem `client/docker/egress-proxy.Dockerfile`; default ON em `config.py:97`. Fase 0 host-side: `docker/egress_guard.py` `[confirmado]` |
| 0009 | Runtime de sandbox pluggable | Aceito (2026-07-30) | **parcial** | `SandboxBackend` existe (`sandbox/protocol.py`, `factory.py`). DOCKER e PROCESS `implemented=True`; **FIRECRACKER e REMOTE `implemented=False`** e levantam `SandboxNotSupportedError` (`sandbox/factory.py:66-84,135-139`) `[confirmado]` |

**Leitura:** os 6 ADRs "Aceitos" de 2026-06-09 (todo o corpus do Control Panel) somam **zero linhas
de implementação** 2 meses depois. Os 3 ADRs que geraram código são exatamente os de runtime
(0007/0008/0009) — e o único ainda marcado "Proposto" (0007) é o mais implementado dos três em
backend. O status declarado dos ADRs **não é um indicador confiável** do estado do código.

### B.3 RFCs

| # | Título | Implementado? | Evidência |
|---|---|---|---|
| 0001 | Contrato do `workspace` MCP server | **não implementado** | Ver ADR-0003 acima `[confirmado]` |
| 0002 | Versionamento + materialização spec→commit | **não implementado** | zero hits `[confirmado]` |
| 0003 | Máquina de estados de tarefas + ingestão de eventos | **parcial** | Máquina de estados existe (`model/vo/TaskStatusTransition.java`); ingestão de eventos Git **não** (`IntegrationWebhookController` existe mas não alimenta estado de spec) `[confirmado]` |
| 0004 | Algoritmo do Pass 5 | **não implementado** | zero hits `[confirmado]` |
| 0005 | RunEvent (visibility/importance) + Run Admission | **implementado** | `client/squadx_client/messaging/run_event.py`, `backend/.../RunAdmissionService.java`, `RunAdmissionAction`/`ReasonCode` enums, `V34` `[confirmado]` |
| 0006 | Egress firewall sidecar | **implementado** | Ver D.4 `[confirmado]` |

### B.4 OpenSpec

- `openspec/specs/` materializadas (4): `attention-budget`, `context-packet`, `run-admission`,
  `sandbox-hardening` — todas vindas da change **arquivada** `agent-work-governance`
  (`openspec/changes/archive/agent-work-governance/`) `[confirmado]`.
- `openspec/changes/add-control-panel/` — change **ativa, não aplicada**, com 6 specs
  (`control-panel-work-model`, `pass5-validation`, `execution-tracking`, `workspace-mcp-server`,
  `harness-connectors`, `spec-versioning-materialization`). Nenhuma tem código correspondente
  `[confirmado]`. Último commit em `openspec/`: 2026-06-28 → **change parada há ~6 semanas**.

### B.5 `CLAUDE.md`

Existe (`CLAUDE.md`, 7.492 bytes). Instrui: vertical slice por recurso; `validateUserAccess` em todo
método de service; **"não há `@PreAuthorize` nos controllers"**; DTO JSON snake_case; Flyway
sequencial; TanStack Query + Zustand; `parseWithFallback` com zod.

**Divergências verificadas:**

- `[confirmado]` **`@PreAuthorize` existe nos controllers**, ao contrário do que `CLAUDE.md` afirma:
  `RbacController.java:34,49` usa `@PreAuthorize("@permissionChecker.check(...)")`.
- `[confirmado]` `validateUserAccess`/`OrganizationAccessGuard` **não** está em todos os services:
  18 dos 41 services não têm nenhuma chamada (contagem por `grep -c`). Em vários casos a checagem
  migrou para o controller (`CostController.java:40`) ou para `@PreAuthorize` — o que é defensável,
  mas contraria a regra escrita e torna a auditoria por convenção não confiável.
- `[confirmado]` `CLAUDE.md` diz "Python 3.12"; `client/pyproject.toml:10` exige `>=3.11` e o CI roda
  **3.11** (`.github/workflows/ci.yml:12`). O `.venv` local é 3.12.

### B.6 Fontes de verdade paralelas (fora do corpus SDD)

Sim, e são muitas. `documentos/` tem **34 arquivos** que ancoram comportamento, alguns
contraditórios entre si `[confirmado]`:

| Arquivo | Ancora o quê | Conflito |
|---|---|---|
| `documentos/DECISAO-ARQUITETURAL-SANDBOXING.md` | Fases Docker→gVisor→Firecracker com gatilhos ("100+ exec/dia", "SOC 2 / multi-tenant"). Data: Fevereiro 2026, "APROVADO PARA IMPLEMENTAÇÃO" | Os gatilhos existem em `config.py:121-125` mas **nunca são lidos** |
| `documentos/ARQUITETURA-RUNTIME.md` | Topologia alvo k8s + host Docker. Declara precedência própria: "código > este doc > ARCHITECTURE.md (mais antigo/marketing)" | Diagrama ainda mostra **Supabase Realtime** para sinalização WebRTC — removido do código em 2026-08-04 |
| `documentos/PILOTO-ESCOPO.md` | Veredito GO/NO-GO de homologação. "vivo" | Declara precedência própria também |
| `documentos/ARCHITECTURE.md` | Auto-declarado "mais antigo/marketing" pelo doc acima | — |
| `documentos/THREAT-MODEL.md` | Modelo de ameaça referenciado por código (`StompSubscriptionAuthorizer.java:21` cita "threat-model #4") | — |
| `README.md` (20 KB) | Promessas de produto e roadmap com checkboxes | Ver seção L |
| `documentos/KanbanBoard.tsx` | **Um componente React solto dentro de `documentos/`** | Código morto em pasta de docs |

Há ainda **três** documentos que declaram sua própria precedência de forma independente
(`CONSTITUTION.md:3`, `ARQUITETURA-RUNTIME.md:6`, `PILOTO-ESCOPO.md:7`) — o que na prática significa
que **não há uma hierarquia documental efetiva**.

---

## C. Stack real (extraída das fontes autoritativas)

### C.1 Runtimes e frameworks

| Componente | Fonte | Versão | Pin? |
|---|---|---|---|
| Java | `backend/pom.xml:22` | **21** | fixo |
| Spring Boot | `backend/pom.xml:11` | **3.4.1** | fixo |
| Spring AI | `backend/pom.xml:27` | 1.1.4 | fixo |
| JobRunr | `backend/pom.xml:26` | 7.3.2 | fixo |
| MapStruct / Lombok | `backend/pom.xml:25,24` | 1.6.3 / 1.18.36 | fixo |
| Stripe Java | `backend/pom.xml:162` | 26.1.0 | fixo |
| AWS SDK S3 | `backend/pom.xml:138` | 2.25.0 | fixo |
| Node | `frontend/package.json:7` | `>=20` | range |
| Next.js | `frontend/package.json` | **16.3.0** | **fixo** |
| React | idem | `^19.0.0` | range |
| TypeScript | idem | `^5.7.2` | range |
| pnpm | `packageManager` | 9.15.0 | fixo |
| Python | `client/pyproject.toml:10` | `>=3.11` (CI: 3.11; venv local: 3.12) | range |
| Rust/Tauri | `desktop/` | 3 arquivos, 54 linhas | esqueleto |

**Lockfiles:** `frontend/pnpm-lock.yaml` existe (referenciado em `ci.yml:96`). O client **não tem
lockfile** — `pyproject.toml` usa só ranges (`langgraph>=0.2.0,<1.0`, `litellm>=1.50.0` sem teto).
`[confirmado]` Isso significa que **builds do client não são reprodutíveis**: um `pip install` hoje
e outro amanhã podem trazer LiteLLM diferentes. As únicas deps pinadas são as de dev
(`ruff==0.15.22`, `mypy==2.3.0`, `dnslib==0.9.25`) — e o comentário no `pyproject.toml:66-70`
explica que foram pinadas justamente porque a falta de pin quebrava o CI.

### C.2 SDK de orquestração de agentes — o que é realmente importado e chamado

**LangGraph**, confirmado em execução, não só declarado:

- `client/squadx_client/orchestrator/graph.py:6` — `from langgraph.graph import END, StateGraph`
- `graph.py:72-128` — `create_orchestrator()` monta um `StateGraph` com 8 nós
  (`analyze → plan → execute → review → arbiter → {commit | escalate | error}`) e o compila.
- **LiteLLM** para roteamento de provider: `client/squadx_client/llm/router.py`, suporta
  OpenAI/Anthropic/Google/OpenRouter (`router.py:35-39,125-131`).
- **LangChain** entra apenas como camada de tools (`@tool` em `agents/tools.py:32,61,91,116,145,181`)
  e pelo `ChatLiteLLM` do `langchain_community` — pinado a `<1.0` com comentário explicando que
  LangChain 1.x quebra o client em runtime (`pyproject.toml:16-18`).

`[não encontrado]` OpenHands SDK, CrewAI, AutoGen, Agno, ou qualquer outro framework de agentes.
`[confirmado]` `graph.compile()` é chamado **sem `checkpointer`** (`graph.py:128`) → sem
persistência de estado do grafo (ver E.4).

O backend também tem seu próprio caminho de LLM: `spring-ai-openai` (`pom.xml:197`) usado por
`AiAnalysisService.java` (resumos de execução, descrição de PR) e `DirectAgentChatService.java`.
São **dois** clientes de LLM independentes no produto, com contas de custo separadas.

### C.3 Dependências copyleft (AGPL/GPL)

**`[não encontrado]` nenhuma dependência AGPL ou GPL.** Verificado varrendo os 139 `*.dist-info` do
`.venv` do client por `License`/`Classifier: License`:

| Pacote | Licença | Risco |
|---|---|---|
| `certifi` 2026.5.20 | MPL-2.0 | baixo (copyleft **fraco**, por arquivo; não contamina) |
| `pathspec` 1.1.1 | MPL-2.0 | baixo |
| `orjson` 3.11.9 | MPL-2.0 AND (Apache-2.0 OR MIT) | baixo |
| `tqdm` 4.68.2 | MPL-2.0 AND MIT | baixo |
| `av` / `aiortc` / `pylibsrtp` | BSD-3-Clause | nenhum |
| `litellm` / `langgraph` | MIT | nenhum |

**Ressalva honesta:** `av` declara BSD-3, mas **empacota binários FFmpeg**, cuja licença efetiva
depende de como foi compilado (LGPL-2.1+ normalmente; GPL se com `--enable-gpl`). Não consegui
verificar o build embarcado (`.venv/.../av.libs` não existe no wheel macOS ARM instalado — o
diretório não foi encontrado). **`[inferência, não verificado]` — item para due diligence jurídica
antes de distribuir a imagem do client.**

`frontend/node_modules` **não está instalado** (verificado), então **não auditei licenças de npm**.
`squad-maps/node_modules` está no disco mas não rastreado no git; `squad-maps/LICENSE` é MIT com
copyright de terceiro ("tt-a1i (Archify)") — código **vendorizado de outro projeto**, fora do
controle de versão. `[confirmado]`

### C.4 Dependências possivelmente abandonadas

Sem acesso a registry, não posso confirmar datas de release. Sinais indiretos:

- `sockjs-client@^1.6.1` (`frontend/package.json`) — 1.6.1 é de 2021/2022 `[inferência: versão
  estável há anos, projeto em manutenção mínima]`. É dependência **crítica** — todo o transporte
  STOMP do frontend passa por ele.
- `react-kanban-kit@0.0.2-beta.5` — **versão beta pré-1.0 pinada exata**, sustentando a feature
  central de Kanban `[confirmado no manifesto]`.
- `Node 20` está declarado deprecated pelo próprio GitHub Actions nos logs do CI
  (`gh run view 30951211478` → 4 anotações de deprecação) `[confirmado]`.
- `actions/setup-java@v4` idem, "deprecated and will no longer receive updates" `[confirmado]`.

---

## D. Camada de isolamento e execução do agente — **SEÇÃO CRÍTICA**

### D.1 O caminho completo: tarefa → execução → resultado

| # | Elo | Arquivo:linha | O que acontece |
|---|---|---|---|
| 1 | Backend decide despachar | `ExecutionService.java:409-417` `dispatchTaskAssignment` | Monta payload e envia **para o e-mail do usuário que iniciou a execução** |
| 2 | Payload da tarefa | `ExecutionService.java:421-450` `buildTaskAssignmentPayload` | Campos: `task_id`, `title`, `description`, `project_id`, `agent_type`, `runtime_kind`, `cli_provider`, `execution_id`, **`sandbox_egress_policy`** |
| 3 | Envio STOMP | `WebSocketEventService.java:92-103` | `convertAndSendToUser(username, "/queue/tasks", …)` |
| 4 | Daemon recebe | `client/squadx_client/daemon.py:257-303` `_handle_task_assigned` | Valida (`_validate_task_data:25-56`), checa limite de concorrência, cria task asyncio |
| 5 | Roteamento de runtime | `daemon.py:330-349` | `smoke` \| `EXTERNAL_CLI` (`_run_external_cli_task`) \| `NATIVE` (LangGraph) |
| 6 | Credenciais montadas | `daemon.py:407-416` | `ANTHROPIC_API_KEY` / `OPENAI_API_KEY` / `GOOGLE_API_KEY` no `exec_env`, passando por `scrub_env(allow=…)` |
| 7 | Sessão de sandbox | `daemon.py:425-432` → `sandbox/factory.py:143-164` `create_sandbox_session` | Resolve backend (`docker` por default) |
| 8 | Start do container | `docker/sandbox_start.py:33-120` `start_agent_sandbox` | Sidecar de egress → container do agente → VNC/live |
| 9 | Criação do container | `docker/manager.py:123-253` `create_container` | Aplica hardening; junta netns do sidecar |
| 10 | Execução de comandos | `docker/sandbox_exec.py:20-84` / `:87-146` | `docker exec` com `environment=exec_env` |
| 11 | Tools do agente | `agents/tools.py:33` `execute_bash`, `:62` `write_file`, `:146` `run_python`, `:182` `install_dependencies` | Todas via `sandbox.execute*` |
| 12 | Resultado volta | `daemon.py:466-486` | branch/commit via `git rev-parse` no sandbox + tokens/custo |
| 13 | Teardown | `daemon.py:487-488` `finally: await sandbox.stop()` | **`stop()`, não `cleanup()`** — ver D.7 |

### D.2 Mecanismo de isolamento hoje, em execução

**Docker + `runc`.** `[confirmado]`

- Default do daemon: `SQUADX_SANDBOX_BACKEND=docker` (`config.py:113`) e
  `SQUADX_SANDBOX_RUNTIME=docker` (`config.py:119`).
- `resolve_runtime()` (`hardening.py:478-508`) só escolhe gVisor/Firecracker se o binário
  (`runsc` / `firecracker-containerd`) existir no `PATH` (`detect_available_runtimes:454-475`).
- No host auditado: `docker info` → `Runtimes: io.containerd.runc.v2 runc` / `Default Runtime: runc`.
  **Nenhum `runsc`.** `[confirmado]`
- gVisor/Firecracker no código são apenas dois dicionários de uma linha (`{"runtime": "runsc"}` /
  `{"runtime": "firecracker"}` — `hardening.py:69-78`). Não há provisioning, teste de integração,
  nem host que os tenha. **É scaffold, como o próprio README admite (`README.md:340-341`: "Scaffold").**

### D.3 Flags de hardening realmente aplicadas

Perfil `standard` (default: `ContainerConfig.enable_hardening=True`, `security_level="standard"` —
`manager.py:39-40`), montado em `SecurityConfig.to_docker_kwargs()` (`hardening.py:128-178`):

| Controle | Valor | Evidência | Observação |
|---|---|---|---|
| `--user` | `1000:1000` (não-root) | `hardening.py:96` | ✅ |
| `--read-only` | `True` | `hardening.py:99`, `:324` | ✅ |
| `--cap-drop` | `ALL` | `hardening.py:106` | ✅ nenhuma cap adicionada (`cap_add=[]`, `:107`) |
| `--security-opt no-new-privileges` | `true` | `hardening.py:148-149` | ✅ |
| seccomp | perfil inline JSON | `hardening.py:180-208` | ✅ `defaultAction: SCMP_ACT_ERRNO`, **320** syscalls em allow (contados via `json.load` de `client/docker/seccomp/agent.json`) |
| AppArmor | **não aplicado por default** | `hardening.py:216-224` | ❌ Comentário no código admite: o default anterior nomeava um perfil inexistente e nunca funcionou; hoje só se o operador setar `SQUADX_APPARMOR_PROFILE` **e** carregar o perfil no host |
| `--pids-limit` | 256 (standard) / 128 (maximum) | `hardening.py:122`, `:349` | ✅ |
| memória / swap | `2g` / `2g` (swap desabilitado) | `hardening.py:119-120` | ✅ |
| CPU | `cpu_quota = 2.0 × 100000` | `hardening.py:141-142` | ✅ |
| tmpfs | `/tmp` 100M e `/run` 10M, ambos `noexec,nosuid,nodev` | `hardening.py:100-103` | ✅ |
| user namespace remap | **`[não encontrado]`** | `grep -rn "userns"` no client → zero hits | ❌ **root do container = root do host.** Combinado com `--user 1000`, mitiga mas não elimina |
| `--pid` (namespace de PID do host) | `pid_mode=None` → namespace próprio | `hardening.py:126,175-176` | ✅ |

**Falha silenciosa notável e bem tratada:** se o perfil seccomp não carregar,
`_resolve_seccomp_opt()` retorna `None` e **loga WARNING explícito** dizendo que o container roda com
o seccomp *default* do Docker (`hardening.py:201-208`). Isso é o comportamento correto — degrada
ruidosamente. Mas degrada.

**Falha real e grave:** o perfil `development` (`hardening.py:296-313`) desliga `read_only`,
`no_new_privileges` **e seccomp**, e liga rede em bridge. Ele é selecionável por
`ContainerConfig.security_level` — que é uma **string livre** (`manager.py:40`), não um enum
validado no ponto de configuração.

### D.4 Rede e egress

**O socket Docker do host NÃO é montado dentro do container do agente.** `[confirmado]` — resposta
direta à pergunta: **não**.

Varredura completa: `git grep -n "docker\.sock\|/var/run/docker"` retorna 17 hits, **nenhum** no
caminho de criação do container do agente. Os hits são:
- `docker-compose.yml:166` — montado **somente** no `promtail` (`:ro`), sob o profile `monitoring`.
- `client/squadx_client/cli/doctor.py:137-138,183-184` e 5 scripts de instalação — apenas
  *descoberta* do socket do Colima para o `DOCKER_HOST` do daemon (que roda no host).
- `manager.py:174-241` monta `container_kwargs` sem nenhum bind de socket.

**Egress — arquitetura real (RFC-0006 / ADR-0008 Fase 1):**

O agente é `cap-drop ALL`, então iptables *dentro* dele é impossível. A solução implementada é um
**sidecar privilegiado que dona a network namespace**:

- Sidecar criado com `cap_add=["NET_ADMIN"]`, `network_mode="bridge"`, `no-new-privileges`
  (`docker/egress_sidecar.py:41-51`).
- Agente entra na netns do sidecar: `network_mode=container:<sidecar_id>`
  (`egress_sidecar.py:56-58`, aplicado em `manager.py:236-241`).
- Política default-deny + allowlist por domínio, com **DNS proxy** que pina respostas permitidas num
  `ipset` com TTL de 3600s (`network_policy.py:164-182`; imagem em `client/docker/egress-proxy.Dockerfile`).
- Separação agente/proxy dentro da mesma netns é feita por `-m owner --uid-owner`
  (`network_policy.py:168-173`) — o proxy alcança o DNS upstream, o agente não.
- Ligado por **default**: `egress_sidecar_enabled=True` (`config.py:97`),
  `egress_fail_open=False` (`config.py:99`).
- Política vem do backend, por squad: `sandbox_egress_policy` no payload
  (`ExecutionService.java:448`, `resolveEgressPolicy:463-471`, migração `V36__add_squad_sandbox_policy.sql`).
  Nunca retorna null — cai em `AGENT_DEFAULT`.
- Fase 0 host-side (bloqueio de metadata cloud `169.254.169.254` / `169.254.170.2` na chain
  `DOCKER-USER`): `docker/egress_guard.py`, best-effort, com log alto se não conseguir aplicar
  (`egress_guard.py:11-19`).
- Imagem `squadx/egress-proxy:latest` **existe no host auditado** (`docker images` → 133MB)
  `[confirmado]`.

**Isso é a parte mais bem construída do sistema.** Falha fechada em três pontos distintos
(`sandbox_start.py:151-157`, `:184-188`, `:190-193`).

**Mas o egress NÃO é irrestrito nem hermético — é allowlist, e o allowlist precisa incluir as APIs
de LLM.** Ou seja: existe, por construção, um canal de saída autorizado para um endpoint que aceita
POST de texto arbitrário. Isso é o que torna D.5 um problema estrutural, não um bug.

**Caminhos onde o egress fica aberto (todos logam em ERROR, mas seguem):**

1. `SQUADX_EGRESS_SIDECAR=false` → `sandbox_start.py:94-101` loga
   `egress_unenforced … the agent has UNRESTRICTED network access` e **continua a execução**.
2. VNC ligado sem sidecar → `manager.py:208-217` **rebaixa `network=none` para `bridge`** e loga
   `egress_open_bridge … this agent has UNRESTRICTED egress`. Como `enable_vnc` é `True` por default
   (`config.py:79`) e `enable_network` é `False`, este é o caminho que roda se alguém desligar o
   sidecar sem desligar o VNC.
3. Backend PROCESS: `process_network` default é **`allow`** (`config.py:115`,
   `sandbox/process/isolator.py:23-30`) → **rede do host, sem filtro nenhum**.
4. `xt_set` ausente no kernel do host → o script injetado aborta sob `set -e` com DROP default em
   vigor → o run falha fechado (documentado em `egress-proxy.Dockerfile:15-17`). Correto.

### D.5 Credenciais — entram **dentro** do sandbox

**`[confirmado]` As chaves de LLM são injetadas no container. Não há proxy de credencial.**

```
daemon.py:406-416
    exec_env["ANTHROPIC_API_KEY"] = settings.anthropic_api_key
    exec_env["OPENAI_API_KEY"]    = settings.openai_api_key
    exec_env["GOOGLE_API_KEY"]    = settings.google_api_key
    exec_env = scrub_env(exec_env, allow=("ANTHROPIC_API_KEY","OPENAI_API_KEY","GOOGLE_API_KEY"))
→ daemon.py:432   sandbox.start(exec_env=exec_env)
→ sandbox_start.py:55   sandbox._exec_env = dict(exec_env or {})
→ sandbox.py:285,304 → sandbox_exec.py:53,119   environment=exec_env
```

O que **foi** feito bem: as chaves são **env de exec**, não env de create. O comentário em
`sandbox.py:71-77` explica corretamente que env de create fica visível em `docker inspect` e
`/proc/1/environ` pela vida inteira do container. Isso é uma mitigação real e bem pensada.

O que **não** foi resolvido: no momento em que o comando roda, `ANTHROPIC_API_KEY` está no ambiente
do processo dentro do container. Qualquer código que o agente gerar e executar lê `os.environ`. A
única barreira remanescente é o allowlist de egress — que precisa permitir `api.anthropic.com`.
`[confirmado]` **Um agente comprometido por prompt injection pode ler a chave e enviá-la ao próprio
endpoint permitido.**

Há uma mitigação parcial no caminho External CLI: `cli_security_mode="enforce"` por default
(`config.py:88`), que aborta o run em findings de severidade `block` (instruction-override,
secret-exfiltration, credential-file-read) — `client/squadx_client/agents/security.py` (179 linhas).
É detecção heurística de prompt injection, não uma fronteira de credencial.

`[não encontrado]` Nenhuma injeção de credencial Git/GitHub no sandbox — o commit é feito
localmente no worktree (`git rev-parse` em `daemon.py:470-472`); não achei push automatizado com token.

### D.6 Filesystem montado do host

**`[confirmado]` Exatamente um bind mount, rw:**

```python
# docker/sandbox_start.py:75-81
volumes={ sandbox.workspace_path: {"bind": "/workspace", "mode": "rw"} }
```

`sandbox.workspace_path` vem de `daemon.py:391`:
`task_data.get("project_path") or settings.workspace_path`.

**Análise do risco de path arbitrário:**
- `[confirmado]` O backend **não envia `project_path`** — `git grep -n "project_path\|projectPath" -- backend/src`
  retorna **zero hits**, e `buildTaskAssignmentPayload` (`ExecutionService.java:421-450`) não o inclui.
  Logo, hoje, o path vem sempre de `settings.workspace_path` (default `/workspace`; o instalador Mac
  seta `${INSTALL_DIR}/workspaces` — `scripts/install-mac-client.sh:196`).
- `[confirmado]` **Mas não há defesa nenhuma se ele passar a vir.** `_validate_task_data`
  (`daemon.py:25-56`) valida apenas `task_id`, `title`, `description`. Não há allowlist de prefixo,
  não há `resolve_under_workspace` neste caminho. Se qualquer versão futura do backend (ou um
  backend comprometido/malicioso, ou um MITM no STOMP) enviar `project_path: "/"`, o daemon monta a
  raiz do host como `/workspace` rw dentro do container do agente. **É uma bomba armada, não
  detonada.** A função de containment existe (`sandbox/paths.py:11-42`) mas é usada **apenas** pelo
  backend PROCESS (`process/session.py:117,130`), não pelo Docker.
- `[não encontrado]` Nenhum outro bind mount: sem `/etc`, sem `~/.ssh`, sem `~/.aws`, sem
  `~/.gitconfig`, sem socket Docker.

### D.7 Ciclo de vida — descartado ou reaproveitado?

**Cold path (default, `sandbox_pool_enabled=False` — `config.py:129`):** um container por tarefa.
Mas o teardown chama **`stop()`, não `cleanup()`** (`daemon.py:487-488`) → o container fica em estado
`exited` no host, não é removido. `[confirmado]`

**E o reaper não existe.** Isto está admitido por escrito no próprio repositório:

```
client/tests/test_architecture_guards.py:55-64
  "sandbox_ttl_seconds": (
      "No reaper exists. SandboxLifecycleManager (docker/lifecycle.py) implements TTL
       and is unit-tested, but nothing constructs it: sandbox.py imports it and stores
       ttl_seconds without ever using either. An orphaned sandbox therefore lives until
       the daemon removes it."
  )
```

`docker/lifecycle.py` tem **257 linhas de código testado e nunca instanciado**. `[confirmado]`

**Warm path (`SQUADX_SANDBOX_POOL_ENABLED=true`):** containers são **reaproveitados entre tarefas**:

- `release()` (`pool.py:284-320`) faz `container.stop(timeout=5)`, verifica se voltou a `created`, e
  **devolve ao pool**. Nenhuma limpeza de filesystem, nenhum `docker rm`. Tudo que a tarefa anterior
  escreveu fora de `/workspace` (em `/tmp` tmpfs, ou em qualquer caminho gravável) persiste.
- Todos os containers do pool compartilham **um único** diretório do host:
  `pool.py:379` monta `self._workspace_mount → /workspace`, vindo de
  `settings.sandbox_pool_workspace_root` = `/var/squadx/workspaces` (`config.py:138-140`).
- O próprio header do módulo avisa (`pool.py:9-13`): *"Don't enable the pool without worktrees if you
  run concurrent tasks — they will step on each other."* — mas o isolamento delegado (git worktree) é
  **por subtarefa dentro de uma execução**, não por tenant.
- Nota positiva real: o pool **não** carrega credenciais, porque secrets viajam no exec e não no
  create (`pool.py:25-28`). Isso está correto.

`[confirmado]` **Não há conceito de tenant em nenhum ponto do ciclo de vida do sandbox.** Nem
`organization_id`, nem label, nem namespace de container. Grep por `organization` no
`client/squadx_client/docker/` → zero hits.

### D.8 Tempo de spin-up

`[não encontrado]` **Nenhuma métrica instrumentada de spin-up.** `pool.py` registra `time.time()` em
`in_use_since` (`:253`) e `created_at` (`:396`), mas nada emite duração de start como métrica.
`docker/metrics.py` (133 linhas) coleta CPU/mem do container em execução, não latência de criação.

Único número disponível é **documental, não medido**: `pool.py:2-4` afirma cold start de
"10-20s" e warm de "sub-second". `[inferência a partir de comentário do autor — não é medição]`.

---

## E. Orquestração de agentes

### E.1 Agentes e como são definidos

**7 especialistas + 1 adapter, definidos em código** (subclasses Python com prompt embutido no
arquivo), não em config nem em arquivo de prompt externo:

| Agente | Classe | Linha em `agents/factory.py` |
|---|---|---|
| Frontend | `FrontendAgent` | `:32` |
| Backend | `BackendAgent` | `:60` |
| Fullstack | `FullstackAgent` | `:90` |
| DevOps | `DevOpsAgent` | `:116` |
| QA | `QAAgent` | `:144` |
| Coordinator | `CoordinatorAgent` | `:172` |
| Database | `DatabaseAgent` | `:231` |
| External CLI (adapter) | `ExternalCliAgent` | `agents/external_cli_agent.py` (268 linhas) |

Entrada única: `create_agent()` (`factory.py:299`). Há um bloco de "disciplina compartilhada"
anexado a todo prompt de especialista (`factory.py:11`). `[confirmado]`

O `ExternalCliAgent` suporta 5 providers built-in (CLAUDE_CODE, CODEX, GEMINI_CLI, AIDER, OPENCODE)
e um mecanismo genérico de templates por env var (`config.py:36-42`) que permite registrar uma nova
CLI **sem mudança de código**.

### E.2 Motor de orquestração

**Grafo de estados (LangGraph `StateGraph`)**, loop principal em
`client/squadx_client/orchestrator/graph.py:72-128`. `[confirmado]`

```
analyze → plan → execute ⇄ (should_continue) → review → arbiter ──APPROVE──→ commit → END
                                                          ├──CONTINUE──→ execute
                                                          └──ESCALATE──→ escalate → END
```

Nós implementados em `orchestrator/nodes.py` (868 linhas): `analyze_task`, `create_plan`,
`execute_subtask`, `review_results`, `arbiter`, `escalate`, `commit_changes`, `handle_error`.

O **arbiter** — não o reviewer — é a autoridade que encerra o loop; `cycle_count` com `max_cycles`
é o backstop rígido, e há teto de custo por run (`cost_budget_usd=5.0` default, `config.py:106`),
threaded no state pelo daemon (`daemon.py:346`). `[confirmado]` Este é um design maduro: há um
loop-breaker explícito e um teto econômico.

### E.3 Comunicação entre agentes

**`[confirmado]` Não há comunicação agente↔agente em execução.** Os agentes são invocados
sequencialmente pelo grafo; o estado compartilhado é o `OrchestratorState`
(`orchestrator/state.py`, 168 linhas).

Existe uma infraestrutura de mailbox construída e **morta**:
- `client/squadx_client/messaging/mailbox.py` define `AgentMailbox` e `AgentMailboxMessage`.
- Varredura em todo o `client/` (excluindo `__pycache__`, `.venv`): **`AgentMailbox` nunca é
  instanciado fora do próprio arquivo**. `[confirmado]`
- O backend tem o lado servidor ativo (`AgentMessageController`, `AgentMessageService`, tabela
  `agent_messages` via `V21`), e o frontend tem `agentMessagesApi` (`frontend/src/lib/api.ts:1457`).
  Ou seja: **a feature existe de ponta a ponta exceto no agente**.

### E.4 Persistência de estado de execução — checkpoint/restore

**`[confirmado]` Não existe checkpoint/restore de execução.** Duas evidências independentes:

1. `graph.compile()` é chamado **sem `checkpointer`** (`graph.py:128`). Grep por
   `checkpointer|MemorySaver|SqliteSaver` no client → **zero hits**. Se o daemon morrer no meio de
   uma execução, o grafo recomeça do zero — ou não recomeça.
2. `client/squadx_client/checkpoint/manager.py` implementa um `CheckpointManager` completo
   (`save`/`restore`/`list_checkpoints`/`delete`, com captura de `git diff HEAD`) que **não tem
   nenhum consumidor**: grep por `CheckpointManager` fora do próprio módulo → zero hits.
   **Código morto.**

O que **existe** é checkpoint de *worktree* (git commit automático antes do teardown do sandbox):
`nodes.py:533-548` chama `wm.checkpoint(...)`, implementado em `git/worktree.py:67-72`. Isso salva o
trabalho, não o estado do grafo.

### E.5 Git worktree isolation

**`[confirmado]` Implementado e ligado por default** (`use_worktrees=True`, `config.py:60`):

- `client/squadx_client/git/worktree.py` — `WorktreeManager` com `create`/`checkpoint`/`merge`.
- Uso real por subtarefa: `nodes.py:378-403` cria `.worktrees/<nome-único>/` com branch própria a
  partir de main, **e é esse path que o sandbox monta como `/workspace`**.
- Guard barato antes de tentar: `_is_git_repo(workspace_path)` (`nodes.py:129`).
- Falha degradada: qualquer erro cai de volta no workspace compartilhado com log
  `worktree_setup_failed_falling_back` (`nodes.py:403`) — **é hardening, não garantia**.
- Merge de volta na branch de integração ao final: `nodes.py:811-829`.

Testado: `tests/test_worktree.py`, `tests/test_orchestrator_worktree.py`.

---

## F. Infraestrutura e deploy — **SEÇÃO CRÍTICA**

### F.1 Onde o SquadX roda hoje

**Hoje, de fato: na máquina do desenvolvedor.** Um Mac mini M4 com Colima. Não encontrei evidência
de nenhum ambiente de staging ou produção rodando. `[confirmado por]`:

- `documentos/PILOTO-ESCOPO.md:15-24` — aceite local **GO condicional** (2026-07-29), evidência
  `task_id=6`, smoke com OpenRouter na máquina de dev.
- `documentos/PILOTO-ESCOPO.md:28-33` — aceite staging: **NO-GO**, faltando "#39 cluster + secrets,
  #41 egress Linux, #42 live-view, #43 UAT/authz formal".
- Nenhum artefato de estado de infra (terraform state, kubeconfig, output de `kubectl`) no repo.

### F.2 IaC / manifests presentes

| Artefato | Caminho | Estado |
|---|---|---|
| Helm chart | `infra/helm/squadx/` (Chart + 11 templates) | completo (backend, frontend, postgres StatefulSet, redis, ingress, secrets) |
| Kustomize base | `infra/k8s/base/` (5 manifests + kustomization) | completo |
| Overlays | `infra/k8s/overlays/{staging,prod}/` | completo; **validados offline no CI** (`ci.yml:145-173`, inclusive checando que staging não vaza para o namespace/host de prod) |
| Compose dev | `docker-compose.yml` (raiz) | postgres, redis, monitoring (profile) |
| Compose plataforma | `platform/docker-compose.yml` (13 services) | squadx + squadx-live + brainsentry + livekit + minio + observabilidade |
| nginx TLS | `infra/nginx/` (Dockerfile, nginx.conf, ssl-renew.sh) | presente |
| Systemd (daemon) | `client/deploy/squadx-client.service` | presente |
| Monitoring | Prometheus + alert-rules, Loki + promtail, Tempo, Alertmanager, Grafana provisioning | presente |

### F.3 Orquestração: Kubernetes **para o painel**, nunca para o agente

Esta é uma decisão explícita e bem documentada, e o código a respeita:

- `infra/k8s/client-deployment.yml.disabled:1-8` — arquivo **deliberadamente desabilitado**:
  *"the client daemon does NOT run as a Kubernetes pod. It creates hardened Docker sandboxes via the
  host Docker daemon, which a plain pod cannot reach."*
- `infra/helm/squadx/templates/client-deployment.yaml:1-6` — gated off por `.Values.client.enabled`.
- `client/deploy/README.md:20-25` — as alternativas (montar socket do nó, ou DinD privilegiado)
  foram **avaliadas e rejeitadas**.

**Modelo de deploy real:** k8s (ou compose) roda backend/frontend/postgres/redis; o daemon roda como
**processo nativo systemd num host Docker dedicado**, com `User=squadx`, `Group=docker`
(`squadx-client.service:24-25`). O próprio arquivo declara a consequência
(`squadx-client.service:13-15`): *"That group grants root-equivalent control of the host Docker — run
this host as a dedicated sandbox runner, not alongside other workloads."*

### F.4 Virtualização disponível no host de execução

**Saída literal dos comandos pedidos está no bloco `EVIDÊNCIA` ao final.** Resumo e leitura:

| Comando | Resultado | Leitura |
|---|---|---|
| `uname -a` | `Darwin … 25.5.0 … RELEASE_ARM64_T8132 arm64` | macOS 26.5.2, Apple **M4** (ARM64) |
| `ls -l /dev/kvm` | `No such file or directory` | **Sem KVM.** Esperado: macOS não tem KVM |
| `egrep -c '(vmx\|svm)' /proc/cpuinfo` | `No such file or directory` | N/A em macOS; e ARM não usa vmx/svm |
| `systemd-detect-virt` | `command not found` | N/A em macOS |
| `nproc` / `free -g` | `command not found` | Equivalentes: 10 cores, 16 GiB (`sysctl`) |
| `docker info` | Server 29.5.2 · **Runtimes: runc apenas** · overlayfs · cgroup v2 · Kernel 6.8.0-117 Ubuntu 24.04 · aarch64 · **CPUs: 2** · **Mem: 3.813GiB** | O engine roda numa **VM Linux** com 2 vCPU e 3,8 GiB |
| `colima status` | `colima is running using macOS Virtualization.Framework` · `mountType: virtiofs` | A VM é gerida pelo **Virtualization.framework** da Apple |

**Conclusões duras:**

1. **`[confirmado]` O host auditado não suporta Firecracker.** Firecracker exige KVM/`/dev/kvm`. Aqui
   o Docker roda dentro de uma VM do Virtualization.framework, e virtualização aninhada não está
   disponível nesse arranjo na Apple Silicon para este caso de uso.
2. **`[confirmado]` gVisor não está instalado** (`Runtimes: io.containerd.runc.v2 runc`). Poderia ser
   instalado dentro da VM Colima Linux, mas não está — e nada no repo o provisiona
   (`grep -rn "runsc" infra/ scripts/` → apenas as referências de código Python).
3. **`[confirmado]` O envelope de recursos é minúsculo para o caso de uso:** 2 vCPU / 3,8 GiB na VM,
   contra um default de sandbox de **2 CPU / 2 GiB por agente** (`config.py:74-75`) e
   `max_concurrent_agents=4` (`config.py:72`). **Quatro agentes concorrentes pediriam 8 GiB numa VM
   de 3,8 GiB.** O limite de concorrência do daemon não conversa com a capacidade do host.
4. A imagem do agente tem **5,01 GB em disco** (`docker images`), o que também explica o cold start
   de 10-20s citado em `pool.py:2-4`.

### F.5 O ambiente de produção alvo suporta KVM / nested virt?

**`[não encontrado]` — não é possível determinar a partir do repositório.** Nenhum manifest, script
ou doc especifica instance type, provedor de nuvem, ou requisito de KVM para o agent host. O mais
próximo é `ADR-0009` (tabela "O que o usuário precisa instalar"), que lista para `firecracker`:
*"KVM + firecracker/containerd · difícil nativo · Host dedicado / cloud"* — uma condição, não um fato
sobre um host existente.

→ **Pergunta aberta para o humano (N.1).**

Para responder isso é preciso rodar, **no host real de execução** (não neste sandbox):

```bash
uname -a
ls -l /dev/kvm
egrep -c '(vmx|svm)' /proc/cpuinfo
lscpu | grep -i virtual
systemd-detect-virt
docker info | grep -iE 'runtime|storage|kernel|server version'
nproc && free -g
# se for cloud: instance type e se nested virt está habilitado
```

---

## G. Fronteira Control Plane / Execution Plane

**Contexto do prompt vs. repositório:** o prompt afirma que o SquadX foi re-escopado para execution
plane, com Kanban/billing/RBAC/templates migrando para um Control Plane separado.
**`[não encontrado]` Não há nenhum registro dessa decisão no repositório** — nenhum ADR, RFC,
OpenSpec change, issue referenciada ou doc em `documentos/`. O corpus vigente diz o **oposto**:
`CONSTITUTION.md:11-17` e `openspec/project.md:8-14` descrevem duas faces do mesmo produto, e
`ADR-0006` posiciona o Control Panel como bounded context **dentro da mesma stack**.

Medindo o quanto do re-escopo foi executado no código: **zero.**

| Domínio | Status | Evidência |
|---|---|---|
| **Kanban / gestão de tarefas** | **presente e ativo** | Backend: `TaskController` (`/api/v1/tasks`), `TaskService`, `Task` entity, `TaskStatusTransition` VO, `TaskDependency`. Frontend: `src/app/(dashboard)/tasks/`, `components/kanban/kanban-board.tsx` + `task-card.tsx` com testes. Dep npm dedicada `react-kanban-kit`. É o **coração** do backend `[confirmado]` |
| **Billing / cobrança** | **presente, backend ativo, sem UI** | Backend ativo: `BillingController` (`/api/v1/billing`), `BillingService`, `Subscription` entity, `V8__add_billing_tables.sql`, `stripe-java:26.1.0` (`pom.xml:162`), webhook público em `SecurityConfig.java:63`. Frontend: **não há `billingApi`** em `src/lib/api.ts` (22 objetos `*Api`, nenhum de billing) e nenhuma página. → **backend vivo, produto morto** `[confirmado]` |
| **RBAC / autenticação de usuário final** | **presente e ativo** | `AuthController`, `AuthService` (JWT jjwt 0.12.6), OAuth2 client + `CustomOidcUserService`, `RbacController` com `@PreAuthorize`, `CustomRole`/`RolePermission`/`UserCustomRole` entities, `SsoConfigController`, `V11__add_sso_and_rbac.sql`. É o mecanismo de authz de **todo** o sistema `[confirmado]` |
| **Branding white-label** | **presente, backend ativo, UI mínima** | `BrandController` (`/api/v1/branding`), `BrandService` com `OrganizationAccessGuard`, `BrandConfig` entity, `V14__add_white_label.sql`, `brandApi` no frontend (`api.ts:1312`). Sem página dedicada em `(dashboard)/` `[confirmado]` |
| **Templates de equipe** | **presente e ativo** | `TemplateController` (`/api/v1/templates`), `TeamTemplateService`, `templatesApi` (`api.ts:1242`) `[confirmado]` |

**Extras que também vivem no monolito e não são execution plane:** `MeetingController` +
`CalendarSyncController` + Google Calendar API (`pom.xml:145-156`), `RecordingController` +
`SessionRecording` + S3 (`pom.xml:138`), `HighlightController` + `AiAnalysisService`,
`NotificationController` + `EmailService` (Resend), `RegionController`, `CapabilityController`,
`MemoryController`/`MemoryPolicyController`, `AutopilotController` + JobRunr. `[confirmado]`

**Conclusão da seção G:** o re-escopo descrito no prompt **não começou**. O backend não é um
execution plane — é um SaaS B2B completo com 33 controllers, do qual o execution plane é uma fatia
(`ExecutionController` + `WebSocketEventService` + `RunAdmissionService`). Se o objetivo é extrair o
execution plane, a superfície a separar é de **~36 mil linhas de Java** com acoplamento por JPA
(`Task → Project → Organization` atravessa quase toda entidade).

---

## H. Persistência e multi-tenancy

### H.1 Bancos

| Banco | Uso | Evidência |
|---|---|---|
| PostgreSQL 16 | Backend, via Flyway (39 migrações `V1`–`V37`) | `pom.xml:84,89`, `backend/src/main/resources/db/migration/` |
| Redis | Cache/sessão Spring | `pom.xml:54` |
| SQLite | Estado local do daemon | `config.py:64` (`~/.squadx/squadx.db`), `aiosqlite` dep |
| PostgreSQL (×3) + MinIO + FalkorDB | Stack unificada (squadx + live + brainsentry) | `platform/docker-compose.yml:37,60,83,101,139` |

### H.2 Discriminador de tenant

**`[confirmado]` Sim, existe e é consistente: `organization_id`.** Presente em pelo menos 11
migrações (`V1`, `V3`, `V8`, `V9`, `V11`, `V12`, `V14`, `V18`, `V19`, `V28`, `V34`). A hierarquia é
`Organization → Project → Task → Execution` e `Organization → Squad → Agent`.

### H.3 Row-Level Security

**`[não encontrado]` — nenhuma RLS.** `grep -rn "ROW LEVEL SECURITY\|CREATE POLICY"` em todas as 39
migrações → **zero hits** (o único match de "POLICY" é um comentário em `V36` sobre política de
egress). `[confirmado]`

Isolamento é **100% aplicativo**, por duas vias:
1. `OrganizationAccessGuard.requireMember(orgId, userId)` (`service/OrganizationAccessGuard.java:34-40`)
   — centralizado, usado por 8 services.
2. `validateUserAccess` privado, copiado por service (ex.: `ExecutionService.java:357-361`).

O javadoc do próprio guard é a evidência mais honesta do repo
(`OrganizationAccessGuard.java:11-18`): *"Historically each service carried its own private
validateUserAccess copy, and the peripheral resources that were added without that copy became
cross-tenant IDOR holes — a user could act on another organization's billing, branding, meetings,
agent messages, etc. purely by passing a different id."* `[confirmado]`

Isso já foi corrigido para os recursos citados. Mas o padrão que produziu os buracos — checagem
opcional por convenção, sem enforcement estrutural — **continua sendo o padrão**.

### H.4 Se dois clientes usassem a plataforma hoje, algo impediria vazamento?

Resposta honesta, por camada:

| Camada | Impede vazamento? | Evidência |
|---|---|---|
| REST | **parcialmente** | Guard/`validateUserAccess`/`@PreAuthorize` cobrem a maioria. Mas 18 de 41 services não têm checagem própria, e não há teste ou lint que garanta que um endpoint novo a tenha. **Uma regressão é silenciosa.** |
| Banco | **não** | Sem RLS. Uma query que esqueça o `WHERE organization_id = ?` retorna dados de todos `[confirmado]` |
| STOMP broadcast | **sim, para os tópicos conhecidos** | `StompSubscriptionAuthorizer` resolve `organizations`/`projects`/`tasks`/`executions` até a org e checa membership (`:70-84`); destinos escopados irresolvíveis são **negados** (`:66`) — a decisão certa |
| STOMP `/topic/live/{code}` | **não** | O `switch` em `resolveOrganizationId` (`:73-84`) não trata `live` → cai em `default -> null` → **allow**. Qualquer usuário autenticado que conheça um código de sessão assiste à live view de outra organização `[confirmado]` |
| Plano de execução (sandbox) | **não** | Zero noção de tenant no `client/` (grep por `organization` em `client/squadx_client/docker/` → zero). O isolamento entre tenants é *"cada tenant tem seu próprio host de daemon"* — o que é uma escolha arquitetural válida, mas **não está enforced em lugar nenhum**, e o backend despacha por e-mail de usuário (`ExecutionService.java:415`), não por tenant |
| VNC / Live View | **não** | `x11vnc … -nopw` (`client/docker/start-agent.sh:94`) — **sem senha**. A porta é publicada pelo sidecar no host (`sandbox_start.py:177`). Qualquer um com acesso de rede ao host Docker e à porta vê e **controla** a sessão do agente `[confirmado]` |

---

## I. Segredos

### I.1 Como são armazenados e injetados

| Camada | Mecanismo | Evidência |
|---|---|---|
| Backend | 100% env var, **sem default inseguro** para os críticos: `${SPRING_DATASOURCE_PASSWORD}` e `${JWT_SECRET}` não têm fallback — a app **não sobe** sem eles | `application.yml:8,51` `[confirmado]` |
| Backend (opcionais) | `${STRIPE_API_KEY:}`, `${RESEND_API_KEY:}`, `${AWS_SECRET_ACCESS_KEY:}` etc. com default vazio | `application.yml:65,70,192-193` |
| k8s | `Secret` via Helm (`infra/helm/squadx/templates/secrets.yaml`) e `infra/k8s/secrets.example.yml` | manifest presente |
| CI | `${{ secrets.GITHUB_TOKEN }}`; segredo de teste é literal e óbvio (`JWT_SECRET: test-secret-key-for-ci-must-be-32-chars`) | `ci.yml:65,193` `[confirmado, aceitável]` |
| Daemon | `pydantic-settings` lendo `.env` + env (`config.py:12-16`) | `[confirmado]` |
| Sandbox | env de **exec** (ver D.5) | `[confirmado]` |

**`[não encontrado]` Nenhum secret manager / Vault / keychain / KMS** em nenhum ponto. A gestão é
"env var mais disciplina".

### I.2 Varredura do histórico git

Comando executado (redigindo qualquer match antes de exibir):

```bash
git log --all -p | grep -nE "sk-[A-Za-z0-9]{20,}|sk-ant-[A-Za-z0-9-]{20,}|AKIA[0-9A-Z]{16}|ghp_[A-Za-z0-9]{30,}|-----BEGIN (RSA|OPENSSH|EC) PRIVATE KEY-----"
```

**Resultado: zero matches.** `[confirmado]`

`git ls-files | grep -iE "\.env"` retorna apenas **5 arquivos `.env.example`** (backend, client,
client/deploy, frontend, platform) — nenhum `.env` real rastreado.
`git log --all --name-only -- '*.env'` → **vazio**. `[confirmado]`

`.gitignore` cobre `.env`, `.env.local`, `.env.*.local`, `*.pem`, `*.key`, `credentials.json`,
`secrets.json` (`.gitignore:9-11,63-66`). `[confirmado]`

### I.3 Segredos vivos no disco (não no git)

`client/.env` existe no disco de trabalho com **chaves reais** de `SQUADX_API_TOKEN`,
`OPENAI_API_KEY`, `ANTHROPIC_API_KEY`, `OPENROUTER_API_KEY`, `SUPABASE_ANON_KEY` (li os nomes das
variáveis; **não reproduzo valores**). Está corretamente **ignorado pelo git** e não vazou.
`[confirmado]` É o padrão de trabalho do projeto — em texto plano, sem criptografia em repouso, no
mesmo host onde os sandboxes rodam.

---

## J. Observabilidade

| Capacidade | Estado | Evidência |
|---|---|---|
| Métricas (backend) | **implementado** | `micrometer-registry-prometheus` (`pom.xml:169`); actuator expõe `health,info,metrics,prometheus` (`application.yml:92`) num management port separado (9091) `[confirmado]` |
| Tracing OTel | **implementado** | `micrometer-tracing-bridge-otel` + `opentelemetry-exporter-otlp` (`pom.xml:175,179`); config `management.tracing` + `otlp.tracing` (`application.yml:99-103`); Tempo provisionado (`infra/tempo/`) `[confirmado]` |
| Métricas de negócio | **existe apenas no working tree, não commitado** | `backend/src/main/java/dev/squadx/observability/BusinessMetrics.java` + teste aparecem como `??` em `git status` `[confirmado]` |
| Logs agregados | **implementado (infra)** | Loki + promtail (`infra/loki/`), com promtail lendo `/var/lib/docker/containers` e o socket Docker `:ro` (`docker-compose.yml:163-166`) |
| Dashboards | parcial / não commitado | `infra/grafana/provisioning/dashboards/` está **untracked** (`git status`); só `business-overview.json` |
| Alertas | presente | `infra/prometheus/alert-rules.yml` + Alertmanager |
| Logging estruturado (client) | **parcial — declarado, não configurado** | `structlog` é dependência e `structlog.get_logger()` é usado, mas **`structlog.configure(...)` nunca é chamado** em lugar nenhum (grep → zero hits). Resultado: saída no renderer default (console), **não JSON** `[confirmado]` |
| Cost tracking de LLM | **implementado** | `CostEvent` entity (org/agent/execution/provider/model/tokens), `CostTrackingService`, `CostController` (`/api/v1/costs`), `V18__add_cost_tracking.sql`, `costsApi` no frontend. Client reporta tokens/custo por run (`daemon.py:481-485`) e há teto por run (`cost_budget_usd`) `[confirmado]` |
| Trilha de auditoria (usuário) | **implementado** | `AuditLog` entity (user, action, resourceType, resourceId, details jsonb, **ipAddress**), `AuditService`, `AuditLogController`, `V5__create_audit_logs_table.sql` `[confirmado]` |
| **Trilha de auditoria "o que o agente executou"** | **parcial e não confiável para forense** | Há `ExecutionLog` com classificação de Attention Budget (`ExecutionLog.java:23-28`) e streaming de output (`daemon.py:753+`, `_progress` em `daemon.py:446-461`). **Mas:** o progresso enviado é truncado — `stripped.splitlines()[-1][:200]` (`daemon.py:452`), ou seja **última linha, 200 caracteres**. Não há registro estruturado *por comando executado* (`agents/tools.py:33` `execute_bash` não emite evento de auditoria com o comando + exit code + hash do diff). Para responder "o que exatamente este agente rodou às 14h32?" o sistema **não tem a resposta** `[confirmado]` |

---

## K. Testes e CI

### K.1 Volume de testes

| Runtime | Testes | Módulos | Proporção |
|---|---:|---:|---|
| Backend (JUnit 5 + Mockito + AssertJ) | 71 arquivos | 283 `.java` em `main` | ~25% |
| Client (pytest) | 53 arquivos | 132 `.py` | ~40% |
| Frontend (Vitest + Testing Library) | 21 arquivos `.test.tsx?` | 89 `.ts/.tsx` em `src` | ~24% |

### K.2 Execução real

**Client — executado nesta auditoria:**

```
744 passed, 2 skipped, 1 warning in 5.06s
```

`[confirmado]` — `.venv/bin/python -m pytest -q` no diretório `client/`. Suite rápida e verde.

**Ressalva importante sobre o que esses 744 provam:** os 2 skips são justamente os testes marcados
`integration`/`sandbox_docker` (marcadores declarados em `pyproject.toml:96-99`; usados em
`tests/test_egress_sidecar.py` e `tests/test_e2e_execution.py`). Os testes de sandbox rodam sobre
mocks — `tests/test_docker_sandbox_backend.py` tem 11 usos de `Mock/patch`,
`tests/test_egress_sidecar.py` tem 23. **A suite verifica que os kwargs corretos são passados ao SDK
do Docker; não verifica que o kernel do host os aplica.** `[confirmado]`

**Frontend e backend — não executados** (ver ressalva no sumário). `frontend/node_modules` ausente.

### K.3 CI

`.github/workflows/ci.yml` — 6 jobs: `backend`, `frontend`, `client`, `kustomize`, `docker` (só em
`main`), `deploy-staging`. Mais `release.yml`.

**Status real (`gh run list`):** `[confirmado]`

| Data | Branch | Resultado |
|---|---|---|
| 2026-08-04 | main | **failure** |
| 2026-07-30 | main | **failure** |
| 2026-07-30 | main | **failure** |
| 2026-07-30 | main | **failure** |
| 2026-07-30 | PRs | success (×3) |

**O CI de `main` está vermelho desde pelo menos 2026-07-30 — os 4 últimos pushes em `main` falharam.**

Causa exata do último (`gh run view 30951211478`): job **Client (Python)** falha em
**"Lint with ruff"**. Reproduzi localmente:

```
I001 Import block is un-sorted or un-formatted
  --> squadx_client/live/__init__.py:3:1
  --> squadx_client/live/live_api_client.py:3:1
  --> squadx_client/live/session_manager.py:3:1
Found 3 errors. [*] 3 fixable with the `--fix` option.
```

**Três erros de ordenação de import, auto-corrigíveis com `ruff check --fix`.** `[confirmado]`

**A consequência é desproporcional à causa:** o job `docker` declara
`needs: [backend, frontend, client, kustomize]` (`ci.yml:176`). Com `client` vermelho, **nenhuma
imagem foi construída nem publicada no GHCR desde 2026-08-04**, e `deploy-staging` nunca roda. As
imagens `latest` no registry estão defasadas em relação a `main`. `[confirmado — os jobs `Docker
Build` e `Deploy to Staging` aparecem como `- ... in 0s` (skipped) no run]`

Nota: a versão do ruff no CI é `0.15.22` (pinada) e a local é `0.15.16` — ambas acusam os mesmos
3 erros, então não é discrepância de versão.

### K.4 Testes que cobrem especificamente a fronteira de isolamento

**Existem, e são de qualidade acima da média — mas todos em nível de unidade/mock.** `[confirmado]`

| Teste | O que cobre |
|---|---|
| `tests/test_hardening_seccomp.py` | Perfil seccomp é inlinado como JSON (não path) — **zero mocks**, testa a lógica pura |
| `tests/test_network_policy.py` + `test_network_policy_injection.py` | Geração e injeção de política |
| `tests/test_egress_sidecar.py` | Topologia netns; tem marca `integration` (skipped sem Docker) |
| `tests/test_egress_guard.py` | Regra `DOCKER-USER` de metadata |
| `tests/test_egress_dns_proxy.py` | Anti-rebinding do DNS proxy (roda no repo, não só na imagem — motivo do dev-dep `dnslib`) |
| `tests/test_sandbox_exec_env.py` | Credenciais no exec, não no create |
| `tests/test_sandbox_paths.py` | Containment de path (mas só usado pelo backend PROCESS) |
| `tests/test_sandbox_backend_contract.py` | Contrato comum entre backends |
| `tests/test_process_sandbox_backend.py` | bwrap/Seatbelt |
| **`tests/test_architecture_guards.py`** | **O melhor artefato de qualidade do repo:** falha o build se um setting de segurança não tiver consumidor, forçando "dead caps" a serem declarados por escrito num `_QUARANTINE` com justificativa. Docstring `:1-22` narra três controles que estavam mortos e silenciosos |

**`[não encontrado]` O que falta: nenhum teste executa um sandbox real e tenta escapar dele.** Não há
teste que suba um container e verifique empiricamente que `CAP_SYS_ADMIN` foi negado, que o
filesystem é read-only, que uma conexão a um domínio fora do allowlist é bloqueada, ou que a chave
de LLM não é exfiltrável. O `test-egress` existe como **script manual** (`homolog-client-host.sh
test-egress`, exigindo `SQUADX_DOCKER_IT=1` e Linux), não como gate de CI.

---

## L. Divergências e dívida

### L.1 Documentação × código

| # | Afirmação | Fonte | Realidade | Evidência |
|---|---|---|---|---|
| 1 | "Control Panel: a spec é a fonte de verdade; Pass 5; MCP workspace" (6 ADRs "Aceitos") | `CONSTITUTION.md:19-45`, ADR-0001…0006 | **Zero linhas implementadas** 2 meses após aceite | `git grep -ril "SpecVersion\|Pass5\|materializ"` → 0 |
| 2 | "MCP `workspace` como contrato único" | ADR-0003, RFC-0001 | Diretório `client/squadx_client/mcp/` só tem `__pycache__`; import é opcional e loga ausência | `git ls-files client/.../mcp/` vazio; `nodes.py:768-773` |
| 3 | "não há `@PreAuthorize` nos controllers" | `CLAUDE.md` | Há | `RbacController.java:34,49` |
| 4 | "Python 3.12" | `CLAUDE.md`, `README.md:6` badge | `pyproject` exige `>=3.11`; CI roda 3.11 | `pyproject.toml:10`, `ci.yml:12` |
| 5 | "seccomp (336 syscalls)" | `README.md:98` | **320** syscalls | contagem via `json.load` de `client/docker/seccomp/agent.json` |
| 6 | Badge `license-MIT` | `README.md:5` | **Não existe arquivo LICENSE** na raiz | `ls LICENSE*` → no matches |
| 7 | Supabase Realtime como sinalização WebRTC | `documentos/ARQUITETURA-RUNTIME.md` (diagrama), `client/.env.example:38-40` | Removido do código em 2026-08-04 (3 commits); resta **só** em `application-test.yml` | `git grep -ril supabase -- backend/src frontend/src client/squadx_client` → 1 hit (teste) |
| 8 | Supabase obrigatório para subir a plataforma | `platform/docker-compose.yml:14-15` (`SUPABASE_URL:?Set…` — **falha se ausente**) | Código não usa mais | `platform/docker-compose.yml:14` |
| 9 | Auto-upgrade de runtime por threshold (100/1000 exec/dia) | `documentos/DECISAO-ARQUITETURAL-SANDBOXING.md`, `config.py:121-125` | `resolve_runtime()` só probeia binário; **nunca lê a flag nem os thresholds** | `hardening.py:478-508`; admitido em `test_architecture_guards.py:66-71` |
| 10 | "Docker hardened … AppArmor" | comentários históricos | AppArmor nunca foi aplicado; default removido | `hardening.py:216-224` (admissão no código) |
| 11 | "gVisor / Firecracker" no roadmap com `[x]` | `README.md:505-506` | São 2 dicts de 1 linha; `FIRECRACKER` marcado `implemented=False` no próprio factory | `hardening.py:69-78`, `factory.py:72,81` |
| 12 | Re-escopo para execution plane (premissa do prompt) | — | Sem registro no repo; corpus diz o contrário | `CONSTITUTION.md:11-17`, `openspec/project.md:8-14` |
| 13 | `RESUMO-EXECUTIVO-20-LINHAS.md`, `PROJETO_COMPLETO.md`, `ARCHITECTURE.md` | `documentos/` | Auto-declarados obsoletos por outro doc ("mais antigo/marketing") | `ARQUITETURA-RUNTIME.md:6` |

### L.2 Código morto (agrupado por tema)

| Tema | Artefato | Linhas | Por que está morto |
|---|---|---:|---|
| **Ciclo de vida do sandbox** | `client/squadx_client/docker/lifecycle.py` (`SandboxLifecycleManager`) | 257 | Importado e nunca instanciado. TTL implementado e testado, **sem reaper**. Admitido em `test_architecture_guards.py:55-64` |
| **Checkpoint de execução** | `client/squadx_client/checkpoint/manager.py` | ~140 | `CheckpointManager` sem nenhum consumidor |
| **Comunicação entre agentes** | `client/squadx_client/messaging/mailbox.py` | ~90 | `AgentMailbox` nunca instanciado (backend e frontend da feature existem) |
| **Config fantasma** | `settings.workspace_mount_path` | 1 | Declarado (`config.py:69`) e **nunca lido** em lugar nenhum |
| **Config fantasma** | `auto_upgrade_runtime`, `gvisor_threshold`, `firecracker_threshold`, `sandbox_ttl_seconds`, `sandbox_max_ttl_seconds` | 5 | Em `_QUARANTINE` (`test_architecture_guards.py:55-72`) — mortos **declarados** |
| **Backends não implementados** | `SandboxBackendKind.FIRECRACKER`, `.REMOTE` | — | `implemented=False`; alias parseáveis que só levantam erro (`factory.py:23-27,135-139`) |
| **Componente órfão** | `documentos/KanbanBoard.tsx` | — | Componente React dentro da pasta de documentação |
| **Migrações de outro sistema** | `client/supabase/migrations/*.sql` (2 arquivos) | — | Supabase removido do produto |
| **Manifests desligados** | `infra/k8s/client-deployment.yml.disabled` | — | Deliberado e documentado (aceitável) |
| **Billing sem UI** | `BillingController`/`BillingService`/Stripe | — | Backend + webhook públicos, **sem consumidor no frontend** |
| **Vendor fora do git** | `squad-maps/` (com `node_modules`) | — | Não rastreado; código de terceiro (MIT, "tt-a1i (Archify)") |
| **Clientes parados** | `mobile/` (2.008 linhas, parado 3,5 meses), `desktop/` (397 linhas / 54 de Rust, parado 5 meses) | 2.405 | Zero commits em 90 dias |

### L.3 TODO/FIXME/HACK

`git grep -n "TODO\|FIXME\|HACK"` em `backend/src client/squadx_client frontend/src` → **55 hits**,
mas a esmagadora maioria é `TaskStatus.TODO` (o enum de estado do Kanban), não marcadores de dívida.
`[confirmado]` **Dívida real anotada como TODO/FIXME é praticamente inexistente no código.**

Isso não é uma boa notícia disfarçada. A dívida deste projeto não está em comentários `TODO` — está
em (a) **código completo e nunca ligado** (L.2), e (b) **decisões aceitas e nunca implementadas**
(B.2). Ambas são invisíveis a um grep por marcadores.

**Contraponto justo:** a densidade e a qualidade dos comentários de *raciocínio* neste código estão
muito acima da média. Vários módulos explicam **por que** uma escolha foi feita e qual falha ela
previne (`sandbox.py:71-77`, `pool.py:19-28`, `hardening.py:216-224`, `egress_sidecar.py:1-12`,
`OrganizationAccessGuard.java:11-18`, `test_architecture_guards.py:1-22`). Isso é ativo real.

---

## M. Blockers — **SEÇÃO CRÍTICA**

**Critério:** P0 = impede uso em produção com dado de cliente · P1 = impede escala/multi-tenant ·
P2 = dívida.

| ID | Sev | Descrição | Evidência | Por que bloqueia |
|---|---|---|---|---|
| **B01** | **P0** | **Chaves de LLM do operador vivem dentro do sandbox que executa código gerado por LLM.** Não há proxy de credencial. | `daemon.py:407-416` → `sandbox_exec.py:53,119` | O allowlist de egress **tem que** permitir a API de LLM. Logo existe, por construção, um canal autorizado por onde a chave sai. Prompt injection → exfiltração. A detecção heurística (`cli_security_mode=enforce`, `agents/security.py`) é mitigação, não fronteira. Com chave de cliente, é incidente reportável. |
| **B02** | **P0** | **Um único anel de isolamento (runc) entre código não confiável e um host com privilégio root-equivalente de Docker.** Sem gVisor, sem microVM, sem userns remap. | `docker info` → `Runtimes: runc`; `hardening.py:69-78`; `squadx-client.service:24-25` (`Group=docker`) + `:13-15`; `grep userns` → 0 hits | Um escape de container (CVE de runc/kernel) entrega o daemon, que tem controle root-equivalente do Docker do host, que tem as chaves e todos os workspaces. Não há segunda barreira. A defesa em profundidade prometida pela spec de fev/2026 (Fase 2/3) **não existe**. |
| **B03** | **P0** | **Live View (VNC) sem autenticação.** `x11vnc -nopw`, porta publicada no host pelo sidecar. | `client/docker/start-agent.sh:94`; `sandbox_start.py:177` | Quem alcançar a porta **vê e controla** a sessão do agente — teclado e mouse, dentro do container com o workspace do cliente montado rw. Não é leitura passiva; é controle. |
| **B04** | **P0** | **Nenhum reaper de sandbox.** Teardown chama `stop()`, não `cleanup()`; o TTL configurado nunca é aplicado. | `daemon.py:487-488`; `test_architecture_guards.py:55-64` (admissão explícita); `lifecycle.py` (257 linhas nunca instanciadas) | Containers parados acumulam indefinidamente, cada um com o bind mount do workspace e o filesystem da última execução. Vazamento de recurso **e** de dado. Num host de 3,8 GiB (F.4), também é exaustão. |
| **B05** | **P1** | **Zero isolamento entre tenants no plano de execução.** Nenhum `organization_id` em nenhum ponto do `client/`; despacho por e-mail de usuário; warm pool recicla containers sem limpar FS, sobre um diretório de workspace compartilhado. | `grep organization` em `client/squadx_client/docker/` → 0; `ExecutionService.java:409-417`; `pool.py:284-320,379`; `config.py:138-140` | O modelo implícito é "um host por tenant" — mas isso não é enforced em nenhum lugar, não é verificado, e não é o que o Helm/k8s sugere. Dois clientes no mesmo host de daemon compartilham containers reciclados e um diretório de workspace. |
| **B06** | **P1** | **Sem RLS no Postgres; isolamento é 100% aplicativo e por convenção.** 18 de 41 services sem checagem própria; nenhum lint/teste garante a checagem em endpoint novo. | `grep "ROW LEVEL SECURITY"` em 39 migrações → 0; contagem por service; `OrganizationAccessGuard.java:11-18` (histórico de IDORs cross-tenant já ocorridos) | Uma query sem `WHERE organization_id` vaza tudo. O próprio repo documenta que essa classe de bug **já aconteceu** em billing, branding, meetings e agent messages. Sem defesa em profundidade, vai acontecer de novo. |
| **B07** | **P1** | **`/topic/live/{code}` não é autorizado por tenant.** O resolvedor de organização não trata o namespace `live` → cai em `default -> null` → allow. | `StompSubscriptionAuthorizer.java:73-84` | Qualquer usuário autenticado com um código de sessão assina eventos de live de outra organização. É a única exceção num authorizer que, no resto, é rigoroso (nega escopos irresolvíveis). |
| **B08** | **P1** | **CI de `main` vermelho desde 2026-07-30; nenhuma imagem publicada desde 2026-08-04.** Causa: 3 erros de import-sort (auto-fixáveis). | `gh run list` (4 failures consecutivas em main); `gh run view 30951211478` (job Client → "Lint with ruff"); `ruff check squadx_client` local reproduz | `docker` depende de `client` (`ci.yml:176`), então build e `deploy-staging` são pulados. **O registry está defasado em relação a `main`** — qualquer deploy hoje entrega código antigo. E um main cronicamente vermelho torna o CI inútil como sinal. |
| **B09** | **P1** | **Capacidade do host não conversa com os limites do daemon.** Host: 2 vCPU / 3,8 GiB. Daemon: 4 agentes concorrentes × (2 CPU / 2 GiB) = 8 CPU / 8 GiB. Imagem do agente: 5 GB. | `docker info`; `config.py:72,74,75`; `docker images` | Sob a configuração default, o host satura ou OOM-killa antes do segundo agente. Não há admission control de recurso no daemon (o `RunAdmissionService` do backend faz dedup/follow-up, não capacidade). |
| **B10** | **P1** | **`project_path` do payload STOMP vira bind mount do host sem nenhuma validação.** Hoje o backend não envia o campo — a bomba está armada, não detonada. | `daemon.py:391` → `sandbox_start.py:75-81`; `_validate_task_data:25-56` (valida só 3 campos); `sandbox/paths.py` existe mas só é usado pelo backend PROCESS | Um backend futuro, comprometido ou mal configurado que envie `project_path: "/"` monta a raiz do host rw dentro do container do agente. A função de containment já existe no repo e **não está ligada neste caminho**. |
| **B11** | **P1** | **Backend PROCESS tem egress irrestrito por default** (`process_network="allow"`) e escreve arquivos no host sem sandbox. | `config.py:115`; `isolator.py:23-30`; `process/session.py:117,130` | O backend "leve" para dev não tem a fronteira de rede que o Docker tem. Se alguém o promover além do laptop, perde-se todo o RFC-0006 sem aviso. |
| **B12** | **P2** | **Sem lockfile no client Python.** Ranges abertos (`litellm>=1.50.0` sem teto). | `client/pyproject.toml:14-62`; `[não encontrado]` requirements.lock / poetry.lock / uv.lock | Builds não reprodutíveis num componente que executa código não confiável. Um LiteLLM novo pode mudar comportamento entre dois deploys idênticos. Os comentários no próprio `pyproject.toml:66-70` mostram que isso já quebrou o CI antes. |
| **B13** | **P2** | **Nenhum teste executa um sandbox real e tenta escapar dele.** 744 testes verdes, mas os de isolamento são mocks; os `integration` são pulados. | `pytest` → `744 passed, 2 skipped`; 11 e 23 mocks em `test_docker_sandbox_backend.py` / `test_egress_sidecar.py`; `test-egress` é script manual em `homolog-client-host.sh` | A suite prova que os kwargs certos vão ao SDK — não que o kernel os aplica. A afirmação "está hardened" é, hoje, **não verificada empiricamente em CI**. |
| **B14** | **P2** | **Sem auditoria forense do que o agente executou.** Progresso é truncado a "última linha, 200 chars". | `daemon.py:452`; `agents/tools.py:33` (`execute_bash` sem evento de auditoria) | Após um incidente, não há como reconstruir os comandos executados. Para qualquer cliente com requisito de compliance, é bloqueante. |
| **B15** | **P2** | **Corpus SDD descolado do código.** 6 ADRs "Aceitos" com zero implementação; 3 docs declarando precedência própria e conflitante; 44 mil linhas de markdown. | B.2, B.6 | O status "Aceito" perdeu significado. Um novo colaborador (humano ou agente) que ler `CONSTITUTION.md` construirá um modelo mental errado do sistema — que é exatamente o que a constituição existe para evitar. |
| **B16** | **P2** | **Sem arquivo LICENSE**, apesar do badge MIT; e licenciamento efetivo do FFmpeg embarcado em `av` não verificado. | `ls LICENSE*` → no matches; `README.md:5`; C.3 | Bloqueia distribuição comercial limpa e qualquer due diligence. |
| **B17** | **P2** | **Bus factor = 1.** Autor único em 250/250 commits, 50 branches remotas abertas. | `git shortlog -sn --all`; `git branch -r \| wc -l` | Risco de continuidade do negócio; nenhuma revisão independente jamais ocorreu no código de segurança. |

### M.1 Declaração direta

**O estado atual do isolamento não sustenta um cliente pagante executando código de terceiros.**

Justificativa, sem eufemismo: existe **um** anel de isolamento (runc), e do outro lado dele há um
daemon com privilégio root-equivalente sobre o Docker do host, as chaves de LLM em texto plano no
mesmo processo, um VNC sem senha apontando para dentro do container, e nenhum reaper que garanta que
o container morreu. As três camadas de defesa em profundidade prometidas desde fevereiro de 2026
(gVisor → Firecracker) **não existem em código** — existem como duas strings de configuração e
gatilhos que nunca são lidos.

O que **é** verdade e merece ser dito com a mesma clareza: o **egress** foi projetado e implementado
seriamente — default-deny, sidecar com netns, DNS proxy com pinning por ipset, separação por uid,
falha fechada em três pontos, política por squad vinda do backend. Isso é trabalho de qualidade.
E `test_architecture_guards.py` é um mecanismo genuinamente incomum: um teste que impede que um
controle de segurança volte a ficar morto e silencioso. **O problema não é falta de competência
técnica — é que a superfície do produto (33 controllers, 5 runtimes, Kanban+billing+RBAC+calendário)
está desproporcional ao que um autor único consegue endurecer.**

---

## N. Perguntas abertas para o humano

Cada uma delas **muda a resposta** do ADR de revisão da camada de isolamento. O repositório não as
responde.

### N.1 — Ambiente de produção alvo e KVM/nested virt *(bloqueia a decisão de isolamento)*

O repo só descreve *condições* ("Host dedicado / cloud", ADR-0009), nunca um host real. Precisa-se
saber, do host que vai executar agentes em produção:

- Provedor e instance type (bare metal? VM? qual família?)
- `ls -l /dev/kvm` e `egrep -c '(vmx|svm)' /proc/cpuinfo` **no host real**
- Se for cloud: nested virtualization habilitada? (GCP: `enable-nested-virtualization`; AWS: só
  metal/`.metal`; Azure: séries Dv3+)
- Arquitetura: **ARM64 ou x86_64?** O ambiente de dev é ARM64 (Apple M4). Firecracker em ARM tem
  suporte, mas gVisor e a matriz de imagens mudam. Se produção for x86_64 e dev for ARM, a paridade
  de imagem do agente (5 GB) é um problema por si só.

Sem isso: **Firecracker não pode sequer ser avaliado**, e a única alternativa realista de
endurecimento é gVisor (que roda sobre runc, sem KVM).

### N.2 — Existe cliente/piloto real hoje, e com que dado?

O repo diz GO local / NO-GO staging (`PILOTO-ESCOPO.md:13-33`), mas isso é de 2026-07-29.

- Há alguém além de você executando tarefas no SquadX hoje?
- Se sim: os repositórios que os agentes acessam são **seus** ou **de terceiros**?
- As chaves de LLM são **suas** (BYO do operador) ou **do cliente**?

Isso decide se B01 (chave no sandbox) é um risco aceito ou um incidente esperando acontecer. Com
chave e código do próprio operador, o modelo atual é defensável. Com dado de cliente, não é.

### N.3 — Qual é o Control Plane que consome este execution plane, e por qual contrato?

O prompt pressupõe um Control Plane separado. O repositório não tem nenhum registro dele.

- Ele existe (outro repo, outro produto), está planejado, ou é a change
  `openspec/changes/add-control-panel/` (parada desde 2026-06-28)?
- Se existe: o contrato é o STOMP+REST atual (`/user/queue/tasks` + claim/status/logs) ou um novo?
- O Kanban/billing/RBAC do backend Spring **saem** (extração de ~36k linhas com acoplamento JPA
  `Task→Project→Organization`), ou o backend Spring **é** o Control Plane e o execution plane é só o
  `client/`? Essas duas leituras levam a arquiteturas incompatíveis.

### N.4 — O re-escopo foi decidido? Onde?

Não há ADR, RFC, issue ou doc registrando-o, e `CONSTITUTION.md` + `openspec/project.md` afirmam o
contrário. Se a decisão foi tomada, **ela existe apenas fora do repositório** — o que torna todo o
corpus SDD ativamente enganoso para qualquer colaborador (ou agente) que o leia.

### N.5 — Modelo de tenancy pretendido

Duas leituras incompatíveis convivem hoje:

- **(a) "um host de daemon por tenant"** — sugerido por `client/deploy/README.md` e pelo despacho por
  e-mail de usuário. Se for esta, B05 deixa de ser P1 e vira requisito de **provisionamento** — mas
  precisa ser enforced e documentado, não implícito.
- **(b) "um pool multi-tenant"** — sugerido pelo Helm chart, pelo warm pool e pela linguagem de SaaS
  do README. Se for esta, B05 é bloqueante duro e o warm pool **precisa** de tenant-awareness antes
  de qualquer cliente.

Não dá para endurecer o isolamento sem saber qual das duas é o alvo.

### N.6 — Perguntas menores, mas que travam decisões

- O `mobile/` e o `desktop/` estão descontinuados ou pausados? (5 e 3,5 meses parados; `desktop/`
  tem 54 linhas de Rust.) Se descontinuados, saem do escopo de manutenção e de auditoria de licença.
- Billing (Stripe) é para valer? Backend + webhook público existem, UI não. Se não é para valer, é
  superfície de ataque pública sem produto.
- `squad-maps/` (não rastreado, com `node_modules`, código MIT de terceiro) é para entrar no repo ou
  é ferramenta local?

---

## EVIDÊNCIA — saídas literais dos comandos de infraestrutura (seção F)

> **Aviso de escopo:** estes comandos rodaram na **máquina de desenvolvimento** (Mac mini M4 com
> Colima), que é o host de execução real hoje (F.1). **Não representam nenhum host de produção** —
> nenhum foi identificado no repositório. Ver N.1 para o que precisa ser rodado no host real.
> Os comandos Linux-específicos falham porque o host é macOS; incluí os equivalentes.

```
### uname -a
Darwin Mac-mini-de-edson.local 25.5.0 Darwin Kernel Version 25.5.0: Tue Jun  9 22:26:22 PDT 2026; root:xnu-12377.121.10~1/RELEASE_ARM64_T8132 arm64

### ls -l /dev/kvm
ls: /dev/kvm: No such file or directory

### egrep -c '(vmx|svm)' /proc/cpuinfo
egrep: /proc/cpuinfo: No such file or directory

### systemd-detect-virt
(eval):1: command not found: systemd-detect-virt

### nproc / free -g
(eval):1: command not found: nproc
(eval):1: command not found: free

### equivalentes macOS: sysctl -n machdep.cpu.brand_string hw.ncpu hw.memsize ; sw_vers
Apple M4
10
17179869184
ProductName:		macOS
ProductVersion:		26.5.2
BuildVersion:		25F84

### docker info | grep -iE 'runtime|storage|kernel|server version|operating system|cgroup|architecture|total memory|cpus'
 Server Version: 29.5.2
 Storage Driver: overlayfs
 Cgroup Driver: cgroupfs
 Cgroup Version: 2
 Runtimes: io.containerd.runc.v2 runc
 Default Runtime: runc
  cgroupns
 Kernel Version: 6.8.0-117-generic
 Operating System: Ubuntu 24.04.4 LTS
 Architecture: aarch64
 CPUs: 2
 Total Memory: 3.813GiB

### docker version (trecho)
Client: Docker Engine - Community
 Version:           29.6.2
 API version:       1.54 (downgraded from 1.55)
 Go version:        go1.26.5
 Git commit:        dfc4efb1e2
 Built:             Thu Jul 16 16:09:24 2026
 OS/Arch:           darwin/arm64
 Context:           default

Server: Docker Engine - Community
 Engine:
  Version:          29.5.2
  API version:      1.54 (minimum version 1.40)
  Go version:       go1.26.3
  Git commit:       568f755
  Built:            Wed May 20 14:39:25 2026
  OS/Arch:          linux/arm64
  Experimental:     false
 containerd:
  Version:          v2.2.4

### colima status
time="2026-08-10T21:35:55-03:00" level=info msg="colima is running using macOS Virtualization.Framework"
time="2026-08-10T21:35:55-03:00" level=info msg="arch: aarch64"
time="2026-08-10T21:35:55-03:00" level=info msg="runtime: docker"
time="2026-08-10T21:35:55-03:00" level=info msg="mountType: virtiofs"
time="2026-08-10T21:35:55-03:00" level=info msg="docker socket: unix:///Users/edsonmartins/.colima/default/docker.sock"
time="2026-08-10T21:35:55-03:00" level=info msg="containerd socket: unix:///Users/edsonmartins/.colima/default/containerd.sock"

### docker images | grep -i squadx
ghcr.io/edsonmartins/squadx.dev/backend:staging-smoke   f831bf9b0892        296MB       296MB
squadx-backend:staging-smoke                            c8bcd330b9b8        296MB       296MB
squadx-frontend:staging-smoke                           d80b12d0230a       80.2MB      80.2MB
squadx/agent:latest                                     f60dfc140dd7       5.01GB      1.29GB
squadx/agent:live                                       e6d8794aff56       5.01GB      1.29GB
squadx/egress-proxy:latest                              bcdda917fcdd        133MB       30.5MB

### docker ps -a | grep -i squadx
(nenhum container squadx em execução ou parado)
```

### Comandos de verificação adicionais (seções D, I, K)

```
### git grep -n "docker\.sock|/var/run/docker"   → 17 hits, NENHUM no container do agente
docker-compose.yml:166:      - /var/run/docker.sock:/var/run/docker.sock:ro     # serviço: promtail (profile monitoring)
client/squadx_client/cli/doctor.py:137,138,183,184                              # descoberta do socket Colima (host)
scripts/{homolog-client-host,homolog-local-smoke,install-mac-client,smoke-mac}.sh  # DOCKER_HOST do daemon (host)
documentos/HOMOLOGACAO-LOCAL-DOCKER.md:22,86                                    # documentação

### git log --all -p | grep -E "sk-[A-Za-z0-9]{20,}|AKIA[0-9A-Z]{16}|ghp_[A-Za-z0-9]{30,}|BEGIN .* PRIVATE KEY"
(zero matches)

### git ls-files | grep -iE "\.env"
backend/.env.example
client/.env.example
client/deploy/squadx-client.env.example
frontend/.env.example
platform/.env.example

### grep -rn "ROW LEVEL SECURITY|CREATE POLICY" backend/src/main/resources/db/migration/
(zero matches — único hit de "POLICY" é comentário em V36 sobre política de egress)

### client: .venv/bin/python -m pytest -q
744 passed, 2 skipped, 1 warning in 5.06s

### client: .venv/bin/ruff check squadx_client
I001 Import block is un-sorted or un-formatted  --> squadx_client/live/__init__.py:3:1
I001 Import block is un-sorted or un-formatted  --> squadx_client/live/live_api_client.py:3:1
I001 Import block is un-sorted or un-formatted  --> squadx_client/live/session_manager.py:3:1
Found 3 errors. [*] 3 fixable with the `--fix` option.

### gh run list --limit 8
completed  failure  feat(platform): add postgres-live/minio stack…  CI  main  push  2026-08-04T21:10:41Z
completed  failure  refactor(client): extract sandbox_start…        CI  main  push  2026-07-30T16:23:36Z
completed  success  refactor(client): extract sandbox_start…        CI  refactor/…  pull_request
completed  failure  refactor(client): extract AgentSandbox egress…  CI  main  push  2026-07-30T16:16:31Z
completed  success  refactor(client): extract AgentSandbox egress…  CI  refactor/…  pull_request
completed  failure  refactor(client): DockerSandboxSession…         CI  main  push  2026-07-30T15:53:57Z
completed  success  refactor(client): DockerSandboxSession…         CI  refactor/…  pull_request
completed  failure  refactor(client): finish ADR-0009 review…       CI  main  push  2026-07-30T15:42:05Z

### gh run view 30951211478 (jobs)
✓ Kustomize (overlays) 7s
✓ Backend (Spring Boot) 2m8s
X Client (Python) 42s        → passo que falhou: "Lint with ruff"
✓ Frontend (Next.js) 1m6s
- Docker Build in 0s          (skipped: needs client)
- Deploy to Staging in 0s     (skipped)

### seccomp: python3 -c "json.load(open('client/docker/seccomp/agent.json'))"
defaultAction: SCMP_ACT_ERRNO
total syscalls listados: 320
ações presentes: {'SCMP_ACT_ALLOW'}
```

---

*Fim do diagnóstico. Nenhum plano de ação, ADR ou código foi produzido — por escopo.*
