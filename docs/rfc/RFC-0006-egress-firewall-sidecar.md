# RFC-0006 — Egress firewall sidecar (default-deny + allowlist)

> **Status:** Implementado atrás de flag (`SQUADX_EGRESS_SIDECAR`, default **off**) em 2026-07 —
> `egress_sidecar.py` + wiring em `manager.py`/`sandbox.py`, preset `POLICY_AGENT_DEFAULT`, fail-closed.
> **Pendente:** imagem `squadx/egress-proxy` (dns-proxy) e verificação ponta-a-ponta em host Linux
> (testes de integração marcados `integration`, pulados por default). Rollout: ligar o flag, calibrar
> allowlist por telemetria, então flip do default.
>
> Realiza **ADR-0008** Fase 1. Define como aplicar egress default-deny com allowlist de domínios ao
> sandbox **sem** iptables dentro do container não-confiável, movendo o enforcement para um **sidecar
> privilegiado** que compartilha o network namespace do agente. Reusa o motor já existente em
> `client/squadx_client/docker/network_policy.py` (`NetworkPolicy`, `EgressSidecarConfig`,
> `generate_network_setup_script`), que hoje é código morto. Precedência abaixo de ADR-0008.

## 0. Contexto e restrição central

O container do agente roda com `cap-drop ALL` + non-root (`docker/hardening.py`). `iptables` exige
`NET_ADMIN`+root e **uma capability derrubada não pode ser readquirida por `exec`** — por isso
`apply_network_setup` (`docker/manager.py`), que roda o script iptables *dentro* do container, sempre
falha. **Conclusão do ADR-0008:** o ponto de enforcement precisa viver **fora** da superfície
controlada. A Fase 0 (já entregue, `egress_guard.py`) bloqueia só o metadata cloud no host. Esta fase
entrega o **default-deny + allowlist** completo.

## 1. Topologia — sidecar compartilhando o netns

Para cada run que usa rede, sobem **dois** containers:

```
┌─────────────────────── network namespace (do sidecar) ───────────────────────┐
│  egress-sidecar  (privilegiado: NET_ADMIN)        agent  (cap-drop ALL, non-root)│
│   - iptables OUTPUT: default DROP + allow          - compartilha o netns via     │
│   - dns-proxy (resolve só allowlist)                 network_mode=container:<sc> │
│   - porta VNC publicada aqui                        - NÃO consegue alterar regras│
└──────────────────────────────────────────────────────────────────────────────┘
```

- O **sidecar** é criado e iniciado **primeiro**, dono do netns, com `cap_add=["NET_ADMIN"]`, as
  portas publicadas (VNC), e aplica a política (iptables + dns-proxy) antes do agente subir.
- O **agente** sobe com `network_mode="container:<sidecar_id>"` → compartilha o netns do sidecar.
  Como continua `cap-drop ALL` + non-root, **não pode** modificar as regras que o sidecar instalou.
- Todo egress do agente atravessa o netns do sidecar e é filtrado lá. Não há iptables no container do
  agente — a incompatibilidade da Fase 0 desaparece.

Isto substitui o hack atual `enable_vnc → network_mode="bridge"` (`manager.py:173-174`): a porta VNC
passa a ser publicada **no sidecar**, mantendo o egress restrito.

## 2. Modelo de política (reuso, com presets corrigidos)

Reusar `NetworkPolicy`/`EgressRule` de `network_policy.py`. **Corrigir os presets enganosos**
(ADR-0008 higiene) — o default de produção deve ser utilizável:

```python
POLICY_AGENT_DEFAULT = NetworkPolicy(
    default_action=EgressAction.DENY,
    rules=[
        # LLM providers (os agentes quebram sem isto)
        EgressRule(ALLOW, "api.anthropic.com"),
        EgressRule(ALLOW, "api.openai.com"),
        EgressRule(ALLOW, "generativelanguage.googleapis.com"),
        # git + package registries (herda de POLICY_PACKAGE_MANAGERS)
        EgressRule(ALLOW, "github.com", [80, 443, 22]),
        EgressRule(ALLOW, "*.githubusercontent.com"),
        EgressRule(ALLOW, "*.pypi.org"), EgressRule(ALLOW, "files.pythonhosted.org"),
        EgressRule(ALLOW, "registry.npmjs.org"), EgressRule(ALLOW, "repo1.maven.org"),
        # metadata continua negado (belt-and-suspenders com a Fase 0)
        EgressRule(DENY, "169.254.169.254"), EgressRule(DENY, "169.254.170.2"),
    ],
)
```

- Renomear/deprecar `POLICY_NONE` (é deny-all — engana) e `POLICY_PACKAGE_MANAGERS` (não libera LLM).
- `get_predefined_policy` passa a expor `"agent-default"` (o preset acima), `"deny-all"`,
  `"full"` (allow-all exceto metadata, para debugging). Config `SQUADX_NETWORK_POLICY` default
  `"agent-default"`.
- Allowlist por org/squad no futuro: a política pode vir do backend por run (campo no dispatch),
  caindo no `agent-default` quando ausente.

## 3. Enforcement dentro do sidecar

Duas camadas, ambas no netns do sidecar:

1. **iptables (fail-closed):** `OUTPUT` default `DROP`; `ACCEPT` para `lo`, ESTABLISHED/RELATED, e a
   porta do dns-proxy; `DROP` explícito para os CIDRs de metadata; regras de allow por IP resolvido.
   Base já gerada por `generate_network_setup_script` / `EgressSidecarConfig.to_iptables_rules` —
   agora executadas **no sidecar** (que tem NET_ADMIN), não via `exec_run` no agente.
2. **dns-proxy (allowlist de domínios):** redireciona 53/tcp+udp para um resolver local que só
   responde por domínios da allowlist (`EgressSidecarConfig.to_dns_config` já produz
   `allowDomains`/`denyDomains`/`upstreamDNS`). Domínios wildcard (`*.pypi.org`) resolvidos no proxy;
   bloqueia DoT/DoH (853, e provedores DoH conhecidos) para o agente não furar o proxy.

Combinar as duas fecha o furo "resolver por IP direto" (iptables) e "domínio arbitrário" (dns-proxy).

## 4. Ciclo de vida (mudanças em `docker/manager.py`)

```
create_run_network()            # docker network dedicada por run (cleanup no fim)
create_sidecar()                # egress-proxy image, NET_ADMIN, portas VNC, política via env/mount
start_sidecar() + apply_policy()# iptables + dns-proxy sobem ANTES do agente
create_container(..., network_mode=f"container:{sidecar_id}")  # agente entra no netns do sidecar
# ... run ...
teardown(): stop agente → stop sidecar → remove network
```

- `AgentSandbox`/`DockerManager` ganham o par sidecar↔agente no seu tracking (hoje só rastreia o
  agente). O `LiveStreamInfo` referencia a porta publicada no **sidecar**.
- **Fail-closed:** se o sidecar falhar ao aplicar a política, o run **não inicia** (erro, não degrade
  para rede aberta). Contraste com a Fase 0, que degrada ruidosamente porque só protege metadata; aqui
  a ausência de política significa egress irrestrito, então tem de abortar.

## 5. Superfície de config (`config.py`)

| Setting (alias) | Default | Descrição |
|---|---|---|
| `egress_sidecar_enabled` (`SQUADX_EGRESS_SIDECAR`) | `false` (rollout) → `true` | liga o sidecar |
| `network_policy` (`SQUADX_NETWORK_POLICY`) | `agent-default` | preset ou nome de política |
| `egress_sidecar_image` (`SQUADX_EGRESS_PROXY_IMAGE`) | `squadx/egress-proxy:latest` | imagem do sidecar |
| `egress_fail_open` (`SQUADX_EGRESS_FAIL_OPEN`) | `false` | **nunca** `true` em produção; só debug local |

`block_cloud_metadata` (Fase 0) permanece — protege o caminho sem sidecar e é defesa em profundidade.

## 6. Interação com live-view (VNC→WebRTC)

Como o netns é do sidecar, a porta VNC é publicada no sidecar. O `webrtc_bridge` conecta na porta
publicada do sidecar (mesma mecânica, host diferente do container). Testar o caminho VNC ponta-a-ponta
com o sidecar antes de tornar default (é a maior fonte de regressão).

## 7. Plano de testes

- **Unit (client, pytest):** construção de `POLICY_AGENT_DEFAULT`; `to_iptables_rules`/`to_dns_config`
  cobrem allow/deny/metadata; `create_container` passa `network_mode=container:<id>` quando o sidecar
  está ligado; teardown remove sidecar+network; **fail-closed** quando `apply_policy` falha.
- **Integração (host Linux com Docker, fora do CI padrão — marcar `@pytest.mark.docker`):** dentro do
  agente, `curl https://api.anthropic.com` **passa**, `curl https://evil.example` **falha**,
  `curl http://169.254.169.254` **falha**, e o agente **não** consegue `iptables -F` (sem NET_ADMIN).
- Sinalizar no `log()` qualquer egress negado (telemetria) para calibrar a allowlist antes de tornar
  default-deny obrigatório.

## 8. Rollout

1. Entregar sidecar + `egress_sidecar_enabled=false` (opt-in). Rodar com telemetria de egress negado.
2. Calibrar a allowlist a partir dos negados legítimos observados.
3. Flip do default para `true` quando a taxa de falso-bloqueio estabilizar; documentar como
   behavior-change (igual ao flip de `cli_security_mode=enforce`).
4. Fase 2 (gVisor, RFC futuro) torna o isolamento de rede/syscall estrutural e reduz a dependência do
   sidecar.

## 9. Relacionado

- `docs/adr/ADR-0008-egress-enforcement-nivel-de-rede.md` (decisão; Fase 0 entregue, esta é a Fase 1)
- `documentos/THREAT-MODEL.md` (fronteira #2, residual #1)
- `documentos/DECISAO-ARQUITETURAL-SANDBOXING.md` (Docker Hardened → gVisor → Firecracker)
- `client/squadx_client/docker/network_policy.py` (motor a reusar), `docker/manager.py` (seam),
  `docker/egress_guard.py` (Fase 0, defesa em profundidade)
