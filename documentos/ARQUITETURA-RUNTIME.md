# Arquitetura de runtime — como o SquadX.dev roda

**Estado alvo (staging/prod):** cluster Kubernetes + host Docker dedicado.  
**Estado já exercitado em dev:** tudo local (Colima) com o mesmo fluxo lógico.

Precedência: **código > este doc > ARCHITECTURE.md (mais antigo/marketing)**.

---

## 1. Visão geral (como fica rodando)

```mermaid
flowchart TB
  subgraph users["Humanos"]
    PM["PM / Tech Lead<br/>Browser"]
    Viewer["Viewer Live<br/>Browser / Mobile"]
  end

  subgraph edge["Borda / Ingress"]
    ING["Ingress + TLS<br/>nginx / cert-manager"]
  end

  subgraph k8s["Cluster Kubernetes"]
    FE["Frontend<br/>Next.js 16"]
    BE["Backend<br/>Spring Boot 3.4 / Java 21"]
    PG[("PostgreSQL 16")]
    RD[("Redis")]
    BE --- PG
    BE --- RD
  end

  subgraph dockerhost["Host Docker dedicado (NÃO é pod)"]
    DAEMON["squadx-client<br/>daemon Python"]
    subgraph sandboxes["Por task"]
      AGENT["Sandbox agent<br/>Docker hardened"]
      EGRESS["Sidecar egress-proxy<br/>allowlist + DNS proxy"]
      AGENT -.->|netns compartilhado| EGRESS
    end
    DAEMON -->|cria / gerencia| AGENT
    DAEMON -->|cria / gerencia| EGRESS
  end

  subgraph external["Externos"]
    LLM["LLM<br/>OpenRouter / OpenAI / Anthropic / …"]
    SUPA["Supabase Realtime<br/>sinalização WebRTC"]
    GH["GitHub<br/>commits / PRs"]
    REG["GHCR<br/>imagens"]
  end

  PM -->|HTTPS REST| ING
  Viewer -->|HTTPS / WSS| ING
  ING --> FE
  ING --> BE
  FE -->|REST + STOMP/SockJS| BE

  DAEMON <-->|STOMP WSS + REST<br/>claim / status / logs| BE
  DAEMON -->|API keys no env do run| LLM
  AGENT -->|egress filtrado| LLM
  AGENT --> GH

  DAEMON -->|VNC→WebRTC| SUPA
  Viewer <-->|WebRTC + signaling| SUPA
  DAEMON -.->|pull images| REG
  k8s -.->|pull images| REG
```

### Papéis em uma frase

| Peça | Onde roda | Função |
|------|-----------|--------|
| **Frontend** | K8s (ou Vercel) | Kanban, settings, Live View UI |
| **Backend** | K8s | API, auth JWT, tasks/executions, STOMP, multi-tenant |
| **Postgres / Redis** | K8s | Estado + cache/filas leves |
| **Client daemon** | **Host com Docker** | Recebe tasks, sobe sandboxes, orquestra agentes |
| **Sandbox agent** | Containers no host | Código do agente (LangGraph ou CLI externa) |
| **Egress sidecar** | Container irmão | Firewall de saída (default-deny + allowlist) |
| **LLM** | SaaS | Inteligência (chaves no daemon / sandbox) |
| **Supabase** | Cloud | Sinalização do Live View (não é o “core” de tasks) |

---

## 2. Por que o client **não** fica no Kubernetes

O daemon precisa do **Docker do host** (`docker.from_env()`) para criar sandboxes irmãos + sidecar de rede.

Rodar isso *dentro* do cluster exigiria Docker socket no pod (quase root no node) ou DinD privilegiado. A arquitetura escolhida:

```text
  [ K8s: UI + API + DB ]  ──STOMP/WSS──►  [ Host: daemon + sandboxes ]
```

Código/docs: `client/deploy/README.md`.

---

## 3. Fluxo de uma task (runtime)

```mermaid
sequenceDiagram
  autonumber
  actor User as Humano
  participant FE as Frontend
  participant BE as Backend
  participant DB as PostgreSQL
  participant D as Client daemon
  participant S as Sandbox + egress
  participant LLM as LLM provider

  User->>FE: Cria task / Start execution
  FE->>BE: POST /tasks, POST /executions
  BE->>DB: Persiste task + execution PENDING
  BE-->>D: STOMP /user/queue/tasks<br/>(ou daemon faz poll /executions/pending)

  D->>BE: claim execution
  BE->>DB: RUNNING
  D->>S: Cria agent (+ egress sidecar)
  D->>LLM: Analyze / plan / agent loop
  S->>LLM: Tools no container (se rede allowlist)
  D->>BE: status + logs (STOMP)
  BE->>DB: Atualiza execution
  BE-->>FE: WebSocket updates
  FE-->>User: Progresso / custo / status

  opt Live View
    D->>S: VNC no container :live
    D-->>User: WebRTC via Supabase signaling
  end
```

---

## 4. Staging vs produção (deploy)

```mermaid
flowchart LR
  subgraph ci["GitHub Actions"]
    TEST["lint / test"]
    IMG["build + push GHCR"]
    DEP["deploy-staging"]
  end

  subgraph ghcr["ghcr.io/..."]
    I1["backend / frontend"]
    I2["client / agent / egress-proxy"]
  end

  subgraph stg["Namespace squadx-staging"]
    SFE["frontend:staging-*"]
    SBE["backend"]
    SPG["postgres"]
    SRD["redis"]
  end

  subgraph prod["Namespace squadx"]
    PFE["frontend:latest"]
    PBE["backend"]
  end

  subgraph host["Docker host homolog/prod"]
    SD["squadx-client.service"]
  end

  TEST --> IMG
  IMG --> ghcr
  DEP --> stg
  ghcr --> stg
  ghcr --> host
  SD --> SBE
```

| Ambiente | Namespace | Frontend image | Hosts (alvo) |
|----------|-----------|----------------|--------------|
| Staging | `squadx-staging` | `frontend:staging-<sha>` | `staging.squadx.dev`, `api.staging.squadx.dev` |
| Prod | `squadx` | `frontend:<sha>` / `latest` | `squadx.dev`, `api.squadx.dev` |

`NEXT_PUBLIC_*` é **build-time** → uma imagem de frontend por ambiente.

---

## 5. Dev local (o que já rodamos no smoke)

Mesmo fluxo lógico, colapsado numa máquina:

```mermaid
flowchart TB
  B["Browser opcional"]
  BE["Backend :8080"]
  PG["Postgres :55432"]
  RD["Redis :56379"]
  D["Daemon Python"]
  DK["Colima / Docker"]
  OR["OpenRouter"]

  B --> BE
  BE --> PG
  BE --> RD
  D <--> BE
  D --> DK
  D --> OR
```

Docs: `documentos/HOMOLOGACAO-LOCAL-DOCKER.md`.

---

## 6. Camadas de segurança no sandbox (alvo)

```text
┌─ Host Docker ─────────────────────────────────────────┐
│  iptables DOCKER-USER: bloqueio metadata cloud (opt)  │
│  ┌─ egress-proxy (NET_ADMIN) ───────────────────────┐ │
│  │  dns-proxy + ipset allowlist                     │ │
│  │  default-deny egress                             │ │
│  └──────────────────────┬───────────────────────────┘ │
│  ┌─ agent (cap-drop ALL, non-root, seccomp, ro FS) ─┐ │
│  │  LangGraph / External CLI                        │ │
│  │  workspace / worktree git                        │ │
│  └──────────────────────────────────────────────────┘ │
└───────────────────────────────────────────────────────┘
```

---

## 7. O que *não* está no desenho de runtime atual

- **Control Panel / Pass 5 / MCP workspace** — spec e branches; não no `main` de runtime.
- Client como Deployment k8s — **desabilitado** (`client-deployment.yml.disabled`).
- gVisor / Firecracker como default — opt-in se o binário existir.

---

## Referências

- `CLAUDE.md` — monorepo e fluxo STOMP  
- `infra/k8s/README.md` — overlays staging/prod  
- `client/deploy/README.md` — host do daemon  
- `documentos/PILOTO-ESCOPO.md` — GO local vs NO-GO staging  
- `frontend/public/docs/architecture.svg` — diagrama estático do frontend  
