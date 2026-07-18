# Homologação — verificação em ambiente real

Roteiro para exercitar, num host/cluster de verdade, os itens da auditoria de
homologação que foram **implementados mas ainda não comprovados** (nenhum foi
rodado com Docker/kubectl aqui). Cada seção tem: pré-condição, comandos, e o
**critério de aprovação** (o que você deve observar para dar OK).

> Estado do `main` em 2026-07-18 (verificado por inspeção):
> - ✅ **B1** (authz cross-tenant — `OrganizationAccessGuard`), **B2** (imagens do
>   sandbox + `client/deploy`), **egress firewall** — presentes no `main`.
> - ❌ **B3** (isolamento de staging + secrets + kustomize) **NÃO está no `main`**.
>   `infra/k8s/` ainda é flat, `secrets.yml` (`change-me`) ainda existe, e o job
>   `deploy-staging` do `ci.yml` ainda faz `kubectl create namespace squadx`
>   (**namespace de PROD**). O B3 vive só no branch `origin/pr/staging-env-and-secrets`.
>   **A Seção 3 depende de mergear o B3 antes.**

---

## Seção 0 — Pré-requisito: mergear o B3 (staging isolado + secrets)

Sem isto, não existe overlay de staging para aplicar e o deploy aponta para o
namespace de produção com `secrets.yml` `change-me`.

```bash
# Abrir PR do branch B3 (rebaseado no main atual) e revisar o diff
git fetch origin
git checkout -B pr/staging-env-and-secrets origin/pr/staging-env-and-secrets
git rebase origin/main            # só toca infra/k8s + ci.yml → não deve conflitar com código
git push --force-with-lease
gh pr create --base main --head pr/staging-env-and-secrets \
  --title "fix(infra): isolate staging (kustomize overlays) + secrets from GitHub Actions (B3)"
```

**Critério de aprovação:** CI verde no PR; após o merge, o `main` passa a ter
`infra/k8s/base/` + `infra/k8s/overlays/{staging,prod}/` e `secrets.example.yml`
(sem `secrets.yml`).

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

## Seção 3 — Deploy de staging isolado (kustomize) + secrets  *(requer Seção 0)*

**Pré-condição:** B3 mergeado; um cluster k8s com `cert-manager` + ingress-nginx;
`KUBE_CONFIG` e os secrets abaixo configurados em **Settings → Secrets and
variables → Actions**.

Secrets do GitHub Actions a criar (o job `deploy-staging` monta o `squadx-secrets`
no namespace `squadx-staging` a partir deles):

| GitHub Actions secret | vira a chave |
|---|---|
| `STAGING_JWT_SECRET` | `JWT_SECRET` (≥32 chars) |
| `STAGING_DB_PASSWORD` | `SPRING_DATASOURCE_PASSWORD` + `POSTGRES_PASSWORD` |
| `STAGING_SUPABASE_ANON_KEY` | `SUPABASE_ANON_KEY` |
| `STAGING_SUPABASE_SERVICE_KEY` | `SUPABASE_SERVICE_KEY` |
| `STAGING_STRIPE_API_KEY` | `STRIPE_API_KEY` |
| `STAGING_STRIPE_WEBHOOK_SECRET` | `STRIPE_WEBHOOK_SECRET` |
| `STAGING_RESEND_API_KEY` | `RESEND_API_KEY` |
| `STAGING_AWS_ACCESS_KEY_ID` | `AWS_ACCESS_KEY_ID` |
| `STAGING_AWS_SECRET_ACCESS_KEY` | `AWS_SECRET_ACCESS_KEY` |

Aplicação manual (o mesmo que o CI faz em push na `main`):

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

| # | Área | Aprovado quando… | Bloqueado por |
|---|------|------------------|---------------|
| 0 | Merge B3 | `main` tem overlays + `secrets.example.yml` | — |
| 1 | Egress | integração passa; fora-da-allowlist e metadata falham | host Docker+xt_set |
| 2 | Imagens | 3 imagens `squadx/*` existem | host Docker |
| 3 | Staging | recursos em `squadx-staging`, sem `change-me` | Seção 0 + cluster |
| 4 | Live-view | stream em `/live/{code}`, Stop funciona | daemon + backend + front |
