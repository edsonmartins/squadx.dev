# Dev LIGHT — Mac mini / laptop (ADR-0009)

**Modo:** client daemon no Mac aponta para o **painel SaaS** (ou API local opcional).  
**Sandbox hoje:** Docker via **Colima** (não exige Docker Desktop).  
**Opcional sem Docker:** `SQUADX_SANDBOX_BACKEND=process` (Seatbelt). Ver [PROCESS-SANDBOX.md](./PROCESS-SANDBOX.md).

## O que este modo *não* instala

- Kubernetes, Helm, Postgres, Redis, Java/Spring, painel Next.js  
- Systemd de produção (use `install-vps.sh` no Linux para Team DOCKER)

## One-shot install

No checkout do monorepo:

```bash
./scripts/install-mac-client.sh              # brew + Colima + venv + env wizard
./scripts/install-mac-client.sh --pull-images  # imagens GHCR em vez de build
./scripts/install-mac-client.sh --non-interactive --skip-images  # CI / re-run
```

O script grava:

| Path | Conteúdo |
|------|----------|
| `~/.squadx/.venv` | Python venv com `squadx-client` |
| `~/.squadx/bin/squadx-client` | symlink |
| `~/.squadx/env.sh` | `PATH` + `DOCKER_HOST` Colima + source do env |
| `~/.squadx/squadx-client.env` | API URL, token, LLM keys (mode 600) |

## Uso diário

```bash
source ~/.squadx/env.sh
squadx-client doctor
squadx-client start -f
```

Smoke:

```bash
./scripts/smoke-mac.sh
```

## Doctor no Mac

O `doctor` reporta:

- `sandbox.backend` — default `docker`
- `docker.cli` / `docker.daemon` — usa `DOCKER_HOST` (Colima socket se setado)
- `docker.colima` — status Colima / socket quando o host é Darwin
- `egress.kernel` — **WARN** em macOS (proof de packet filter é Linux VPS)

## Limitações honestas

| Feature | Dev LIGHT (Mac) |
|---------|-----------------|
| Claim + logs + LLM | ✅ |
| External CLI no sandbox | ✅ (via Colima) |
| Live View | 🟡 se imagem `:live` + Supabase |
| Egress allowlist comprovado | 🟡 Colima VM ≠ homolog Linux |
| Sem Docker | ❌ até PROCESS (#71) |

## Homebrew

A formula em `scripts/Formula/squadx-client.rb` é **placeholder** (sha256 / URL de release).  
**Caminho suportado até release estável:** `install-mac-client.sh` only.

## Team DOCKER (contraste)

Empresa / VPS Linux:

```bash
./scripts/install-vps.sh --pull-images
# ver client/deploy/README.md
```

## Referências

- [ADR-0009](../docs/adr/ADR-0009-sandbox-runtime-pluggable.md) — packaging SKUs  
- [PLANO-ADR-0009](./PLANO-ADR-0009-SANDBOX-E-INSTALADORES.md) — fases  
- [EGRESS-RUNBOOK](../client/deploy/EGRESS-RUNBOOK.md) — proof em Linux  
