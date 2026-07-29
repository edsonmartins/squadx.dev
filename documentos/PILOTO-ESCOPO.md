# Escopo do piloto / UAT de homologação (runtime)

**Status:** vivo — alinhar com a epic [#37](https://github.com/edsonmartins/squadx.dev/issues/37)  
**Data:** 2026-07-29 (atualizado após smoke OpenRouter local)  
**Público:** aceite de homologação do **runtime** (não do Control Panel).

Precedência em caso de dúvida: **código > CONSTITUTION > ADRs/RFCs > OpenSpec > este doc**.

---

## Veredito GO / NO-GO (duas faixas)

Há **dois** critérios de aceite. Não misturar.

### A) Aceite **local do runtime** (path sem k8s)

| Resultado | **GO condicional — ACEITO em 2026-07-29** |
|-----------|-------------------------------------------|
| O que vale | API + claim + orquestrador + LLM (OpenRouter) + tools + execução concluída **na máquina de dev** |
| Ambiente | Colima/Docker + Postgres/Redis + backend local + `client` daemon |
| Evidência | Smoke task_id=6: `write_file` `homolog_ok.txt`=`HOMOLOG_OK`; `task_execution_completed`; OpenRouter `gpt-4o-mini`; PRs #52–#58 |
| NÃO vale como | Homologação de **staging**, venda enterprise, ou “prod-ready na nuvem” |

**Condições do GO local:**

1. Usar `client/.env` com LLM real (`OPENROUTER_API_KEY` + `SQUADX_DEFAULT_MODEL=openrouter/...` **ou** OpenAI/Anthropic).
2. Scripts/docs em `documentos/HOMOLOGACAO-LOCAL-DOCKER.md` + `scripts/homolog-local-smoke.sh`.
3. Arbiter pode dar `escalate` (review pedindo atenção humana); o que importa para este aceite é **pipeline e LLM/tools**, não “approve 100% sem crítica”.

### B) Aceite **homologação staging** (UAT oficial)

| Resultado | **NO-GO** (ainda) |
|-----------|-------------------|
| Falta | #39 cluster + secrets, #41 egress Linux, #42 live-view, #43 UAT/authz formal |
| Ambiente alvo | `squadx-staging`, hosts `*.staging.squadx.dev`, client em host Docker dedicado |

**GO de staging** só quando a checklist da seção “Gate staging” abaixo estiver 100% ✅.

---

## Objetivo do piloto (visão completa)

Provar ponta a ponta:

> Dashboard → task no Kanban → daemon no host Docker → sandbox hardened → logs/custo → Live View → stop.

- **Já coberto no aceite local (A):** API → task → daemon → LLM/tools → conclusão (sem UI browser E2E, sem live).
- **Ainda só no aceite staging (B):** ambiente isolado na nuvem, live-view, egress comprovado em Linux, UAT authz.

---

## IN (escopo do produto no piloto)

| Área | Aceite local (A) | Aceite staging (B) |
|------|------------------|--------------------|
| Auth login/JWT | ✅ exercitado | ✅ revalidar |
| Project + task + execution/admission | ✅ | ✅ |
| Client claim (STOMP + poll) | ✅ | ✅ |
| LLM nativo + tools (OpenRouter ok) | ✅ | ✅ |
| Custo reportado no run | ✅ parcial (~$0.04 no smoke) | ✅ |
| Imagens sandbox build/publish | ✅ local + ✅ GHCR CI | pull no host |
| Sandbox hardening (cap-drop etc.) | 🟡 código + imagem; não auditado formal | 🟡/✅ |
| Egress firewall packet proof | ❌ | ✅ #41 |
| Live View | ❌ | ✅ #42 |
| Multi-tenant 403 formal | ❌ | ✅ #43 |
| Staging k8s isolado | ❌ | ✅ #39 |

## OUT (explicitamente fora)

| Área | Motivo |
|------|--------|
| **Control Panel / Pass 5 / MCP workspace** | Código em `feat/cp-*`, **não** no `main` (#46) |
| gVisor / Firecracker como default | Scaffold; fase 2/3 |
| Mobile store / Desktop release | Wrappers |
| Multi-region real | Config, não multi-cluster |
| White-label / Stripe live / SSO Okta full | Não são critério de go do runtime local |
| Telemetria de egress negado em produção | Residual threat model |

---

## Gate de GO

### Gate local (A) — **GO em 2026-07-29**

1. [x] CI publica imagens no GHCR (#38)
2. [x] Host Docker local: imagens + daemon claim (#40)
3. [x] Escopo/docs essenciais (#44) + path local documentado
4. [x] Smoke LLM ponta a ponta (OpenRouter + tools + `task_execution_completed`)
5. [x] Kustomize overlays validam no CI (#45)

### Gate staging (B) — **NO-GO**

1. [ ] Secrets `STAGING_*` + `KUBE_CONFIG`; pods healthy em `squadx-staging` (#39)
2. [ ] Egress comprovado em host Linux real (#41)
3. [ ] Live-view E2E (#42)
4. [ ] Smoke UAT + authz cross-org (#43)
5. [ ] Client em host dedicado (systemd) apontando para API de staging

---

## Path sem Kubernetes (aceite A)

- `documentos/HOMOLOGACAO-LOCAL-DOCKER.md`
- `scripts/homolog-local-smoke.sh` / `scripts/homolog-client-host.sh`
- LLM: `OPENROUTER_API_KEY` + `SQUADX_DEFAULT_MODEL=openrouter/...` (ou OpenAI/Anthropic)

## Pré-requisitos operacionais (#39)

Sem cluster Kubernetes + secrets, o job `Deploy to Staging` falha no preflight (proposital).

Ver lista completa em `infra/k8s/README.md`. Mínimo:

| Secret | Uso |
|--------|-----|
| `KUBE_CONFIG` | kubeconfig **base64** com acesso ao cluster de homolog |
| `STAGING_JWT_SECRET` | ≥32 chars |
| `STAGING_DB_PASSWORD` | Postgres + datasource |
| `STAGING_SUPABASE_*` | live view / auth integration |
| demais `STAGING_*` | Stripe/Resend/AWS — placeholders ok se a feature não for testada |

```bash
# Exemplo: gravar kubeconfig (não commitar o arquivo)
base64 < ~/.kube/config | pbcopy   # macOS → colar no secret KUBE_CONFIG
```

Packages GHCR: se **private**, o cluster precisa de `imagePullSecret` (ou tornar public em  
https://github.com/users/edsonmartins/packages).

---

## O que não confundir com “pronto”

- README histórico ainda pode listar features enterprise/marketing; **este doc manda no aceite**.
- OpenSpec `add-control-panel` unchecked no `main` ≠ bug do piloto runtime.
- CI verde em unit tests ≠ smoke em host real (egress/live).
