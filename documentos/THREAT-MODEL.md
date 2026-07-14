# Threat Model — SquadX.dev (fronteira central: o sandbox)

**Data:** Julho 2026
**Status:** 🟡 Vivo (revisar a cada mudança de fronteira; ver `CLAUDE.md`)
**Contexto:** SquadX orquestra squads de agentes de IA que **executam código autoral do agente**
dentro de sandboxes Docker, com status/logs voltando ao dashboard por STOMP e uma visão ao vivo
VNC→WebRTC. A superfície de segurança central — e a maior objeção de venda enterprise — é o
**sandbox rodando código não-confiável**. Este documento é um artefato de confiança + um mapa
honesto do que está aplicado e do que é risco residual. Precedência abaixo de CONSTITUTION/ADR/RFC.

> Inspirado no `docs/security/threat-model.md` do Lemma (ver `documentos/LEARNINGS-Lemma.md` #21).
> Casa com a decisão de isolamento faseado em `DECISAO-ARQUITETURAL-SANDBOXING.md`
> (Docker Hardened → gVisor → Firecracker).

## 1. Fronteiras de confiança

Legenda de estado: ✅ aplicado · 🟡 parcial/opt-in · ❌ presente no código mas **não** aplicado na
execução real / ausente.

| # | Fronteira | Entrada não-confiável | Controles exigidos | Estado (evidência) |
|---|-----------|----------------------|--------------------|--------------------|
| 1 | **Sandbox Docker** (agente executa código) | código/comandos gerados pelo agente ou pela CLI externa | cap-drop ALL, no-new-privileges, rootfs read-only, non-root, limites de recurso, tmpfs noexec, **seccomp**, egress restrito | ✅ cap-drop/no-new-priv/ro-rootfs/non-root/limites/tmpfs (`docker/hardening.py:96-149`); ❌ **seccomp não aplicado** (perfil existe em `docker/seccomp/agent.json` mas `to_docker_kwargs` omite — `hardening.py:150-151`); 🟡 gVisor/Firecracker só se o binário existir (`hardening.py:436-466`) |
| 2 | **Rede do sandbox** (egress) | tentativa de exfiltração / acesso a metadata cloud | default-deny + allowlist de domínios; bloquear 169.254.169.254 | ❌ **egress aberto por default**: `enable_vnc=True` troca `network_mode` de `none`→`bridge` (`docker/manager.py:173-174`) e o `network_policy=` **não é passado** por nenhum caller (`daemon.py:394`, `orchestrator/nodes.py:383`) — o motor `network_policy.py` é código morto na prática |
| 3 | **Chaves de provider** (env no sandbox) | vazamento de segredo p/ o container | injeção em runtime (nunca na imagem) + allowlist | ✅ `scrub_env` com allowlist das 3 chaves ANTHROPIC/OPENAI/GOOGLE (`agents/security.py:42-58`, `daemon.py:383-392`); imagens sem segredo (grep limpo). 🟡 allowlist é por **nome** de var (segredo em var de nome benigno passa); scrub só no caminho External-CLI |
| 4 | **Prompt do agente** (injection) | instruction-override, exfiltração, acesso a arquivo sensível | detectar e **bloquear** | 🟡 `assess_prompt` detecta os 3 padrões mas **default é `audit` (só loga)** (`agents/security.py:73-110`, `config.py:82`); só no caminho External-CLI, não no nativo |
| 5 | **Artefatos internos da CLI** (`.claude/.codex/.omx/.aider/.opencode`) | poluição de commit / vazamento de histórico | filtrar antes do commit | ✅ `filter_internal_artifacts` (`agents/security.py:115-136`) aplicado em coleta e no commit (`external_cli_agent.py:236`, `nodes.py:766`) |
| 6 | **Worktree / repo git** | escrita fora do escopo, colisão entre runs | isolamento por run + cleanup | ✅ worktree por agente `squadx/<task_id>/<agent>` + cleanup (`git/worktree.py`); 🟡 chaveado por `agent_name` (não run-id) → dois runs do mesmo agente colidem no path (`worktree.py:43`) |
| 7 | **Live view VNC→WebRTC** | enumeração/hijack de stream entre tenants | token por sessão + escopo de org/host | 🟡 **corrigido o escopo de org** (2026-07): `/api/v1/live-view/supabase/**` agora resolve session→task→org e exige membership (`@AuthenticationPrincipal` + `TaskRepository`/`OrganizationMemberRepository`): reads filtram/404 sem vazar, by-task/create/end **403** p/ não-membro (`LiveViewController.java`, 507 testes verdes). Resta o **token por viewer** (assinado/TTL) — Fase 2/gVisor. |
| 8 | **STOMP / WebSocket** (daemon↔backend) | conexão não autenticada, subscrição cross-tenant | rejeitar CONNECT sem token + authz por destino | 🟡 `WebSocketAuthInterceptor` só rejeita token **inválido**; CONNECT **sem** header passa (`WebSocketAuthInterceptor.java:33,59`); sem authz por destino nesta camada — depende de config de broker separada (verificar) |
| 9 | **API REST pública** (multi-tenant) | acesso cross-org | `validateUserAccess` (membership) em toda camada de serviço | ✅ `existsByOrganizationIdAndUserId` aplicado em 12 serviços + 3 controllers; exceção crítica na fronteira #7 |
| 10 | **Admissão de run** (gatilhos duplicados/concorrentes) | replay, corrida, ação sem aprovação | idempotência + follow-up + gate humano | ✅ `RunAdmissionService.admit` (dedup/queue_follow_up/start) + gate de aprovação humana opt-in (`ApprovalService`, migração V35) |
| 11 | **Custo / loop** | loop infinito, gasto ilimitado | teto de ciclos + teto de custo | 🟡 `max_cycles=3` (hard backstop) sempre ativo; `cost_budget_usd` **default None = sem teto** (`orchestrator/state.py:162`); custo é freio pós-subtask, não pré-empta subtask em curso |

## 2. Lacunas conhecidas / riscos residuais (ranqueados)

Ordenados por severidade. Cada um é candidato a issue/fix; alguns são **regressões**, não só "risco aceito".

1. **[ALTO] Egress de rede irrestrito por default — e a abordagem de enforcement é inviável com o
   hardening atual (não é só "código não fiado").** Nenhum caller passa `network_policy=`
   (`daemon.py:394`, `orchestrator/nodes.py:383`) e `enable_vnc` força `bridge`
   (`docker/manager.py:173-174`). **Mas simplesmente fiar não resolve:** `apply_network_setup` roda o
   script iptables via `container.exec_run(["sh", ...])` (`manager.py:399`) *dentro* do container — que
   tem `cap-drop ALL` + non-root. `iptables` exige `NET_ADMIN`+root, e uma capability derrubada **não
   pode** ser readquirida por exec. Logo o iptables in-container **sempre falha** (por isso o motor é
   morto). Além disso os presets confundem: `POLICY_NONE` = **deny-all** (quebraria tudo),
   `POLICY_PACKAGE_MANAGERS` não libera as APIs de LLM (quebraria os agentes), e só `POLICY_FULL_ACCESS`
   (allow-all exceto metadata) é sensato. **Fix correto (arquitetural, não local):** aplicar egress no
   nível de **rede do Docker** (rede dedicada por run sem rota para `169.254.169.254`) ou host-iptables
   no bridge, ou um sidecar de firewall — **não** iptables in-container. Alvo mínimo: bloquear
   `169.254.169.254`/`metadata.google.internal` por default (fecha o vetor de SSRF→credenciais de cloud)
   mantendo o egress legítimo. Renomear os presets (`none`=deny-all é enganoso).

2. **[ALTO → CORRIGIDO parcial] Live-view `/supabase/**` sem escopo de org.** Era: qualquer usuário
   autenticado de qualquer org enumerava (`/supabase/sessions/active`), criava ou encerrava sessões de
   sandbox de outros tenants. **Corrigido (2026-07):** os 5 endpoints supabase agora resolvem
   session→task→org e exigem membership — reads filtram/404 (sem vazar existência), by-task/create/end
   retornam **403** p/ não-membro; `SupabaseLiveSessionService.getSessionById` resolve a task no
   end-by-id. Cobertura em `LiveViewControllerTest` (suite backend 507 verde). **Resta:** token por
   *viewer* (assinado/TTL/escopado) para fechar o hijack de stream de forma forte — Fase 2/gVisor.
   Evidência: `LiveViewController.java`, `SupabaseLiveSessionService.java`.

3. **[MÉDIO] Seccomp não é aplicado aos containers.** O perfil `SCMP_ACT_ERRNO` existe
   (`docker/seccomp/agent.json`) mas `to_docker_kwargs` o omite deliberadamente (só está no helper de
   CLI de debug, não chamado). Containers sobem com o seccomp default do Docker — superfície de syscall
   bem maior que a documentada. **Fix:** aplicar `security_opt=["seccomp=<perfil>"]` via SDK, ou mover
   para a fase gVisor (que dá isolamento de syscall por construção). Evidência: `hardening.py:150-151`
   vs `:355-356`.

4. **[MÉDIO] CONNECT STOMP não exige token.** O interceptor só barra token inválido; CONNECT sem
   `Authorization` passa. Verificar se a config de `WebSocketSecurity`/broker rejeita anônimo; se não,
   clientes não autenticados podem subscrever. **Fix:** rejeitar CONNECT sem principal + authz por
   destino. Evidência: `WebSocketAuthInterceptor.java:33,59`.

5. **[BAIXO-MÉDIO] Defaults seguros-por-config, não seguros-por-default.** `cli_security_mode="audit"`
   (injection é logado, não bloqueado) e `cost_budget_usd=None` (sem teto de gasto). **Fix:** considerar
   `enforce` e um teto de custo default em produção. Secundário: `assess_prompt` só roda no caminho
   External-CLI (não no nativo); `scrub_env` faz allowlist por nome de var. Evidência: `config.py:82`,
   `orchestrator/state.py:162`, `agents/security.py:56-57`.

## 3. Caminho de endurecimento

Alinhado ao `DECISAO-ARQUITETURAL-SANDBOXING.md` e às lições do Lemma (`LEARNINGS-Lemma.md` #13-15):

- **Curto prazo (Fase 1 — Docker Hardened, correções):** fechar #1 e #2 (são regressões/bugs), aplicar
  seccomp (#3), rejeitar STOMP anônimo (#4). Baixo esforço, alto impacto.
- **Médio (Fase 2 — gVisor):** `runtimeClass=runsc` para isolamento de syscall (torna #3 estrutural);
  reaper por ref-count de sessão; token assinado/TTL/escopado para o live-view (fecha #2 de forma forte).
- **Longo (Fase 3 — Firecracker):** microVM por run quando exigir SOC 2 / multi-tenant forte.

## 4. Relacionado

- `documentos/DECISAO-ARQUITETURAL-SANDBOXING.md` (path de isolamento faseado)
- `documentos/LEARNINGS-Lemma.md` (#13-15 hardening; #21 este threat model)
- ADR-0007 / RFC-0005 (hardening do sandbox, attention budget, run admission)
- `documentos/sandboxing-execucao-codigo-ai-agents.md`
