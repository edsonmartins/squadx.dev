# Escopo do piloto / UAT de homologação (runtime)

**Status:** vivo — alinhar com a epic [#37](https://github.com/edsonmartins/squadx.dev/issues/37)  
**Data:** 2026-07-29  
**Público:** aceite de homologação do **runtime** (não do Control Panel).

Precedência em caso de dúvida: **código > CONSTITUTION > ADRs/RFCs > OpenSpec > este doc**.

---

## Objetivo do piloto

Provar ponta a ponta:

> Dashboard → task no Kanban → daemon no host Docker → sandbox hardened → logs/custo → Live View → stop.

Ambiente alvo: **staging isolado** (`squadx-staging`, hosts `*.staging.squadx.dev`), alimentado pelo CI na `main`.

---

## IN (aceite do piloto)

| Área | O que entra |
|------|-------------|
| Auth + multi-tenant | login/register, org membership, 403 cross-org em REST/STOMP |
| Projetos / tasks / Kanban | CRUD, status, dependências básicas |
| Squads / agents | 7 tipos + `EXTERNAL_CLI` (providers suportados no client) |
| Execução | admissão (`idempotency_key`), logs com visibility, follow-up queue |
| Client daemon | host Docker dedicado (`client/deploy/`), claim de task |
| Sandbox | cap-drop, non-root, seccomp, read-only rootfs, resource limits |
| Egress | sidecar default-on + allowlist por squad (policy V36); metadata block |
| Live View | VNC→WebRTC, join code, stop da run, progresso/logs reais |
| Custo | tokens/custo reportados (incl. EXTERNAL_CLI) |
| Infra staging | imagens GHCR, overlay kustomize staging, secrets via Actions |

## OUT (explicitamente fora do UAT atual)

| Área | Motivo |
|------|--------|
| **Control Panel / Pass 5 / MCP workspace** | Código em `feat/cp-*`, **não** no `main` (issue #46) |
| gVisor / Firecracker como default | Só se binário existir; fase 2/3 do threat model |
| Mobile app store / Desktop release | Wrappers; sem gate de homolog equivalente |
| Multi-region real | `RegionConfig` é config, não multi-cluster |
| White-label / Stripe live / SSO Okta full | Código existe; não são critérios de go do piloto |
| Telemetria de egress negado em produção | Residual do threat model |

---

## Gate de GO (checklist)

Fonte: epic #37 + `documentos/HOMOLOGACAO-VERIFICACAO.md`.

1. [x] CI publica imagens no GHCR (#38)
2. [ ] Secrets `STAGING_*` + `KUBE_CONFIG`; pods healthy em `squadx-staging` (#39)
3. [ ] Host Docker com `squadx-client` + imagens sandbox (#40)
4. [ ] Egress comprovado em host real (#41)
5. [ ] Live-view E2E + smoke UAT + authz (#42, #43)
6. [ ] Este doc + README alinhados (#44)

**GO** só com 1–6. Qualquer fail vira issue `bug` com severidade.

---

## Path sem Kubernetes (enquanto #39 não desbloqueia)

Dá para provar **#40** (imagens + daemon) e **#41** (egress IT) sem cluster:

- `documentos/HOMOLOGACAO-LOCAL-DOCKER.md`
- `make homolog-client` (ou `./scripts/homolog-client-host.sh all`)
- Backend local: `docker compose up -d postgres redis` + `./mvnw spring-boot:run`

Live view / UAT completo ainda preferem staging k8s, mas o núcleo sandbox/egress
não precisa esperar ingress.

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
