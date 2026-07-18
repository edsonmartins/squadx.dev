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
kubectl apply -k infra/k8s/overlays/prod      # production — see prerequisites below
```

Staging and prod are fully isolated: different namespace, hosts, and TLS issuer.
The `client` daemon is intentionally not here — it runs on a dedicated Docker host
(see `client/deploy/README.md`).

> **`overlays/prod` prerequisites.** Unlike staging, no pipeline creates its Secret
> or builds/pins its images. Before applying prod: (1) create `squadx-secrets` in the
> `squadx` namespace out of band (see Secrets); (2) deploy the prod frontend image
> `ghcr.io/<repo>/frontend:latest` (built with prod `NEXT_PUBLIC_*` — see below), not
> the staging one. Applying it with neither leaves pods in `CreateContainerConfigError`.

## Frontend image is per-environment

`NEXT_PUBLIC_*` values are **inlined into the browser bundle at build time**, so they
**cannot** be overridden by a runtime ConfigMap. The CI therefore builds one frontend
image per environment with the right build-args: `frontend:<sha>` (prod URLs) and
`frontend:staging-<sha>` (staging URLs). The `deploy-staging` job pins the staging
image; the ConfigMap's `NEXT_PUBLIC_*` only feed the Next.js *server* rewrite at
runtime and must match the image's build-args.

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

**Rotation.** `apply` upserts the Secret, but a pod only re-reads it on restart. A
normal deploy rolls backend/frontend (the image sha changes), so rotated app secrets
land automatically; rotating a secret **without** a code change needs a manual
`kubectl rollout restart deployment/squadx-backend -n <ns>`. Rotating the **DB
password** additionally requires a manual database step — postgres bakes
`POSTGRES_PASSWORD` into its PVC on first init and will not adopt a new value by
restart alone, so backend would then fail to authenticate.

## Config

Non-secret config lives in `base/configmap.yml` (prod-valued). The staging overlay
patches only the host-facing keys (`CORS_ALLOWED_ORIGINS`, `NEXT_PUBLIC_API_URL`,
`NEXT_PUBLIC_WS_URL`) in `overlays/staging/configmap-staging.yml`; everything else
is inherited. The Spring profile stays `prod` — there is no `staging` profile, and
staging should mirror production behaviour.
