# SquadX.dev

**AI Development Squads as a Service** - Orquestre equipes virtuais de agentes AI especializados para acelerar o desenvolvimento de software da sua empresa.

[![License](https://img.shields.io/badge/license-MIT-blue.svg)](LICENSE)
[![Python](https://img.shields.io/badge/python-3.12+-blue.svg)](https://python.org)
[![Java](https://img.shields.io/badge/java-21+-orange.svg)](https://openjdk.org)
[![Next.js](https://img.shields.io/badge/next.js-16+-black.svg)](https://nextjs.org)

---

## 🎯 O Problema

Empresas de software enfrentam desafios crescentes:

- **Escassez de talentos**: Dificuldade em contratar e reter desenvolvedores qualificados
- **Custos elevados**: Equipes de desenvolvimento representam o maior custo operacional
- **Velocidade de entrega**: Pressão constante para entregar mais rápido
- **Qualidade inconsistente**: Variação na qualidade do código entre desenvolvedores
- **Escalabilidade limitada**: Impossibilidade de escalar a equipe rapidamente para picos de demanda

## 💡 A Solução

**SquadX.dev** é uma plataforma SaaS B2B que permite às empresas "contratar" squads virtuais de agentes AI especializados para desenvolvimento de software.

### Como Funciona

```
┌─────────────────────────────────────────────────────────────┐
│                    DASHBOARD WEB                             │
│  • Crie projetos e defina tasks no Kanban                   │
│  • Acompanhe o progresso em tempo real                      │
│  • Visualize custos e métricas de produtividade             │
│  • Assista agentes trabalhando via Live View                │
└─────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────┐
│                    SQUADX BACKEND                            │
│  • Gerencia projetos, tasks e squads                        │
│  • Orquestra comunicação em tempo real                      │
│  • Controle de acesso e billing                             │
└─────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────┐
│              SQUADX CLIENT (Ambiente Local)                  │
│  • Executa na infraestrutura do cliente                     │
│  • Código permanece 100% local (compliance)                 │
│  • Agentes AI trabalham em containers isolados              │
│  • Streaming de tela via VNC → WebRTC                       │
└─────────────────────────────────────────────────────────────┘
```

### Agentes Especializados

O SquadX disponibiliza 6 tipos de agentes AI, cada um especializado em uma área:

| Agente | Especialização | Modelo |
|--------|---------------|--------|
| 🧠 **Coordinator** | Análise de requisitos, planejamento, code review | Claude Sonnet |
| 🎨 **Frontend** | React, Next.js, Vue, CSS, TypeScript | GPT-4o |
| ⚙️ **Backend** | Python, Java, Node.js, APIs, Databases | Claude Sonnet |
| 🔧 **DevOps** | Docker, Kubernetes, CI/CD, Infrastructure | GPT-4o |
| 🧪 **QA** | Testes unitários, integração, E2E, qualidade | GPT-4o Mini |
| 🔀 **Fullstack** | Tarefas gerais que envolvem múltiplas áreas | GPT-4o |

---

## ✨ Diferenciais Competitivos

### 1. Multi-Agent Coordination
Diferente de ferramentas que usam um único agente, o SquadX orquestra múltiplos agentes especializados trabalhando em paralelo, similar a uma equipe real de desenvolvimento.

### 2. Código 100% Local
O código da sua empresa nunca sai da sua infraestrutura. O SquadX Client executa localmente, garantindo compliance com **LGPD**, **GDPR** e **HIPAA**.

### 3. Transparência Total (Live View)
Acompanhe em tempo real o que cada agente está fazendo através do **Live View** - streaming WebRTC das telas dos agentes com chat integrado.

### 4. Enterprise-Ready
- Controle de acesso granular (RBAC)
- Audit logs completos
- Integração com SSO corporativo
- SLA garantido

### 5. Modelo de Custo Previsível
Pague apenas pelo que usar, com visibilidade completa dos custos por projeto, task e agente.

---

## 📺 Live View - Streaming de Agentes

O SquadX Live permite assistir seus agentes AI trabalhando em tempo real através de streaming WebRTC de baixa latência.

### Arquitetura do Live Streaming

```
┌─────────────────────────────────────────────────────────────┐
│                   Docker Container                           │
│  ┌─────────────┐    ┌─────────────┐    ┌─────────────────┐  │
│  │    Xvfb     │───▶│   x11vnc    │───▶│   VNC Client    │  │
│  │   :99       │    │   :5900     │    │   (RFB Proto)   │  │
│  └─────────────┘    └─────────────┘    └────────┬────────┘  │
└────────────────────────────────────────────────│────────────┘
                                                 │
                                                 ▼
┌─────────────────────────────────────────────────────────────┐
│                   Python Client                              │
│  ┌─────────────────────────────────────────────────────┐    │
│  │              WebRTC Bridge (aiortc)                  │    │
│  │   • Converte frames VNC para MediaStreamTrack       │    │
│  │   • Gerencia peer connections                       │    │
│  │   • Signaling via Supabase Realtime                 │    │
│  └─────────────────────────────────────────────────────┘    │
└─────────────────────────────────────────────────────────────┘
                                 │
                                 ▼ WebRTC (P2P/SFU)
┌─────────────────────────────────────────────────────────────┐
│                   Browser Viewer                             │
│  ┌─────────────────────────────────────────────────────┐    │
│  │              StreamViewer Component                  │    │
│  │   • Video player com controles                      │    │
│  │   • Stats (bitrate, fps, resolução)                 │    │
│  │   • Chat e lista de participantes                   │    │
│  └─────────────────────────────────────────────────────┘    │
└─────────────────────────────────────────────────────────────┘
```

### Features do Live View

- **WebRTC P2P**: Streaming direto para até 25 viewers
- **Baixa Latência**: < 500ms end-to-end
- **Chat Integrado**: Comunicação em tempo real
- **Controles**: Fullscreen, mute, estatísticas
- **Join Code**: Compartilhe sessões com código de 8 caracteres

---

## 🏗️ Casos de Uso

### Para Startups
- Acelere o desenvolvimento do MVP com uma equipe AI disponível 24/7
- Reduza o tempo de contratação e onboarding

### Para Scale-ups
- Escale a capacidade de desenvolvimento sem aumentar headcount
- Mantenha velocidade de entrega durante picos de demanda

### Para Enterprises
- Automatize tarefas repetitivas de desenvolvimento
- Padronize qualidade de código em toda a organização
- Reduza custos operacionais de TI

---

## 📦 Componentes

### Backend (`/backend`)
API REST e WebSocket construída com **Spring Boot 3.4** e **Java 21**.

- Gerenciamento de projetos, tasks e squads
- Autenticação JWT
- WebSocket (STOMP/SockJS) para comunicação real-time
- PostgreSQL para persistência
- API de Live Sessions (local + Supabase)
- **Supabase Integration**: Sincronização com sessions criadas pelo Python Client

### Frontend (`/frontend`)
Dashboard web construído com **Next.js 16** e **React 19**.

- Kanban board para gestão de tasks
- Visualização em tempo real do progresso
- **Live View** com player WebRTC
- Analytics de custos e produtividade
- Interface responsiva com Tailwind CSS

### Client (`/client`)
Daemon Python que executa no ambiente do cliente.

- Orquestração de agentes com **LangGraph**
- Suporte multi-provider (OpenAI, Anthropic, Gemini) via **LiteLLM**
- Execução segura em containers Docker hardened
- **VNC→WebRTC Bridge** para Live Streaming
- Comunicação STOMP/SockJS com o backend

### Agent Image (`/client/docker`)
Imagem Docker otimizada para execução de agentes.

- Ubuntu 22.04 base com Python 3.12, Node.js 20, Java 21
- Xvfb + x11vnc para interface gráfica virtual
- Ferramentas de desenvolvimento (git, vim, build tools)
- Browser headless (Chromium)
- Profiles Seccomp para segurança

---

## 🔒 Segurança

A segurança é prioridade máxima no SquadX. O Client executa agentes em containers Docker com múltiplas camadas de proteção:

```bash
# Exemplo de container hardened
docker run \
  --rm --read-only \
  --cap-drop=ALL \
  --security-opt no-new-privileges:true \
  --security-opt seccomp=/path/to/seccomp.json \
  --user 1000:1000 \
  --memory=2g --cpus=2.0 --pids-limit=256 \
  --network=none \
  --tmpfs /tmp:size=100M,noexec,nosuid \
  -v /workspace:/workspace:rw \
  squadx/agent:latest
```

### Camadas de Segurança

| Camada | Proteção |
|--------|----------|
| **Capabilities** | `CAP_DROP=ALL` - Remove todas as capabilities Linux |
| **Filesystem** | Read-only root, tmpfs com noexec |
| **Privileges** | `no-new-privileges` - Bloqueia escalação |
| **Syscalls** | Seccomp profile restritivo |
| **Network** | Isolamento total (`--network=none`) |
| **Resources** | Limites de memória, CPU e processos |

### Roadmap de Segurança

- **Fase 1 (Atual)**: Docker Hardened ✅
- **Fase 2**: gVisor (runsc) para isolamento de kernel
- **Fase 3**: Firecracker para micro-VMs

---

## 🚀 Quick Start

### Pré-requisitos

- Docker e Docker Compose
- Java 21+
- Node.js 20+ (com pnpm)
- Python 3.12+
- PostgreSQL 16+

### Desenvolvimento Local

```bash
# Clone o repositório
git clone https://github.com/edsonmartins/squadx.dev.git
cd squadx.dev

# Inicie os serviços com Docker Compose
docker-compose up -d

# Backend (porta 8080)
cd backend
./mvnw spring-boot:run

# Frontend (porta 3000)
cd frontend
pnpm install
pnpm dev

# Client
cd client
pip install -e ".[dev]"
squadx-client start
```

### Build da Imagem do Agent

```bash
cd client/docker
docker build -f agent.Dockerfile -t squadx/agent:latest .
```

### Configurar Supabase

O Live Streaming usa Supabase para signaling WebRTC. Crie um projeto em [supabase.com](https://supabase.com) e execute a migration:

```bash
# Aplicar schema via SQL Editor no Supabase Dashboard
# ou usando supabase CLI:
cd client
supabase db push
```

O arquivo de migration está em `client/supabase/migrations/001_live_sessions.sql`.

### Variáveis de Ambiente

Copie os arquivos de exemplo e configure:

```bash
# Frontend
cp frontend/.env.example frontend/.env.local

# Client
cp client/.env.example client/.env
```

#### Backend
```bash
SPRING_DATASOURCE_URL=jdbc:postgresql://localhost:5432/squadx
SPRING_DATASOURCE_USERNAME=squadx
SPRING_DATASOURCE_PASSWORD=your_password
JWT_SECRET=your-secret-key-at-least-32-characters
```

#### Frontend
```bash
NEXT_PUBLIC_API_URL=http://localhost:8080
NEXT_PUBLIC_SUPABASE_URL=https://your-project.supabase.co
NEXT_PUBLIC_SUPABASE_ANON_KEY=your-anon-key
```

#### Client
```bash
SQUADX_API_URL=http://localhost:8080
SQUADX_API_TOKEN=your-api-token
OPENAI_API_KEY=your-openai-key
ANTHROPIC_API_KEY=your-anthropic-key
SUPABASE_URL=https://your-project.supabase.co
SUPABASE_SERVICE_KEY=your-service-key
```

---

## 📐 Arquitetura Técnica

### Stack Tecnológico

| Camada | Tecnologia |
|--------|-----------|
| **Backend** | Spring Boot 3.4, Java 21, PostgreSQL, Redis, Supabase |
| **Frontend** | Next.js 16, React 19, TypeScript, Tailwind CSS, Supabase |
| **Client** | Python 3.12, LangGraph, LiteLLM, aiortc, Docker SDK |
| **Streaming** | VNC (RFB), WebRTC, Supabase Realtime |
| **Infraestrutura** | Docker, Kubernetes (produção) |

### Fluxo de Execução de Task

```
1. Usuário cria task no Dashboard
                │
                ▼
2. Backend persiste e notifica Client via WebSocket
                │
                ▼
3. Client recebe task e inicia orquestração
                │
                ▼
4. Coordinator Agent analisa e cria plano de execução
                │
                ▼
5. Subtasks são delegadas para agentes especializados
                │
                ▼
6. Agentes executam em containers Docker isolados
   └── Live View streaming disponível via WebRTC
                │
                ▼
7. Resultados são commitados no Git local
                │
                ▼
8. Client reporta conclusão para Backend
                │
                ▼
9. Dashboard atualiza status em tempo real
```

### Estrutura do Monorepo

```
squadx.dev/
├── backend/                 # Spring Boot API
│   ├── src/main/java/dev/squadx/
│   │   ├── config/
│   │   ├── controller/
│   │   ├── dto/
│   │   ├── model/
│   │   ├── repository/
│   │   ├── security/
│   │   ├── service/
│   │   └── websocket/
│   └── pom.xml
├── frontend/                # Next.js Dashboard
│   ├── src/
│   │   ├── app/
│   │   │   └── (dashboard)/
│   │   │       └── live/    # Live View pages
│   │   ├── components/
│   │   │   ├── kanban/
│   │   │   ├── live/        # Stream viewer
│   │   │   └── ui/
│   │   ├── hooks/
│   │   │   └── use-webrtc.ts
│   │   ├── lib/
│   │   │   └── supabase.ts
│   │   └── stores/
│   └── package.json
├── client/                  # Python Daemon
│   ├── squadx_client/
│   │   ├── agents/         # Agentes especializados
│   │   ├── docker/         # Container management
│   │   ├── live/           # Session management
│   │   ├── orchestrator/   # LangGraph workflow
│   │   ├── streaming/      # VNC client + WebRTC bridge
│   │   ├── tools/          # LangChain tools
│   │   └── websocket/      # STOMP client
│   ├── docker/             # Dockerfiles e profiles
│   │   ├── agent.Dockerfile
│   │   └── seccomp/
│   ├── supabase/           # Database migrations
│   │   └── migrations/
│   ├── tests/              # Test suite
│   │   └── e2e/
│   └── pyproject.toml
└── docker-compose.yml
```

### API Endpoints

| Endpoint | Descrição |
|----------|-----------|
| `POST /api/v1/auth/register` | Registro de usuário |
| `POST /api/v1/auth/login` | Login (retorna JWT) |
| `GET/POST /api/v1/organizations` | Gerenciamento de organizações |
| `GET/POST /api/v1/projects` | Gerenciamento de projetos |
| `GET/POST/PATCH /api/v1/tasks` | CRUD de tasks |
| `GET/POST /api/v1/live-view/sessions` | Gerenciamento de live sessions |
| `GET /api/v1/live-view/supabase/sessions/*` | Sessions do Supabase (Python Client) |
| `WS /ws` | WebSocket para real-time |

---

## 🧪 Testes

### Client (Python)

```bash
cd client

# Instalar dependências de teste
pip install -e ".[dev]"

# Executar testes unitários
pytest tests/

# Executar testes E2E (requer Docker)
pytest tests/e2e/ -v

# Cobertura de código
pytest --cov=squadx_client tests/
```

### Frontend

```bash
cd frontend

# Type checking
pnpm type-check

# Linting
pnpm lint

# Formatação
pnpm format
```

### Backend

```bash
cd backend

# Executar testes
./mvnw test

# Build
./mvnw clean package -DskipTests
```

---

## 🗺️ Roadmap

### Q1 2025 - MVP ✅
- [x] Backend com API REST e WebSocket
- [x] Frontend com Kanban e gestão de tasks
- [x] Client com orquestração básica
- [x] Docker sandbox com hardening
- [x] Live View streaming (VNC→WebRTC)
- [ ] 5-10 beta customers

### Q2 2025 - Multi-Agent
- [x] 6 tipos de agentes especializados
- [ ] Execução paralela de subtasks
- [ ] Métricas de performance por agente

### Q3 2025 - Enterprise
- [ ] SSO (SAML/OIDC)
- [ ] Audit logs avançados
- [ ] API pública
- [ ] SFU mode para 100+ viewers

### Q4 2025 - Scale
- [ ] Marketplace de agentes
- [ ] White-label
- [ ] Mobile app (PWA)
- [ ] gVisor/Firecracker isolation

---

## 🤝 Contribuindo

Contribuições são bem-vindas!

```bash
# Fork e clone
git clone https://github.com/seu-usuario/squadx.dev.git
cd squadx.dev

# Crie uma branch
git checkout -b feature/minha-feature

# Faça suas alterações e commit
git commit -m "feat: minha nova feature"

# Push e abra um PR
git push origin feature/minha-feature
```

### Convenções de Commit

Usamos [Conventional Commits](https://www.conventionalcommits.org/):

- `feat:` Nova funcionalidade
- `fix:` Correção de bug
- `docs:` Documentação
- `refactor:` Refatoração
- `test:` Testes
- `chore:` Manutenção

---

## 📄 Licença

Este projeto está licenciado sob a [MIT License](LICENSE).

---

## 📞 Contato

- **Website**: [squadx.dev](https://squadx.dev)
- **Email**: team@squadx.dev
- **GitHub**: [github.com/edsonmartins/squadx.dev](https://github.com/edsonmartins/squadx.dev)

---

<p align="center">
  <strong>SquadX.dev</strong> - Transformando a forma como software é desenvolvido.
</p>
