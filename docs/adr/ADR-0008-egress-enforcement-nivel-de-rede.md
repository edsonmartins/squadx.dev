# ADR-0008 — Enforcement de egress no nível de rede (não iptables in-container)

## Status

Aceito — 2026-07-13. **Fase 0 implementada** (2026-07-13): bloqueio host-side do metadata via
`client/squadx_client/docker/egress_guard.py` (`ensure_cloud_metadata_blocked`, regra DROP idempotente
na chain `DOCKER-USER`), gate `settings.block_cloud_metadata` (default on), aplicado uma vez em
`DockerManager.connect`; degrada **ruidosamente** (log ERROR) quando o host não pode aplicar.

**Fase 1 implementada e ligada por default** (2026-07-17) — ver RFC-0006: sidecar de egress com netns
compartilhado, default-deny + allowlist por domínio via dns-proxy vivo (o agente só enxerga esse
resolver; respostas permitidas são fixadas num ipset), policy **por squad** vinda do backend, e
composição com o warm pool (pares agente+sidecar pré-criados). Fail-closed: um run cuja policy não
possa ser aplicada aborta. **Não verificado** ponta-a-ponta em host real ainda (teste `integration`
escrito, não executado).

**Fase 2 (gVisor) pendente.** Nota: `settings.auto_upgrade_runtime` / `gvisor_threshold` /
`firecracker_threshold` existem mas **não têm consumidor** — `resolve_runtime()` decide por presença
de binário e nunca os lê. Estão em quarentena explícita em `client/tests/test_architecture_guards.py`
para que a lacuna não passe por implementada.

## Contexto

O SquadX executa **código autoral do agente** dentro de sandboxes Docker. O egress da rede é a
fronteira #2 do threat model (`documentos/THREAT-MODEL.md`) e o gap **[ALTO] #1** dos riscos
residuais: hoje o egress é **irrestrito por default**, o que abre o vetor de exfiltração e de
**SSRF → credenciais de cloud** via endpoint de metadata (`169.254.169.254` /
`metadata.google.internal`).

Existe um motor de política de egress no client (`client/squadx_client/docker/network_policy.py`:
`NetworkPolicy`, `EgressRule`, presets `POLICY_NONE`/`POLICY_PACKAGE_MANAGERS`/`POLICY_FULL_ACCESS`,
`generate_network_setup_script`, e um esboço de `EgressSidecarConfig`). **Na prática ele é código
morto**, por três razões concretas:

1. **Nenhum caller passa `network_policy=`** ao criar o sandbox (`daemon.py`,
   `orchestrator/nodes.py`), então nenhuma política é aplicada.
2. **`enable_vnc` força `network_mode="bridge"`** (`docker/manager.py:173-174`) sobrescrevendo o
   `network_disabled` do hardening — todo sandbox com live-view sobe com rede aberta.
3. **O mecanismo de aplicação é inviável sob o hardening.** `apply_network_setup`
   (`docker/manager.py:370-414`) roda o script iptables via `container.exec_run(["sh", ...])`
   *dentro* do container — mas o container hardened tem `cap-drop ALL` + non-root. `iptables` exige
   `NET_ADMIN` + root, e **uma capability derrubada não pode ser readquirida por exec**. Logo o
   script **sempre falha** — endurecer e política de egress são, hoje, mutuamente exclusivos por
   construção.

Além disso, os presets confundem semântica de produto: `POLICY_NONE` = **deny-all** (quebraria
tudo), `POLICY_PACKAGE_MANAGERS` **não** libera as APIs de LLM (quebraria os agentes), e só
`POLICY_FULL_ACCESS` (allow-all exceto metadata) é operacionalmente sensato hoje.

**Conclusão:** o fix **não é localizável** ("só fiar o caller"). Fiar o caller faz o script rodar e
falhar silenciosamente — pior, porque dá falsa sensação de enforcement. A decisão precisa mover o
ponto de enforcement para **fora** do container não-confiável.

## Decisão

Aplicar egress **no nível da rede do Docker/host**, nunca via iptables executado dentro do container
do agente. O ponto de controle fica em uma superfície privilegiada que o código do agente não pode
tocar. Três camadas, faseadas:

1. **Fase 0 — Baseline metadata-block por default (mínimo viável, alta prioridade).**
   Bloquear `169.254.169.254` e `metadata.google.internal` para **todo** sandbox, por default,
   mantendo o egress legítimo (APIs de LLM, package managers). Implementável já com Docker:
   - criar uma **rede Docker dedicada por run** (`docker network create` com subnet própria) sem rota
     para o link-local de metadata, **ou**
   - uma regra **host-iptables** no bridge da rede do sandbox que dropa o CIDR `169.254.0.0/16` na
     saída (`DOCKER-USER` chain), aplicada pelo **host** (privilegiado), não pelo container.
   Isto fecha o vetor SSRF→credenciais de cloud, que é o pior caso, sem esperar allowlist completa.

2. **Fase 1 — Allowlist de egress via sidecar de firewall (default-deny + allow-domains).**
   Um container **sidecar** privilegiado (mesmo `network namespace` do sandbox, ou gateway
   NAT/DNS-proxy) aplica a `NetworkPolicy`: default-deny + allowlist de domínios (LLM providers,
   registries). O sidecar tem `NET_ADMIN`; o sandbox continua `cap-drop ALL`. Reusa
   `network_policy.py` (`EgressSidecarConfig`, `to_dns_config`, `to_iptables_rules`) — o motor deixa
   de ser código morto, mas roda **no sidecar**, não no container do agente.
   **Design detalhado (implementável):** `docs/rfc/RFC-0006-egress-firewall-sidecar.md` — topologia
   `network_mode=container:<sidecar>`, presets corrigidos, ciclo de vida, fail-closed, rollout opt-in.
   **Implementado atrás de flag** (`SQUADX_EGRESS_SIDECAR`, default off): `egress_sidecar.py` +
   `manager.py`/`sandbox.py`, `POLICY_AGENT_DEFAULT`. Pendente: imagem do proxy + verificação em host Linux.

3. **Fase 2 — Enforcement estrutural (gVisor/Firecracker).** Com `runtimeClass=runsc` (gVisor) ou
   microVM (Firecracker), a política de rede vira propriedade do runtime/host, alinhada ao
   `DECISAO-ARQUITETURAL-SANDBOXING.md` (Docker Hardened → gVisor → Firecracker).

**Higiene imediata (independe de fase):**
- **Remover/renomear os presets enganosos.** `none`=deny-all e `package-managers` sem LLM não devem
  ser oferecidos como default. Default de produção = allow-all-**exceto-metadata** (Fase 0) até a
  allowlist estar validada.
- **Não fiar `apply_network_setup` in-container** — deprecá-lo, pois é incompatível com o hardening.
  Marcar `network_policy.py` como aplicado-via-sidecar, não via `exec_run`.
- **Resolver o conflito `enable_vnc`→`bridge`**: o live-view precisa de bind de porta, não de egress
  aberto. Usar a rede dedicada (Fase 0) com a porta VNC publicada, mantendo o metadata-block.

## Alternativas consideradas

1. **Fiar `network_policy=` nos callers e manter iptables in-container.** Rejeitada — incompatível
   com `cap-drop ALL` + non-root; o script sempre falha. Daria enforcement fantasma.
2. **Rodar o sandbox com `NET_ADMIN` para o iptables in-container funcionar.** Rejeitada — devolver
   `NET_ADMIN` ao container não-confiável **derrota o hardening** (permite manipular a própria rede,
   raw sockets, etc.). O ponto de controle não pode viver dentro da superfície controlada.
3. **Só bloquear metadata (Fase 0) e parar aí.** Aceitável como **primeiro passo** (fecha o pior
   vetor), mas insuficiente contra exfiltração geral. Adotada como Fase 0, não como estado final.
4. **Enforcement no nível de rede/sidecar + caminho para gVisor (escolhido).**

## Consequências

- **Positivas:** fecha o vetor SSRF→metadata por default (Fase 0, baixo esforço); egress default-deny
  real na Fase 1 sem enfraquecer o hardening do container; o motor `network_policy.py` passa a ser
  usado de fato (no sidecar); semântica de preset honesta.
- **Custos:** Fase 0 exige gestão de rede Docker por run (criação/cleanup) ou uma regra host-iptables
  provisionada na infra (`infra/`), fora do container. Fase 1 adiciona um sidecar por run (custo de
  recurso + ciclo de vida). O caminho VNC precisa ser re-testado com a rede dedicada.
- **Riscos:** allowlist muito estreita quebra agentes (mitigar com telemetria de egress negado antes
  de tornar default-deny obrigatório); DNS-based allowlisting é contornável por IP direto (por isso a
  Fase 2 estrutural).
- **Relacionado:** `documentos/THREAT-MODEL.md` (#1 residual, fronteiras #1/#2),
  `documentos/DECISAO-ARQUITETURAL-SANDBOXING.md` (path de isolamento faseado),
  `documentos/LEARNINGS-Lemma.md` (#13-15 hardening), `client/squadx_client/docker/network_policy.py`
  (motor a reusar via sidecar), `docker/manager.py:173-174,370-414` (pontos a corrigir/deprecar).
