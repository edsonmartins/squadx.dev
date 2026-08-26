# ADR-0012 — Isolamento do execution plane na fase interna: runc hardened com gate por evento

- **Status:** Proposto
- **Data:** 2026-08-10
- **Decisores:** Edson Martins · Neimar Chagas
- **Supersede:** `documentos/DECISAO-ARQUITETURAL-SANDBOXING.md` (Fevereiro 2026)
- **Relaciona-se com:** ADR-0008 (egress), ADR-0009 (runtime pluggable), ADR-0011

---

## Contexto

`documentos/DECISAO-ARQUITETURAL-SANDBOXING.md` (fev/2026, marcado "APROVADO PARA IMPLEMENTAÇÃO") define upgrade faseado **Docker hardened → gVisor → Firecracker**, com gatilhos: "100+ execuções/dia" para gVisor, "SOC 2 ou multi-tenant" para Firecracker.

Estado real em 2026-08-10 `[confirmado]`:

- **Os gatilhos existem em `config.py:121-125` e nunca são lidos por nenhum código.** Um gatilho que vive em arquivo de configuração é um gatilho que não dispara.
- gVisor e Firecracker existem como **dois dicionários de uma linha** (`hardening.py:69-78`). `ADR-0009` marca `FIRECRACKER` e `REMOTE` com `implemented=False`, levantando `SandboxNotSupportedError`.
- Host de execução real: **Mac mini M4 com Colima**. `ls -l /dev/kvm` → `No such file or directory`. Docker roda numa VM do Virtualization.framework com **2 vCPU / 3,8 GiB**, `Runtimes: runc` apenas, kernel 6.8 Ubuntu 24.04 aarch64.
- Default do daemon: 4 agentes concorrentes × 2 CPU / 2 GiB = **8 GiB numa VM de 3,8 GiB**. Imagem do agente: 5,01 GB.
- Nenhum host de staging ou produção foi identificado. `PILOTO-ESCOPO.md`: GO local, **NO-GO staging**.

**Modelo de ameaça da fase interna** `[decisão]`, conforme ADR-0011: o tenant é a IntegrAllTech e é um só. O código executado é próprio, as chaves de LLM são do operador, o host é do operador, e não há usuário externo.

## Drivers da decisão

- Firecracker exige KVM. **Não é questão de esforço — é ausência de hardware.**
- O risco residual real na fase interna é **cadeia de suprimentos** (agente executando `install_dependencies` e puxando pacote comprometido), não escape deliberado de código hostil.
- O egress default-deny com allowlist já mitiga bem exatamente esse vetor (ADR-0008, implementado e ligado por default).
- Endurecer o isolamento antes de resolver capacidade é otimizar o carro errado.

## Opções consideradas

### Opção A — Manter runc hardened, sem novo anel

- ✅ Adequado ao modelo de ameaça atual; custo zero.
- ❌ Anel único entre código não confiável e um daemon com privilégio root-equivalente sobre o Docker do host (`squadx-client.service:24-25`, `Group=docker`).

### Opção B — gVisor (`runsc`) agora

- ✅ **Instalável hoje**: a VM do Colima é Ubuntu 24.04 aarch64; `runsc` suporta ARM64 e não exige KVM. É drop-in por `RuntimeClass`/daemon config.
- ❌ Penalidade de I/O de 10–30%, sensível para agente que instala pacotes e compila.
- ❌ Custo de provisionamento e teste sem ameaça correspondente na fase interna.

### Opção C — Firecracker / Kata

- ❌ **Impossível no hardware atual.** Sem `/dev/kvm`, sem virtualização aninhada em Apple Silicon nesse arranjo.

### Opção D — Docker Sandboxes (`sbx`) da Docker Inc.

- ✅ Proxy de credencial host-side, egress default-deny, microVM cross-plataforma; gratuito inclusive para uso comercial.
- ❌ **Login humano obrigatório por sandbox** — identidade atrelada a pessoa, não a workload. Incompatível com despacho programático.
- ❌ A gratuidade cobre **usar**, não **embutir** como camada de um produto. Redistribuição exigiria contrato próprio `[inferência forte]`.
- ❌ Fonte fechada + telemetria por default — contra os invariantes de soberania de dados da IntegrAllTech.
- ✅ **Aproveitável como referência de desenho** (ver abaixo) e como ferramenta nas workstations do time.

## Decisão

`[decisão]` **Opção A na fase interna.** Docker + runc hardened permanece o mecanismo de isolamento do execution plane.

A justificativa não é que runc seja suficiente em abstrato — não é. É que o modelo de ameaça da fase interna é código próprio, com chave própria, em host próprio, sem usuário externo.

`documentos/DECISAO-ARQUITETURAL-SANDBOXING.md` fica **supersedido** e deve ser marcado como tal, encerrando sua condição de fonte de verdade paralela.

### O gate é um evento, não um threshold

`[decisão]` O erro estrutural de fevereiro foi codificar gatilhos em configuração. Esta ADR os substitui por um evento observável por uma pessoa:

> **O gate dispara no primeiro usuário do SquadX fora da IntegrAllTech.**

Nesse dia, os itens abaixo passam a ser **pré-requisito de liberação**, não melhorias de backlog:

| # | Pré-requisito | Origem |
|---|---|---|
| G-1 | **Proxy de credencial**: chave de LLM nunca entra no sandbox; o sidecar de egress injeta o header | B01 |
| G-2 | **gVisor** (`runsc`) como runtime default do agente | B02 |
| G-3 | **VNC autenticado** (`-rfbauth`, senha por sessão) | B03 |
| G-4 | **Tenant-awareness no warm pool** e no ciclo de vida do sandbox | B05 |
| G-5 | **RLS no Postgres** como defesa em profundidade do isolamento aplicativo | B06 |
| G-6 | **Auditoria forense** do que o agente executou | B14 |

### Correções imediatas, independentes do gate

`[decisão]` Estas doem **hoje**, com um autor só, e o código já existe no repositório:

| # | Correção | Estado atual |
|---|---|---|
| I-1 | Instanciar `SandboxLifecycleManager`; trocar `stop()` por `cleanup()` em `daemon.py:487-488` | 257 linhas testadas e **nunca construídas** (`lifecycle.py`); admitido em `test_architecture_guards.py:55-64` |
| I-2 | Ligar `sandbox/paths.py` no caminho Docker para conter `project_path` | Função existe, usada só no backend PROCESS |
| I-3 | Redimensionar a VM do Colima: `--cpu 6 --memory 10 --disk 100` | Host tem 10 cores / 16 GiB; VM usa 2 / 3,8 |
| I-4 | Alinhar `max_concurrent_agents` à capacidade real do host | 4 × 2 GiB numa VM de 3,8 GiB |
| I-5 | Fechar `/topic/live/{code}` no `StompSubscriptionAuthorizer` | Um `case` faltando; cai em `default -> null` → allow |
| I-6 | Validar `security_level` como enum, não string livre | O perfil `development` desliga `read_only`, `no_new_privileges` **e** seccomp |

### Referência de desenho a adotar de `sbx`

`[decisão]` O padrão de **proxy de credencial host-side** do Docker Sandboxes é adotado como alvo de G-1. A infraestrutura já existe no `client/`: sidecar com netns própria, DNS proxy, separação por `--uid-owner`. Falta interceptar os endpoints de LLM no proxy e injetar o header ali, em vez de passar `ANTHROPIC_API_KEY` em `exec_env`.

## Consequências

**Positivas**

- Nenhum trabalho de isolamento é feito antes de haver ameaça correspondente.
- O gate é observável e não pode ser esquecido em arquivo de configuração.
- Uma fonte de verdade paralela e contraditória é encerrada.
- As seis correções imediatas são horas de trabalho sobre código já escrito e testado.

**Negativas, assumidas**

- Anel único de isolamento durante toda a fase interna. Aceito para o modelo de ameaça declarado; **inaceitável fora dele** — daí o gate.
- Se o gate disparar sem aviso (piloto surpresa, demo para cliente), há seis itens em caminho crítico. Mitigação: G-1 e G-3 podem ser antecipados a qualquer momento sem depender de nada.

**Riscos**

- `[inferência]` Cadeia de suprimentos permanece o vetor real. O egress allowlist mitiga exfiltração, mas não impede execução de pacote comprometido dentro do sandbox. Aceito na fase interna.
- **O gate depender de memória humana.** Mitigação: T-0012-7.

## Tarefas derivadas

| # | Tarefa | Prioridade |
|---|---|---|
| T-0012-1 | I-1 — instanciar reaper | P0 |
| T-0012-2 | I-3 + I-4 — capacidade do host e limites do daemon | P0 |
| T-0012-3 | I-5 — fechar `/topic/live/{code}` | P0 |
| T-0012-4 | I-2 — containment de `project_path` | P1 |
| T-0012-5 | I-6 — `security_level` como enum | P1 |
| T-0012-6 | Marcar `DECISAO-ARQUITETURAL-SANDBOXING.md` como supersedido por esta ADR | P1 |
| T-0012-7 | Registrar G-1..G-6 como checklist de liberação, versionado no repo e referenciado no `CLAUDE.md` | P1 |
| T-0012-8 | Teste que executa sandbox real e tenta escapar (hoje os testes de isolamento são mocks) | P2 |

## Referências

- `DIAGNOSTIC-SQUADX-2026-08-10.md` §D, §F, §M (blockers B01–B14)
- ADR-0008 (egress), ADR-0009 (runtime pluggable)
- `documentos/DECISAO-ARQUITETURAL-SANDBOXING.md` (supersedido)
- Docker Sandboxes — https://docs.docker.com/ai/sandboxes/security/credentials/
