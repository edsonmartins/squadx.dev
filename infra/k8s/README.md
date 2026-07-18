# Kubernetes manifests (kustomize)

Environment-neutral **base** + per-environment **overlays**. Deploy with
`kubectl apply -k`, which has kustomize built in — no extra tooling.

```
base/                     namespace-neutral: config + backend/frontend/redis/postgres
overlays/
  staging/                namespace squadx-staging, staging.squadx.dev, letsencrypt-staging
  prod/                   namespace squadx, squadx.dev, letsencrypt-prod
secrets.example.yml       reference only — NEVER applied (see Secrets below)
client-deployment.yml.disabled   the client runs on a Docker host, not in-cluster
```

## Deploy

```bash
kubectl apply -k infra/k8s/overlays/staging   # homologation
kubectl apply -k infra/k8s/overlays/prod      # production
```

Staging and prod are fully isolated: different namespace, hosts, and TLS issuer.
The `client` daemon is intentionally not here — it runs on a dedicated Docker host
(see `client/deploy/README.md`).

## Secrets

Secrets are **created in-cluster from CI**, never committed. The `deploy-staging`
job (`.github/workflows/ci.yml`) builds `squadx-secrets` from GitHub Actions secrets
before applying the overlay. Add these under **Settings → Secrets and variables →
Actions**:

| GitHub Actions secret | maps to key |
|---|---|
| `STAGING_JWT_SECRET` | `JWT_SECRET` (min 32 chars) |
| `STAGING_DB_PASSWORD` | `SPRING_DATASOURCE_PASSWORD` + `POSTGRES_PASSWORD` |
| `STAGING_SUPABASE_ANON_KEY` | `SUPABASE_ANON_KEY` |
| `STAGING_SUPABASE_SERVICE_KEY` | `SUPABASE_SERVICE_KEY` |
| `STAGING_STRIPE_API_KEY` | `STRIPE_API_KEY` |
| `STAGING_STRIPE_WEBHOOK_SECRET` | `STRIPE_WEBHOOK_SECRET` |
| `STAGING_RESEND_API_KEY` | `RESEND_API_KEY` |
| `STAGING_AWS_ACCESS_KEY_ID` | `AWS_ACCESS_KEY_ID` |
| `STAGING_AWS_SECRET_ACCESS_KEY` | `AWS_SECRET_ACCESS_KEY` |

`secrets.example.yml` lists the keys and points at the stronger production
mechanisms (Sealed Secrets, External Secrets Operator, Vault) if you outgrow
GitHub Actions secrets. For a prod overlay, wire an equivalent secret step (or a
secrets controller) — do not reuse staging credentials.

## Config

Non-secret config lives in `base/configmap.yml` (prod-valued). The staging overlay
patches only the host-facing keys (`CORS_ALLOWED_ORIGINS`, `NEXT_PUBLIC_API_URL`,
`NEXT_PUBLIC_WS_URL`) in `overlays/staging/configmap-staging.yml`; everything else
is inherited. The Spring profile stays `prod` — there is no `staging` profile, and
staging should mirror production behaviour.
