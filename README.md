# SquadX.dev

**AI Development Squads as a Service** - Orquestre equipes virtuais de agentes AI especializados para acelerar o desenvolvimento de software da sua empresa.

[![License](https://img.shields.io/badge/license-MIT-blue.svg)](LICENSE)
[![Python](https://img.shields.io/badge/python-3.12+-blue.svg)](https://python.org)
[![Java](https://img.shields.io/badge/java-21+-orange.svg)](https://openjdk.org)
[![Next.js](https://img.shields.io/badge/next.js-16+-black.svg)](https://nextjs.org)
[![Tests](https://img.shields.io/badge/tests-591%2B-brightgreen.svg)]()

---

## O Problema

Empresas de software enfrentam desafios crescentes:

- **Escassez de talentos**: Dificuldade em contratar e reter desenvolvedores qualificados
- **Custos elevados**: Equipes de desenvolvimento representam o maior custo operacional
- **Velocidade de entrega**: Pressao constante para entregar mais rapido
- **Qualidade inconsistente**: Variacao na qualidade do codigo entre desenvolvedores
- **Escalabilidade limitada**: Impossibilidade de escalar a equipe rapidamente para picos de demanda

## A Solucao

**SquadX.dev** e uma plataforma SaaS B2B que permite as empresas "contratar" squads virtuais de agentes AI especializados para desenvolvimento de software.

```
                         DASHBOARD WEB (Next.js 16)
       Kanban | Live View | Analytics | Voice/Video | Calendar
                              |
                              v
                     SQUADX BACKEND (Spring Boot 3.4)
         REST API | WebSocket | SSO/OIDC | RBAC | Billing
                              |
                              v
                SQUADX CLIENT (Python - Ambiente Local)
      LangGraph Orchestration | Docker Sandbox | WebRTC Bridge
                              |
                              v
                    CONTAINERS DOCKER HARDENED
        Seccomp | Read-only FS | gVisor/Firecracker | Network Policy
```

---

## Agentes Especializados

O SquadX disponibiliza 7 tipos de agentes AI, cada um especializado em uma area:

| Agente | Especializacao | Modelo |
|--------|---------------|--------|
| **Coordinator** | Analise de requisitos, planejamento, decomposicao de tasks | Claude Sonnet |
| **Frontend** | React, Next.js, Vue, CSS, TypeScript, a11y | GPT-4o |
| **Backend** | Python, Java, Node.js, APIs, Databases | Claude Sonnet |
| **DevOps** | Docker, Kubernetes, CI/CD, Infrastructure as Code | GPT-4o |
| **QA** | Testes unitarios, integracao, E2E (Playwright, Cypress) | GPT-4o Mini |
| **Database** | PostgreSQL, schema design, migrations, query optimization | Claude Sonnet |
| **Fullstack** | Tarefas cross-cutting que envolvem multiplas areas | GPT-4o |

---

## Stack Tecnologico

| Camada | Tecnologia |
|--------|-----------|
| **Backend** | Spring Boot 3.4, Java 21, PostgreSQL 16, Redis, Flyway (14 migrations) |
| **Frontend** | Next.js 16, React 19, TypeScript 5.7, Tailwind CSS, Zustand, TanStack Query |
| **Client** | Python 3.12, LangGraph, LiteLLM, aiortc, Docker SDK |
| **Mobile** | Expo 52, React Native, expo-router 4 |
| **Desktop** | Tauri v2, Rust, WebView |
| **Streaming** | VNC (RFB), WebRTC mesh, Supabase Realtime signaling |
| **Infra** | Docker, Kubernetes, Helm, nginx TLS 1.3, GitHub Actions CI/CD |
| **Observability** | Prometheus, Grafana, Loki, Tempo, AlertManager, OpenTelemetry |

---

## Features

### Core Platform
- **Kanban Board** com drag-and-drop para gestao de tasks
- **7 agentes AI especializados** com agentic loop e 9 ferramentas (bash, file I/O, git, Python, dependencies)
- **LangGraph orchestration** com state machine (analyze -> plan -> execute -> review)
- **WebSocket real-time** via STOMP/SockJS para updates de progresso
- **Audit logging** completo com AOP aspect

### Live View
- **WebRTC P2P streaming** das telas dos agentes (< 500ms latencia)
- **Voice/Video** mesh com push-to-talk via Supabase Realtime signaling
- **Annotation tools** (drawing, pointing, text) sobre o stream
- **Chat em tempo real** integrado
- **Controle remoto** (keyboard/mouse forwarding)
- **Join Code** de 8 caracteres para compartilhar sessoes

### Seguranca & Sandbox
- **Docker hardened**: `CAP_DROP=ALL`, read-only FS, `no-new-privileges`, seccomp (336 syscalls)
- **3 security levels**: Development, Standard, Maximum
- **Network Policy**: Egress filtering com domain allowlist (none, package-managers, full)
- **Lifecycle manager**: TTL-based expiration, state machine, renewal
- **File I/O robusto**: Tar-based binary-safe via Docker `put_archive`/`get_archive`
- **Metricas internas**: CPU, memory, network, PIDs, block I/O em real-time
- **Runtime upgrades**: Docker (runc) -> gVisor (runsc) -> Firecracker (microVM)
- **Seccomp profile** customizado para agentes de desenvolvimento

### Enterprise
- **SSO/OIDC**: Google, Microsoft, Okta com JIT user provisioning
- **Advanced RBAC**: Custom roles com permission matrix (resource + action)
- **Google Calendar sync**: OAuth2 bidirecional, meeting auto-creation
- **AI Highlights**: Analise de logs com 8 tipos de highlight + summary
- **White-label**: Brand configs por organizacao (cores, logo, dominio custom)
- **Multi-region**: Region config com Helm nodeSelector/topologySpread
- **Billing**: Stripe integration com checkout, webhooks, subscription management
- **Email**: Notificacoes transacionais via Resend
- **Rate limiting**: Redis-based com sliding window

### Infrastructure
- **TLS 1.3**: nginx reverse proxy com HSTS, OCSP, HTTP/2, modern ciphers
- **Kubernetes**: Manifests + Helm chart com ingress, cert-manager, autoscaling
- **CI/CD**: GitHub Actions (lint, test, build, push to GHCR, deploy)
- **Monitoring**: Prometheus + Grafana dashboards + Loki logs + Tempo traces
- **Alerting**: AlertManager com rules para CPU, memory, error rate
- **PWA**: Service worker, manifest com 9 tamanhos de icone, apple-touch-icon

### Multi-Platform
- **Web**: Next.js 16 PWA com offline support
- **Mobile**: Expo/React Native com dashboard, tasks, live view, settings
- **Desktop**: Tauri v2 wrapping o frontend em WebView nativo

---

## Quick Start

### Pre-requisitos

- Docker e Docker Compose
- Java 21+ (com Maven)
- Node.js 20+ (com pnpm)
- Python 3.12+
- PostgreSQL 16+
- Redis 7+

### Desenvolvimento Local

```bash
# Clone o repositorio
git clone https://github.com/edsonmartins/squadx.dev.git
cd squadx.dev

# Inicie os servicos com Docker Compose
docker compose up -d postgres redis

# Backend (porta 8080)
cd backend
./mvnw spring-boot:run

# Frontend (porta 3000)
cd frontend
pnpm install && pnpm dev

# Client
cd client
pip install -e ".[dev]"
squadx-client start
```

### Build da Imagem do Agent

```bash
cd client/docker

# Headless (sem interface grafica)
docker build -f agent.Dockerfile --target base -t squadx/agent:latest .

# Com Live View (VNC + noVNC)
docker build -f agent.Dockerfile --target live-view -t squadx/agent:live .
```

### Docker Compose com TLS

```bash
# Gere certificados self-signed para dev
mkdir -p infra/nginx/certs
openssl req -x509 -nodes -days 365 -newkey rsa:2048 \
  -keyout infra/nginx/certs/key.pem \
  -out infra/nginx/certs/cert.pem \
  -subj "/CN=localhost"

# Inicie com TLS
docker compose -f docker-compose.yml -f infra/nginx/docker-compose.tls.yml up
```

### Desktop App (Tauri)

```bash
cd desktop
pnpm install
pnpm tauri dev    # Desenvolvimento
pnpm tauri build  # Build para distribuicao
```

### Mobile App (Expo)

```bash
cd mobile
npm install
npx expo start
```

---

## Variaveis de Ambiente

### Backend
```bash
SPRING_DATASOURCE_URL=jdbc:postgresql://localhost:5432/squadx
SPRING_DATASOURCE_USERNAME=squadx
SPRING_DATASOURCE_PASSWORD=your_password
JWT_SECRET=your-secret-key-at-least-32-characters
REDIS_HOST=localhost
REDIS_PORT=6379

# SSO (opcional)
SPRING_SECURITY_OAUTH2_CLIENT_REGISTRATION_GOOGLE_CLIENT_ID=...
SPRING_SECURITY_OAUTH2_CLIENT_REGISTRATION_GOOGLE_CLIENT_SECRET=...

# Billing (opcional)
STRIPE_SECRET_KEY=sk_...
STRIPE_WEBHOOK_SECRET=whsec_...

# Email (opcional)
RESEND_API_KEY=re_...

# Google Calendar (opcional)
GOOGLE_CALENDAR_CLIENT_ID=...
GOOGLE_CALENDAR_CLIENT_SECRET=...
```

### Frontend
```bash
NEXT_PUBLIC_API_URL=http://localhost:8080
NEXT_PUBLIC_SUPABASE_URL=https://your-project.supabase.co
NEXT_PUBLIC_SUPABASE_ANON_KEY=your-anon-key

# WebRTC TURN Server (opcional)
NEXT_PUBLIC_TURN_URL=turn:your-turn-server.com:3478
NEXT_PUBLIC_TURN_USERNAME=your-username
NEXT_PUBLIC_TURN_CREDENTIAL=your-credential
```

### Client
```bash
SQUADX_API_URL=http://localhost:8080
SQUADX_API_TOKEN=your-api-token
OPENAI_API_KEY=your-openai-key
ANTHROPIC_API_KEY=your-anthropic-key
SUPABASE_URL=https://your-project.supabase.co
SUPABASE_ANON_KEY=your-anon-key

# Sandbox
SQUADX_SANDBOX_RUNTIME=docker          # docker | gvisor | firecracker
SQUADX_NETWORK_POLICY=package-managers  # none | package-managers | full
SQUADX_SANDBOX_TTL=3600                 # seconds
SQUADX_AGENT_MEMORY_LIMIT=2g
SQUADX_AGENT_CPU_LIMIT=2.0
```

---

## Testes

O projeto possui **591+ testes** distribuidos em 80 arquivos:

### Backend (Java - JUnit 5 + Mockito)
```bash
cd backend
./mvnw test

# 22 services + 20 controllers = 274 testes
```

### Frontend (TypeScript - Vitest + Testing Library)
```bash
cd frontend
npx vitest run

# 20 arquivos = 141 testes
```

### Client (Python - pytest)
```bash
cd client
pytest tests/ -v

# 22 arquivos = 176+ testes
```

| Area | Arquivos | Testes | Cobertura |
|------|----------|--------|-----------|
| Backend Services | 22 | ~160 | 100% dos services |
| Backend Controllers | 20 | ~114 | 100% dos controllers |
| Frontend | 20 | 141 | ~60% componentes |
| Python Client | 22 | ~176 | ~60% modulos |
| **Total** | **84** | **~591** | |

---

## Seguranca do Sandbox

### Container Hardening (Producao)

```bash
docker run \
  --rm --read-only \
  --cap-drop=ALL \
  --security-opt no-new-privileges:true \
  --security-opt seccomp=client/docker/seccomp/agent.json \
  --user 1000:1000 \
  --memory=2g --cpus=2.0 --pids-limit=256 \
  --network=none \
  --tmpfs /tmp:size=100M,noexec,nosuid \
  --tmpfs /run:size=10M,noexec,nosuid \
  -v /workspace:/workspace:rw \
  squadx/agent:latest
```

### Camadas de Seguranca

| Camada | Mecanismo | Status |
|--------|-----------|--------|
| **Capabilities** | `CAP_DROP=ALL` | Producao |
| **Filesystem** | Read-only root + tmpfs noexec | Producao |
| **Privileges** | `no-new-privileges` | Producao |
| **Syscalls** | Seccomp profile (336 syscalls permitidos) | Producao |
| **Network** | Policy-based egress filtering | Producao |
| **Resources** | Memory 2g, CPU 2.0, PIDs 256 | Producao |
| **User** | Non-root (UID 1000) | Producao |
| **Runtime** | gVisor (runsc) | Scaffold |
| **Runtime** | Firecracker (microVM) | Scaffold |

### Network Policies

| Policy | Descricao |
|--------|-----------|
| `none` | Sem acesso a rede (maximo isolamento) |
| `package-managers` | Permite PyPI, npm, Maven, GitHub |
| `full` | Permite tudo exceto cloud metadata endpoints |

---

## Estrutura do Monorepo

```
squadx.dev/
├── backend/                    # Spring Boot 3.4 API
│   ├── src/main/java/dev/squadx/
│   │   ├── config/             # Security, OAuth2, Region, WebSocket
│   │   ├── controller/         # 20 REST controllers
│   │   ├── dto/                # Request/Response DTOs
│   │   ├── model/              # 20+ JPA entities
│   │   ├── repository/         # Spring Data JPA repos
│   │   ├── security/           # JWT, PermissionChecker
│   │   └── service/            # 22 business services
│   └── src/main/resources/
│       └── db/migration/       # V1-V14 Flyway migrations
├── frontend/                   # Next.js 16 Dashboard
│   ├── src/
│   │   ├── app/(dashboard)/    # Dashboard pages
│   │   ├── components/         # 34 React components
│   │   ├── hooks/              # WebRTC, voice/video, chat
│   │   ├── lib/                # API client, utils, supabase
│   │   └── stores/             # Zustand state management
│   └── public/
│       ├── icons/              # PWA icons (9 tamanhos)
│       └── manifest.json       # PWA manifest
├── client/                     # Python Daemon
│   ├── squadx_client/
│   │   ├── agents/             # 7 agentes especializados + tools
│   │   ├── docker/             # Sandbox, hardening, lifecycle,
│   │   │                       #   file_ops, metrics, network_policy
│   │   ├── live/               # Session management
│   │   ├── orchestrator/       # LangGraph state machine
│   │   ├── streaming/          # VNC + WebRTC bridge
│   │   └── websocket/          # STOMP client
│   ├── docker/
│   │   ├── agent.Dockerfile    # Multi-stage (base + live-view)
│   │   └── seccomp/agent.json  # Seccomp profile
│   └── tests/                  # 22 test files
├── mobile/                     # Expo/React Native
│   ├── app/                    # expo-router screens
│   └── lib/                    # API client, auth
├── desktop/                    # Tauri v2
│   ├── src-tauri/              # Rust backend + config
│   └── package.json
├── infra/
│   ├── helm/squadx/            # Helm chart + templates
│   ├── k8s/                    # Kubernetes manifests
│   ├── nginx/                  # TLS 1.3 reverse proxy
│   └── monitoring/             # Prometheus, Grafana, Loki, Tempo
├── documentos/                 # 18 docs (architecture, roadmap, etc.)
└── docker-compose.yml          # Local dev + monitoring profile
```

---

## API Endpoints

### Auth & Users
| Method | Endpoint | Descricao |
|--------|----------|-----------|
| POST | `/api/v1/auth/register` | Registro |
| POST | `/api/v1/auth/login` | Login (JWT) |
| GET | `/oauth2/authorization/{provider}` | SSO login |

### Core Resources
| Method | Endpoint | Descricao |
|--------|----------|-----------|
| CRUD | `/api/v1/organizations` | Organizacoes |
| CRUD | `/api/v1/projects` | Projetos |
| CRUD | `/api/v1/tasks` | Tasks |
| CRUD | `/api/v1/squads` | Squads |
| CRUD | `/api/v1/agents` | Agentes AI |

### Live View & Collaboration
| Method | Endpoint | Descricao |
|--------|----------|-----------|
| CRUD | `/api/v1/live-view/sessions` | Live sessions |
| CRUD | `/api/v1/meetings` | Meetings |
| CRUD | `/api/v1/recordings` | Recordings |

### Enterprise
| Method | Endpoint | Descricao |
|--------|----------|-----------|
| CRUD | `/api/v1/organizations/{id}/rbac` | Custom roles & permissions |
| CRUD | `/api/v1/organizations/{id}/sso` | SSO config |
| CRUD | `/api/v1/calendar-sync` | Google Calendar sync |
| GET | `/api/v1/highlights` | AI session highlights |
| CRUD | `/api/v1/branding` | White-label config |
| GET | `/api/v1/regions` | Multi-region info |

### Operations
| Method | Endpoint | Descricao |
|--------|----------|-----------|
| POST | `/api/v1/billing/checkout` | Stripe checkout |
| POST | `/api/v1/billing/webhook` | Stripe webhook |
| GET | `/api/v1/audit-logs` | Audit trail |
| CRUD | `/api/v1/approvals` | Approval workflow |
| GET | `/api/v1/executions` | Execution logs |
| GET | `/health` | Health check |
| WS | `/ws` | WebSocket (STOMP/SockJS) |

---

## Roadmap

### Phase 1 - MVP (Completed)
- [x] Backend REST API + WebSocket
- [x] Frontend Kanban + dashboard
- [x] 7 agentes AI especializados
- [x] Docker sandbox hardened
- [x] Live View (VNC -> WebRTC)
- [x] Chat + controle remoto
- [x] Rate limiting + audit logging
- [x] Billing (Stripe) + Email (Resend)
- [x] Approval workflow
- [x] Recording (S3)

### Phase 2 - Enterprise (Completed)
- [x] SSO/OIDC (Google, Microsoft, Okta)
- [x] Advanced RBAC (custom roles + permissions)
- [x] Google Calendar sync
- [x] AI Highlights (session analysis)
- [x] CI/CD pipeline (GitHub Actions)
- [x] Kubernetes + Helm chart
- [x] Observability stack (Prometheus, Grafana, Loki, Tempo)

### Phase 3 - Scale (Completed)
- [x] Voice/Video (WebRTC mesh + push-to-talk)
- [x] White-label (branding por organizacao)
- [x] Multi-region (config + Helm topology)
- [x] Mobile app (Expo/React Native)
- [x] Desktop app (Tauri v2)
- [x] PWA (service worker + icons)
- [x] TLS 1.3 (nginx + cert-manager)

### Phase 4 - Advanced Sandbox (Completed)
- [x] Network policy (egress filtering por dominio)
- [x] Sandbox lifecycle (TTL, state machine, renewal)
- [x] File ops (tar-based binary-safe I/O)
- [x] Container metrics (CPU, memory, network, PIDs)
- [x] gVisor runtime scaffold
- [x] Firecracker runtime scaffold

### Future
- [ ] SFU mode para 100+ viewers
- [ ] Marketplace de agentes
- [ ] API publica + SDKs
- [ ] execd daemon injection (image-agnostic sandbox)
- [ ] Kubernetes BatchSandbox (O(1) provisioning)

---

## Contribuindo

Contribuicoes sao bem-vindas!

```bash
# Fork e clone
git clone https://github.com/seu-usuario/squadx.dev.git
cd squadx.dev

# Crie uma branch
git checkout -b feature/minha-feature

# Faca suas alteracoes e commit
git commit -m "feat: minha nova feature"

# Push e abra um PR
git push origin feature/minha-feature
```

### Convencoes de Commit

Usamos [Conventional Commits](https://www.conventionalcommits.org/):

- `feat:` Nova funcionalidade
- `fix:` Correcao de bug
- `docs:` Documentacao
- `refactor:` Refatoracao
- `test:` Testes
- `chore:` Manutencao

---

## Licenca

Este projeto esta licenciado sob a [MIT License](LICENSE).

---

## Contato

- **Website**: [squadx.dev](https://squadx.dev)
- **Email**: team@squadx.dev
- **GitHub**: [github.com/edsonmartins/squadx.dev](https://github.com/edsonmartins/squadx.dev)

---

<p align="center">
  <strong>SquadX.dev</strong> - Transformando a forma como software e desenvolvido.
</p>
