# Homologação — verificação em ambiente real

Roteiro para exercitar, num host/cluster de verdade, os itens da auditoria de
homologação que foram **implementados mas ainda não comprovados em host real**.
Cada seção tem: pré-condição, comandos, e o **critério de aprovação**.

Escopo do piloto (IN/OUT): `documentos/PILOTO-ESCOPO.md` · epic [#37](https://github.com/edsonmartins/squadx.dev/issues/37).

> Estado do `main` em 2026-07-29:
> - ✅ **B1** authz cross-tenant · **B2** imagens sandbox + `client/deploy` · **egress** no código
> - ✅ **B3** kustomize overlays staging/prod + secrets via Actions (PR #36)
> - ✅ **#38** GHCR push (`packages:write`) — todas as imagens publicadas no CI
> - ❌ **#39** cluster + `KUBE_CONFIG` / `STAGING_*` ainda **não** configurados
> - ❌ §§1, 2, 4 (egress/live em host real) ainda não exercitados

---

## Seção 0 — Pré-requisito: GHCR + B3  *(feito)*

- Overlays: `infra/k8s/base/` + `overlays/{staging,prod}/`, `secrets.example.yml`
- CI Docker Build publica: backend, frontend, frontend:staging, client, agent, agent:live, egress-proxy
- Job `Kustomize (overlays)` valida o render offline em todo PR

**Critério:** Docker Build verde na `main` (evidência: Actions runs pós-#52/#53).

---

## Seção 0b — Secrets + cluster (#39)  *(bloqueio atual)*

O job `Deploy to Staging` roda preflight e **falha cedo** se faltar secret (em vez de
cair em `localhost:8080`).

1. Ter um cluster k8s (GKE/EKS/DOKS/…) com `cert-manager` + ingress-nginx (ou ajustar o Ingress).
2. Em **Settings → Secrets and variables → Actions**, criar:

| Secret | Notas |
|--------|--------|
| `KUBE_CONFIG` | `base64 < ~/.kube/config` (conteúdo, não path) |
| `STAGING_JWT_SECRET` | ≥32 chars |
| `STAGING_DB_PASSWORD` | |
| `STAGING_SUPABASE_ANON_KEY` / `STAGING_SUPABASE_SERVICE_KEY` | |
| `STAGING_STRIPE_*`, `STAGING_RESEND_API_KEY`, `STAGING_AWS_*` | placeholder ok se não testar a feature |

3. Re-run do workflow CI na `main` (ou push vazio).
4. Packages GHCR private → `imagePullSecret` no cluster **ou** packages public.

**Critério:** pods Running em `squadx-staging`; hosts do ingress = staging; sem `change-me`.

---

## Seção 1 — Egress firewall num host Docker real (RFC-0006 / ADR-0008)

**Pré-condição:** host Linux com Docker e os módulos de kernel `ip_set` / `xt_set`
carregados (`sudo modprobe ip_set xt_set`). macOS não serve (o firewall usa
netns + ipset do host).

```bash
# 1. Construir a imagem do sidecar e as imagens do agente
make build-egress-proxy
make build-sandbox-images        # agent:latest + agent:live + egress-proxy

# 2. Testes de integração (marcados @pytest.mark.integration, exigem Docker)
cd client
SQUADX_DOCKER_IT=1 pytest -m integration tests/test_egress_sidecar.py -v
# opcional, e2e completo de execução em sandbox:
SQUADX_DOCKER_IT=1 pytest -m integration tests/test_e2e_execution.py -v
```

**Smoke manual (default-deny + allowlist):** suba um sandbox com o sidecar e
confirme, de dentro do container do agente:

```bash
# host allowlisted (ex.: API do provedor de LLM) → deve resolver e conectar
curl -sS https://api.anthropic.com/ -o /dev/null -w "%{http_code}\n"
# host NÃO allowlisted → deve FALHAR (timeout/conn refused), não sair
curl -sS --max-time 5 https://example.com/ ; echo "exit=$?"
# tentativa de rebinding/IP direto para metadata → deve ser BLOQUEADA
curl -sS --max-time 5 http://169.254.169.254/ ; echo "exit=$?"
```

**Critério de aprovação:** testes de integração passam; no smoke, host allowlisted
conecta, host fora da allowlist e o IP de metadata (169.254.169.254) **falham**.

---

## Seção 2 — Build real das imagens do sandbox

**Pré-condição:** Docker no host.

```bash
make build-sandbox-images
docker images | grep -E "squadx/(agent|egress-proxy)"
# Esperado: squadx/agent:latest, squadx/agent:live, squadx/egress-proxy:latest
```

Publicação no ghcr (é o que o `release.yml` faz num tag) — só se for validar o push:

```bash
# requer login: echo $GHCR_TOKEN | docker login ghcr.io -u <user> --password-stdin
docker tag squadx/agent:latest ghcr.io/edsonmartins/squadx.dev/agent:test && \
  docker push ghcr.io/edsonmartins/squadx.dev/agent:test
```

**Critério de aprovação:** as três imagens existem localmente; (se testar push) o
ghcr aceita.

---

## Seção 3 — Deploy de staging isolado (kustomize) + secrets  *(requer §0b)*

**Pré-condição:** §0b (secrets + cluster). O CI já aplica o overlay em push na `main`.

Aplicação manual (o mesmo que o CI faz):

```bash
# valida a montagem do overlay ANTES de aplicar (não precisa de cluster)
kubectl kustomize infra/k8s/overlays/staging | less

# aplica no cluster
kubectl create namespace squadx-staging --dry-run=client -o yaml | kubectl apply -f -
# (secrets: em manual, crie o squadx-secrets como no ci.yml deploy-staging)
kubectl apply -k infra/k8s/overlays/staging

# conferir isolamento
kubectl get ns squadx-staging
kubectl get pods,svc,ingress -n squadx-staging
kubectl get ingress -n squadx-staging -o jsonpath='{.items[*].spec.rules[*].host}{"\n"}'
```

**Critério de aprovação:** recursos sobem em `squadx-staging` (NÃO em `squadx`);
os hosts do ingress são os de staging; o issuer é `letsencrypt-staging`; nenhum
`change-me` no `squadx-secrets` (`kubectl get secret squadx-secrets -n
squadx-staging -o yaml` traz valores reais em base64).

---

## Seção 4 — Smoke da live-view (VNC → WebRTC) ponta a ponta

**Pré-condição:** backend + frontend no ar; **daemon do client rodando num host
com Docker** (não é pod — ver `client/deploy/README.md`, systemd `squadx-client.service`).

Passos:
1. Configurar e subir o daemon no host: `client/deploy/squadx-client.env.example`
   → `.env`, instalar o `squadx-client.service`, `systemctl start squadx-client`.
2. No dashboard, criar uma task e dispará-la para esse client.
3. Acompanhar: o daemon cria o sandbox, o VNC do sandbox é transcodificado para
   WebRTC e publicado; no frontend, abrir o detalhe da task → **Watch Live**
   (rota `/live/{code}`).

**Critério de aprovação:** o stream aparece no `/live/{code}` com a tela do agente
ao vivo; o botão **Stop** cancela a execução; o progresso mostra o log real (não
mais o texto fake) — comportamento entregue nos PRs #34/#32.

---

## Resumo dos critérios

| # | Área | Aprovado quando… | Status (2026-07-29) |
|---|------|------------------|---------------------|
| 0 | GHCR + B3 | overlays no main + imagens publicadas | ✅ |
| 0b | Secrets + cluster | preflight OK; pods em `squadx-staging` | ❌ #39 |
| 1 | Egress | integração passa; fora-allowlist e metadata falham | ❌ #41 |
| 2 | Imagens no host | `agent` / `agent:live` / `egress-proxy` no host do client | ❌ #40 |
| 3 | Staging deploy | hosts staging, sem `change-me` | ❌ #39 |
| 4 | Live-view | stream em `/live/{code}`, Stop funciona | ❌ #42 |
