# SquadX Live - Arquitetura Detalhada

**Diagramas e especificações técnicas completas**

---

## 1. Visão Geral da Arquitetura

### Arquitetura em 3 Camadas

```
┌────────────────────────────────────────────────────────────────────────┐
│                        CAMADA 1: FRONTEND                              │
│                        (Cloud - Hospedado)                             │
├────────────────────────────────────────────────────────────────────────┤
│                                                                        │
│  ┌──────────────────────────────────────────────────────────────┐    │
│  │                   Next.js 16 Application                     │    │
│  ├──────────────────────────────────────────────────────────────┤    │
│  │                                                              │    │
│  │  ┌────────────────────┐        ┌────────────────────┐      │    │
│  │  │  SquadX Dashboard  │        │   SquadX Live      │      │    │
│  │  │                    │        │   Viewer (PWA)     │      │    │
│  │  │  • Kanban board    │        │                    │      │    │
│  │  │  • Task mgmt       │        │  • WebRTC viewer   │      │    │
│  │  │  • Analytics       │        │  • Multi-viewer    │      │    │
│  │  │  • Settings        │        │  • Real-time chat  │      │    │
│  │  │  • Projects        │        │  • Annotations     │      │    │
│  │  │                    │        │  • Remote control  │      │    │
│  │  │                    │        │  • Calendar        │      │    │
│  │  └────────────────────┘        └────────────────────┘      │    │
│  │                                                              │    │
│  │  Tecnologias:                                                │    │
│  │  • React 19 + TypeScript 5                                   │    │
│  │  • Tailwind CSS 4                                            │    │
│  │  • Radix UI components                                       │    │
│  │  • TanStack Query (data fetching)                            │    │
│  │  • Zustand (state management)                                │    │
│  │  • STOMP/SockJS (backend communication)                      │    │
│  │  • Supabase Realtime (WebRTC signaling)                      │    │
│  └──────────────────────────────────────────────────────────────┘    │
│                                                                        │
│  Deploy: Vercel / AWS CloudFront                                      │
│  CDN: CloudFlare                                                      │
└────────────────────────────────────────────────────────────────────────┘
                              ↓
                    REST API + STOMP/SockJS
                              ↓
┌────────────────────────────────────────────────────────────────────────┐
│                        CAMADA 2: BACKEND                               │
│                        (Cloud - Hospedado)                             │
├────────────────────────────────────────────────────────────────────────┤
│                                                                        │
│  ┌──────────────────────────────────────────────────────────────┐    │
│  │               Backend Services (Dual Stack)                  │    │
│  ├──────────────────────────────────────────────────────────────┤    │
│  │                                                              │    │
│  │  ┌─────────────────────┐      ┌─────────────────────┐      │    │
│  │  │  Spring Boot 3.4    │      │   Supabase BaaS     │      │    │
│  │  │  (SquadX Core)      │      │   (Live Features)   │      │    │
│  │  │  Java 21            │      │                     │      │    │
│  │  │                     │      │                     │      │    │
│  │  │  • REST API (9 ctrl)│      │  • Auth (JWT)       │      │    │
│  │  │  • STOMP WebSocket  │      │  • Realtime         │      │    │
│  │  │  • Spring Cache     │      │  • Database         │      │    │
│  │  │  • JPA/Hibernate    │      │  • Storage (S3)     │      │    │
│  │  │  • RBAC             │      │  • Email (Resend)   │      │    │
│  │  │                     │      │                     │      │    │
│  │  │  Controllers:       │      │  Tables:            │      │    │
│  │  │  • /tasks           │      │  • conversations    │      │    │
│  │  │  • /projects        │      │  • messages         │      │    │
│  │  │  • /agents          │      │  • meetings         │      │    │
│  │  │  • /executions      │      │  • sessions         │      │    │
│  │  │  • /live-view       │      │  • participants     │      │    │
│  │  │  • /squads          │      │                     │      │    │
│  │  │  • /organizations   │      │                     │      │    │
│  │  │  • /auth            │      │                     │      │    │
│  │  │  • /health          │      │                     │      │    │
│  │  └──────────┬──────────┘      └──────────┬──────────┘      │    │
│  │             │                            │                 │    │
│  │             ↓                            ↓                 │    │
│  │  ┌──────────────────────┐    ┌──────────────────────┐    │    │
│  │  │  PostgreSQL 16       │    │  Supabase PostgreSQL │    │    │
│  │  │  (SquadX Data)       │    │  (Live Data)         │    │    │
│  │  │                      │    │                      │    │    │
│  │  │  14 JPA Entities:    │    │  • conversations     │    │    │
│  │  │  • tasks             │    │  • messages          │    │    │
│  │  │  • projects          │    │  • meetings          │    │    │
│  │  │  • squads            │    │  • sessions          │    │    │
│  │  │  • agents            │    │  • participants      │    │    │
│  │  │  • executions        │    │  • user_presence     │    │    │
│  │  │  • execution_logs    │    │                      │    │    │
│  │  │  • organizations     │    │                      │    │    │
│  │  │  • org_members       │    │                      │    │    │
│  │  │  • users             │    │                      │    │    │
│  │  │  • live_sessions     │    │                      │    │    │
│  │  │  • live_participants │    │                      │    │    │
│  │  │                      │    │                      │    │    │
│  │  │  Flyway migrations   │    │                      │    │    │
│  │  └──────────────────────┘    └──────────────────────┘    │    │
│  │                                                              │    │
│  │  ┌──────────────────────┐                                   │    │
│  │  │  Redis 7 (Cache)     │                                   │    │
│  │  │  (via Spring Cache)  │                                   │    │
│  │  │                      │                                   │    │
│  │  │  • Session cache     │                                   │    │
│  │  │  • Rate limiting     │                                   │    │
│  │  │  • Pub/Sub           │                                   │    │
│  │  └──────────────────────┘                                   │    │
│  └──────────────────────────────────────────────────────────────┘    │
│                                                                        │
│  Deploy: AWS ECS Fargate / Railway                                    │
│  Database: AWS RDS PostgreSQL + Supabase                              │
│  Build: Maven                                                         │
└────────────────────────────────────────────────────────────────────────┘
                              ↓
                      STOMP/SockJS Events
                              ↓
┌────────────────────────────────────────────────────────────────────────┐
│                        CAMADA 3: CLIENT                                │
│                    (Local - Developer Machine)                         │
├────────────────────────────────────────────────────────────────────────┤
│                                                                        │
│  ┌──────────────────────────────────────────────────────────────┐    │
│  │                   Client Applications                        │    │
│  ├──────────────────────────────────────────────────────────────┤    │
│  │                                                              │    │
│  │  ┌─────────────────────┐      ┌─────────────────────┐      │    │
│  │  │  SquadX Client      │      │  SquadX Live Host   │      │    │
│  │  │  (Python Daemon)    │      │  (Tauri Desktop)    │      │    │
│  │  │                     │      │                     │      │    │
│  │  │  • Task receiver    │      │  • Session manager  │      │    │
│  │  │  • LangGraph orch   │◄────►│  • WebRTC host      │      │    │
│  │  │  • Agent spawner    │      │  • VNC capture      │      │    │
│  │  │  • Git operations   │      │  • Screen stream    │      │    │
│  │  │  • Metrics          │      │  • Audio (optional) │      │    │
│  │  │  • LiteLLM router   │      │  • Remote control   │      │    │
│  │  │  • aiortc bridge    │      │  • TURN support     │      │    │
│  │  │                     │      │                     │      │    │
│  │  │  Port: 8765         │      │  Port: 3456         │      │    │
│  │  └──────────┬──────────┘      └──────────┬──────────┘      │    │
│  │             │                            │                 │    │
│  │             │                            │ VNC             │    │
│  │             ↓                            ↓                 │    │
│  │  ┌──────────────────────────────────────────────────────┐  │    │
│  │  │           Docker Engine (Agent Containers)           │  │    │
│  │  │                                                      │  │    │
│  │  │  ┌───────────┐  ┌───────────┐  ┌───────────┐       │  │    │
│  │  │  │ Frontend  │  │ Backend   │  │ Fullstack │       │  │    │
│  │  │  │ Agent     │  │ Agent     │  │ Agent     │       │  │    │
│  │  │  │           │  │           │  │           │       │  │    │
│  │  │  │ Xvfb :99  │  │ Xvfb :100 │  │ Xvfb :101 │       │  │    │
│  │  │  │ x11vnc    │  │ x11vnc    │  │ x11vnc    │       │  │    │
│  │  │  │ :5900     │  │ :5901     │  │ :5902     │       │  │    │
│  │  │  │           │  │           │  │           │       │  │    │
│  │  │  │ network:  │  │ network:  │  │ network:  │       │  │    │
│  │  │  │   none    │  │   none    │  │   none    │       │  │    │
│  │  │  └───────────┘  └───────────┘  └───────────┘       │  │    │
│  │  │                                                      │  │    │
│  │  │  ┌───────────┐  ┌───────────┐                       │  │    │
│  │  │  │ DevOps    │  │ QA        │                       │  │    │
│  │  │  │ Agent     │  │ Agent     │                       │  │    │
│  │  │  │           │  │           │                       │  │    │
│  │  │  │ Xvfb :102 │  │ Xvfb :103 │                       │  │    │
│  │  │  │ x11vnc    │  │ x11vnc    │                       │  │    │
│  │  │  │ :5903     │  │ :5904     │                       │  │    │
│  │  │  │           │  │           │                       │  │    │
│  │  │  │ network:  │  │ network:  │                       │  │    │
│  │  │  │   none    │  │   none    │                       │  │    │
│  │  │  └───────────┘  └───────────┘                       │  │    │
│  │  └──────────────────────────────────────────────────────┘  │    │
│  │                                                              │    │
│  │  Agents (7+1 implemented):                                   │    │
│  │  • 7 specialists: Frontend, Backend, Fullstack, DevOps,      │    │
│  │    QA, Database, Coordinator                                 │    │
│  │  • +1 runtime adapter: ExternalCliAgent (Claude Code /       │    │
│  │    Codex / Gemini CLI in the sandbox)                        │    │
│  │                                                              │    │
│  │  Orchestration: LangGraph (analyze→plan→execute→review→commit)│    │
│  │  LLM Routing: LiteLLM (OpenAI, Anthropic, Gemini)            │    │
│  │  Signaling: Supabase Realtime                                 │    │
│  │  WebRTC: aiortc bridge                                        │    │
│  │                                                              │    │
│  │  OS Support: macOS, Windows (WSL2), Linux                    │    │
│  │  Install: Homebrew, WinGet, APT/DNF                          │    │
│  └──────────────────────────────────────────────────────────────┘    │
└────────────────────────────────────────────────────────────────────────┘
                              ↓
                        WebRTC P2P
                              ↓
┌────────────────────────────────────────────────────────────────────────┐
│                         VIEWERS (Anywhere)                             │
│                    (Browser or Desktop App)                            │
├────────────────────────────────────────────────────────────────────────┤
│                                                                        │
│  Senior Dev 1    Senior Dev 2    Senior Dev 3    ...  Senior Dev N    │
│  (João)          (Maria)         (Pedro)              (Carlos)        │
│                                                                        │
│  [Dashboard]     [Dashboard]     [Dashboard]          [Dashboard]     │
│  [Live View]     [Live View]     [Live View]          [Live View]     │
│  [Real-time Chat][Real-time Chat][Real-time Chat]     [Real-time Chat]│
│  [Remote Control][Remote Control][Remote Control]     [Remote Control]│
│                                                                        │
│  WebRTC Mesh Network - Todos veem mesma sessão simultaneamente        │
└────────────────────────────────────────────────────────────────────────┘
```

---

## 2. Fluxo de Dados: Criação de Live Session

```
STEP 1: PM Requests Live View
═══════════════════════════════

┌─────────────┐
│  PM (João)  │
│  Dashboard  │
└──────┬──────┘
       │ Click "Watch Live" on Task #123
       ↓
┌──────────────────────────┐
│  Frontend (Next.js)      │
│                          │
│  POST /api/v1/live-view/ │
│       sessions           │
│  {                       │
│    task_id: 123          │
│  }                       │
└───────────┬──────────────┘
            │
            ↓

STEP 2: Backend Orchestration
══════════════════════════════

┌─────────────────────────────────┐
│  Spring Boot 3.4 Backend        │
│  (Java 21)                      │
│                                 │
│  1. Validate task exists (JPA)  │
│  2. Check task is running       │
│  3. Check permissions (RBAC)    │
│  4. Create live_session record  │
│  5. Get client_id from task     │
└────────────┬────────────────────┘
             │
             ↓ STOMP message
┌──────────────────────────────────────┐
│  WebSocket Server (STOMP/SockJS)     │
│                                      │
│  send("/topic/client.{client_id}", { │
│    type: "start_live_view",          │
│    session_id: 456,                  │
│    task_id: 123,                     │
│    container_id: "abc123"            │
│  })                                  │
└────────────┬─────────────────────────┘
             │
             ↓

STEP 3: Client Receives Request
════════════════════════════════

┌─────────────────────────────────┐
│  SquadX Client (Python)         │
│  Running on Dev Machine         │
│                                 │
│  STOMP subscription:            │
│  /topic/client.{client_id}      │
│  def handle(data):              │
│    container_id = data['..']    │
│    session_id = data['..']      │
│                                 │
│    # Start SquadX Live Host     │
│    start_live_host(...)         │
└────────────┬────────────────────┘
             │
             ↓

STEP 4: SquadX Live Host Starts
════════════════════════════════

┌─────────────────────────────────────┐
│  SquadX Live Host (Tauri)           │
│                                     │
│  $ squadx-live host                 │
│    --vnc localhost:5900             │
│    --session-id 456                 │
│    --task-id 123                    │
│                                     │
│  1. Connect to VNC (agent screen)   │
│  2. Create WebRTC peer connection   │
│     (via aiortc bridge)             │
│  3. Generate 6-char code: "XYZ789"  │
│  4. Save session to Supabase        │
│  5. Configure TURN server (if NAT)  │
│  6. Start streaming                 │
└────────────┬────────────────────────┘
             │
             ↓ Session created
┌─────────────────────────────────────┐
│  Supabase Database                  │
│                                     │
│  INSERT INTO sessions (             │
│    id: 456,                         │
│    code: "XYZ789",                  │
│    url: "live.squadx.dev/XYZ789",   │
│    task_id: 123,                    │
│    status: 'active',                │
│    max_viewers: 5                   │
│  )                                  │
└────────────┬────────────────────────┘
             │
             ↓ Send URL to backend
┌─────────────────────────────────────┐
│  Client sends STOMP message         │
│                                     │
│  send("/app/live_view_ready", {     │
│    session_id: 456,                 │
│    url: "live.squadx.dev/XYZ789",   │
│    status: "active"                 │
│  })                                 │
└────────────┬────────────────────────┘
             │
             ↓

STEP 5: Backend Updates Frontend
═════════════════════════════════

┌─────────────────────────────────────┐
│  Backend STOMP Broadcast            │
│                                     │
│  send("/topic/user.{pm_user_id}", { │
│    type: "live_view_ready",         │
│    session_id: 456,                 │
│    task_id: 123,                    │
│    url: "live.squadx.dev/XYZ789"    │
│  })                                 │
└────────────┬────────────────────────┘
             │
             ↓

STEP 6: PM Joins Session
═════════════════════════

┌─────────────────────────────────────┐
│  Frontend shows notification        │
│                                     │
│  ┌───────────────────────────┐     │
│  │ Live View Ready!          │     │
│  │                           │     │
│  │ Agent is streaming        │     │
│  │                           │     │
│  │ [Watch Now] [Dismiss]     │     │
│  └───────────────────────────┘     │
└────────────┬────────────────────────┘
             │ PM clicks "Watch Now"
             ↓
┌─────────────────────────────────────┐
│  Open SquadX Live Viewer            │
│                                     │
│  iframe or window.open()            │
│  src="live.squadx.dev/XYZ789"       │
└────────────┬────────────────────────┘
             │
             ↓

STEP 7: WebRTC Connection
══════════════════════════

┌─────────────────────────────────────┐
│  SquadX Live Viewer (PWA)           │
│                                     │
│  1. Load page                       │
│  2. Join code: "XYZ789"             │
│  3. WebRTC handshake:               │
│     - Get session from Supabase     │
│     - Create RTCPeerConnection      │
│     - Exchange SDP via Supabase     │
│       Realtime (signaling)          │
│     - Exchange ICE candidates       │
│     - TURN relay if P2P fails       │
│  4. P2P connection established      │
│  5. Video stream starts             │
└────────────┬────────────────────────┘
             │
             ↓
┌─────────────────────────────────────┐
│  PM vê Agent trabalhando!           │
│                                     │
│  [Agent screen streaming]           │
│  [Real-time chat via Supabase]      │
│  [Remote control available]         │
│  [Annotations available]            │
└─────────────────────────────────────┘
```

---

## 3. Multi-Viewer Architecture

### 3.1 WebRTC Mesh Network

```
                  ┌─────────────────────┐
                  │  SquadX Live Host   │
                  │  (on Dev Machine)   │
                  │                     │
                  │  Captures:          │
                  │  VNC → Agent screen │
                  │  aiortc bridge      │
                  │                     │
                  │  Streams via:       │
                  │  WebRTC P2P         │
                  │                     │
                  │  TURN fallback:     │
                  │  coturn server      │
                  └──────────┬──────────┘
                             │
              WebRTC Mesh Network (P2P)
              Signaling: Supabase Realtime
                             │
         ┌───────────────────┼────────────────────┐
         │                   │                    │
    ┌────▼─────┐       ┌────▼─────┐       ┌─────▼────┐
    │ Viewer 1 │       │ Viewer 2 │       │ Viewer 3 │
    │ (João)   │       │ (Maria)  │       │ (Pedro)  │
    │          │       │          │       │          │
    │ Browser  │       │ Browser  │       │ Browser  │
    │ PWA      │       │ PWA      │       │ PWA      │
    └────┬─────┘       └────┬─────┘       └────┬─────┘
         │                  │                   │
         └──────────────────┼───────────────────┘
                            │
                    Supabase Realtime
                    (for chat, presence,
                     annotations sync,
                     WebRTC signaling)

Cada viewer:
• Recebe stream direto do Host (P2P)
• Envia mensagens via Supabase Realtime (não P2P)
• Sincroniza annotations via data channel
• Pode solicitar remote control (se permitido)
• TURN server usado quando P2P não é possível
• Auto-reconnect em caso de falha de conexão

Latency típica: 80-150ms
```

### 3.2 Data Flow: Chat Message (Real-time via Supabase)

```
Viewer 1 (João) digita no chat
         │
         ↓
    Frontend
         │ Supabase Realtime
         ↓
  Supabase Realtime
         │
         ├─────────┬─────────┬────────────┐
         ↓         ↓         ↓            ↓
    Viewer 1  Viewer 2  Viewer 3    Supabase DB
    (echo)    (recebe)  (recebe)    (persiste)
         │         │         │
         └─────────┴─────────┘
              Todos veem
           mensagem em <100ms
```

### 3.3 Data Flow: Annotation

```
Viewer 2 (Maria) desenha na tela
         │
         ↓
    Frontend (canvas)
         │ WebRTC Data Channel
         ↓
  SquadX Live Host
         │ Broadcast to all peers
         ├─────────┬─────────┐
         ↓         ↓         ↓
    Viewer 1  Viewer 2  Viewer 3
    (vê)      (echo)    (vê)

Latency: 20-50ms (muito rápida, P2P direto)
```

### 3.4 Data Flow: Remote Control

```
Viewer 3 (Pedro) solicita controle remoto
         │
         ↓
    Frontend (request via Supabase Realtime)
         │
         ↓
  SquadX Live Host verifica permissão
         │ allow_remote_control = true?
         ↓
  Host concede controle
         │
         ↓
    Viewer 3 envia input events
         │ WebRTC Data Channel
         ↓
  SquadX Live Host
         │ Traduz para input no container
         ↓
    Agent Container (VNC input injection)

Latency: 30-80ms (P2P direto)
```

---

## 4. Componentes Técnicos Detalhados

### 4.1 Spring Boot Backend (Java 21)

```
┌─────────────────────────────────────────────┐
│         Spring Boot 3.4 Backend             │
│         (Java 21 / Maven)                   │
├─────────────────────────────────────────────┤
│                                             │
│  Controllers (9):                           │
│  ┌─────────────────────────────────────┐   │
│  │  • AgentController                  │   │
│  │  • AuthController                   │   │
│  │  • ExecutionController              │   │
│  │  • HealthController                 │   │
│  │  • LiveViewController               │   │
│  │  • OrganizationController           │   │
│  │  • ProjectController                │   │
│  │  • SquadController                  │   │
│  │  • TaskController                   │   │
│  └─────────────────────────────────────┘   │
│                                             │
│  Services (10):                             │
│  ┌─────────────────────────────────────┐   │
│  │  • AgentService                     │   │
│  │  • AuthService                      │   │
│  │  • ExecutionService                 │   │
│  │  • LiveViewService                  │   │
│  │  • OrganizationService              │   │
│  │  • ProjectService                   │   │
│  │  • SquadService                     │   │
│  │  • SupabaseLiveSessionService       │   │
│  │  • TaskService                      │   │
│  │  • WebSocketEventService            │   │
│  └─────────────────────────────────────┘   │
│                                             │
│  JPA Entities (14):                         │
│  ┌─────────────────────────────────────┐   │
│  │  • Agent, BaseEntity, Execution,    │   │
│  │    ExecutionLog, LiveSession,       │   │
│  │    LiveSessionParticipant,          │   │
│  │    Organization, OrganizationMember,│   │
│  │    Project, Squad, Task, User       │   │
│  │  • Enums: AgentType (COORDINATOR,   │   │
│  │    FRONTEND, BACKEND, FULLSTACK,    │   │
│  │    DEVOPS, QA)                      │   │
│  └─────────────────────────────────────┘   │
│                                             │
│  Security:                                  │
│  ┌─────────────────────────────────────┐   │
│  │  • Spring Security + JWT            │   │
│  │  • HMAC-SHA signing (symmetric)     │   │
│  │  • JwtService (io.jsonwebtoken)     │   │
│  │  • JwtAuthenticationFilter          │   │
│  │  • WebSocketAuthInterceptor         │   │
│  └─────────────────────────────────────┘   │
│                                             │
│  WebSocket:                                 │
│  ┌─────────────────────────────────────┐   │
│  │  • STOMP over SockJS                │   │
│  │  • WebSocketConfig                  │   │
│  │  • WebSocketController              │   │
│  └─────────────────────────────────────┘   │
│                                             │
│  Config:                                    │
│  ┌─────────────────────────────────────┐   │
│  │  • JwtConfig                        │   │
│  │  • RedisConfig (Spring Cache)       │   │
│  │  • SecurityConfig                   │   │
│  │  • SupabaseConfig                   │   │
│  │  • WebSocketConfig                  │   │
│  └─────────────────────────────────────┘   │
│                                             │
│  Database:                                  │
│  ┌─────────────────────────────────────┐   │
│  │  • PostgreSQL 16                    │   │
│  │  • Spring Data JPA / Hibernate      │   │
│  │  • Flyway migrations (V1-V4)       │   │
│  └─────────────────────────────────────┘   │
│                                             │
│  Build: Maven (pom.xml)                     │
└─────────────────────────────────────────────┘
```

### 4.2 SquadX Client (Python Daemon)

```
┌─────────────────────────────────────────────┐
│         SquadX Client                       │
│         (Python Daemon)                     │
├─────────────────────────────────────────────┤
│                                             │
│  Core Modules:                              │
│  ┌─────────────────────────────────────┐   │
│  │  • daemon.py        (main loop)     │   │
│  │  • config.py        (configuration) │   │
│  │  • main.py          (entry point)   │   │
│  └─────────────────────────────────────┘   │
│                                             │
│  LLM Routing (LiteLLM):                    │
│  ┌─────────────────────────────────────┐   │
│  │  • llm/router.py                    │   │
│  │  • Multi-provider: OpenAI,          │   │
│  │    Anthropic, Gemini                │   │
│  └─────────────────────────────────────┘   │
│                                             │
│  Orchestration (LangGraph):                 │
│  ┌─────────────────────────────────────┐   │
│  │  • orchestrator/graph.py            │   │
│  │  • orchestrator/nodes.py            │   │
│  │  • orchestrator/state.py            │   │
│  │  • Flow: analyze→plan→execute→      │   │
│  │          review→commit              │   │
│  └─────────────────────────────────────┘   │
│                                             │
│  Agents (7+1 implemented):                  │
│  ┌─────────────────────────────────────┐   │
│  │  • agents/factory.py                │   │
│  │    - FrontendAgent                  │   │
│  │    - BackendAgent                   │   │
│  │    - FullstackAgent                 │   │
│  │    - DevOpsAgent                    │   │
│  │    - QAAgent                        │   │
│  │    - DatabaseAgent                  │   │
│  │    - CoordinatorAgent               │   │
│  │  • agents/external_cli_agent.py     │   │
│  │    - ExternalCliAgent (adapter)     │   │
│  │  • agents/base.py (BaseAgent ABC)   │   │
│  │  • agents/tools.py (agent tools)    │   │
│  └─────────────────────────────────────┘   │
│                                             │
│  Docker (Hardened Containers):              │
│  ┌─────────────────────────────────────┐   │
│  │  • docker/manager.py                │   │
│  │  • docker/sandbox.py                │   │
│  │  • docker/hardening.py              │   │
│  │  • Docker SDK for Python            │   │
│  └─────────────────────────────────────┘   │
│                                             │
│  Streaming:                                 │
│  ┌─────────────────────────────────────┐   │
│  │  • streaming/webrtc_bridge.py       │   │
│  │    (aiortc for WebRTC)              │   │
│  │  • streaming/vnc_client.py          │   │
│  │  • streaming/vnc_streamer.py        │   │
│  └─────────────────────────────────────┘   │
│                                             │
│  Live:                                      │
│  ┌─────────────────────────────────────┐   │
│  │  • live/session_manager.py          │   │
│  │  • live/supabase_client.py          │   │
│  │  • Supabase Realtime signaling      │   │
│  └─────────────────────────────────────┘   │
│                                             │
│  WebSocket:                                 │
│  ┌─────────────────────────────────────┐   │
│  │  • websocket/stomp_client.py        │   │
│  │  • STOMP/SockJS to backend          │   │
│  └─────────────────────────────────────┘   │
│                                             │
│  Other:                                     │
│  ┌─────────────────────────────────────┐   │
│  │  • git/manager.py                   │   │
│  │  • metrics/collector.py             │   │
│  │  • storage/local_db.py              │   │
│  └─────────────────────────────────────┘   │
└─────────────────────────────────────────────┘
```

### 4.3 SquadX Live Host (Tauri Desktop App)

```
┌─────────────────────────────────────────────┐
│         SquadX Live Host                    │
│         (Tauri 2.0 Application)             │
├─────────────────────────────────────────────┤
│                                             │
│  Frontend (React + TypeScript)              │
│  ┌─────────────────────────────────────┐   │
│  │  Components:                        │   │
│  │  • SessionManager                   │   │
│  │  • ScreenCapture                    │   │
│  │  • WebRTCController                 │   │
│  │  • RemoteControlHandler             │   │
│  │  • Settings                         │   │
│  └─────────────────────────────────────┘   │
│                                             │
│  Backend (Rust)                             │
│  ┌─────────────────────────────────────┐   │
│  │  Modules:                           │   │
│  │  • vnc_client (connect to x11vnc)   │   │
│  │  • webrtc_peer (native WebRTC)      │   │
│  │  • session_api (Supabase client)    │   │
│  │  • turn_config (TURN server setup)  │   │
│  │  • commands (Tauri commands)        │   │
│  └─────────────────────────────────────┘   │
│                                             │
│  System Integration:                        │
│  • Keychain (macOS, Windows Credential)     │
│  • System tray (tray icon + notifications)  │
│  • Auto-updater (Tauri built-in)            │
│  • Deep links (squadx-live://)              │
└─────────────────────────────────────────────┘

File Structure:
squadx-live-host/
├── src-tauri/          # Rust backend
│   ├── src/
│   │   ├── main.rs
│   │   ├── vnc.rs      # VNC client
│   │   ├── webrtc.rs   # WebRTC peer
│   │   ├── turn.rs     # TURN server config
│   │   └── session.rs  # Session mgmt
│   ├── Cargo.toml
│   └── tauri.conf.json
│
└── src/                # React frontend
    ├── components/
    ├── hooks/
    ├── lib/
    └── App.tsx
```

### 4.4 Agent Container Setup

```
Dockerfile (squadx-agent-runtime):

FROM ubuntu:24.04

# System packages
RUN apt-get update && apt-get install -y \
    xvfb \           # Virtual X server
    x11vnc \         # VNC server
    fluxbox \        # Lightweight WM
    python3.11 \
    python3-pip \
    git \
    curl \
    && rm -rf /var/lib/apt/lists/*

# Python dependencies
COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt

# Agent code
COPY agent/ /app/agent/
WORKDIR /app

# Startup script
COPY start-agent.sh /start-agent.sh
RUN chmod +x /start-agent.sh

# Environment
ENV DISPLAY=:99
ENV RESOLUTION=1280x720

CMD ["/start-agent.sh"]

────────────────────────────────────────

start-agent.sh:

#!/bin/bash
set -e

# Start Xvfb (virtual display)
echo "Starting Xvfb on display :99..."
Xvfb :99 -screen 0 ${RESOLUTION}x24 &
sleep 2

# Start window manager
echo "Starting Fluxbox..."
fluxbox &
sleep 1

# Start VNC server
echo "Starting x11vnc on port 5900..."
x11vnc -display :99 \
       -forever \
       -shared \
       -rfbport 5900 \
       -nopw \
       -threads \
       -noxdamage \
       &
sleep 2

# Start agent
echo "Starting agent..."
cd /app/agent
python3 main.py

# Keep container running
wait
```

### 4.5 Database Schema (Live Features)

```sql
-- Supabase Schema for SquadX Live

-- Sessions (PairUX sessions)
CREATE TABLE sessions (
    id BIGSERIAL PRIMARY KEY,
    code VARCHAR(6) UNIQUE NOT NULL,
    url TEXT NOT NULL,
    task_id INTEGER,  -- Reference to SquadX task
    host_user_id UUID REFERENCES auth.users(id),
    status VARCHAR(20) DEFAULT 'active',
    max_viewers INTEGER DEFAULT 5,
    allow_remote_control BOOLEAN DEFAULT FALSE,
    allow_annotations BOOLEAN DEFAULT TRUE,
    started_at TIMESTAMPTZ DEFAULT NOW(),
    ended_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ DEFAULT NOW()
);

CREATE INDEX idx_sessions_code ON sessions(code);
CREATE INDEX idx_sessions_task ON sessions(task_id);
CREATE INDEX idx_sessions_status ON sessions(status);

-- Session participants (who's watching)
CREATE TABLE session_participants (
    id BIGSERIAL PRIMARY KEY,
    session_id BIGINT REFERENCES sessions(id) ON DELETE CASCADE,
    user_id UUID REFERENCES auth.users(id),
    can_control BOOLEAN DEFAULT FALSE,
    can_annotate BOOLEAN DEFAULT TRUE,
    joined_at TIMESTAMPTZ DEFAULT NOW(),
    left_at TIMESTAMPTZ,
    UNIQUE(session_id, user_id, left_at)
);

CREATE INDEX idx_participants_session ON session_participants(session_id);
CREATE INDEX idx_participants_user ON session_participants(user_id);

-- Conversations (team chat)
CREATE TABLE conversations (
    id BIGSERIAL PRIMARY KEY,
    type VARCHAR(20) NOT NULL,  -- 'direct' or 'group'
    name VARCHAR(255),
    avatar_url TEXT,
    session_id BIGINT REFERENCES sessions(id),  -- Link to live session
    created_at TIMESTAMPTZ DEFAULT NOW(),
    updated_at TIMESTAMPTZ DEFAULT NOW()
);

CREATE INDEX idx_conversations_session ON conversations(session_id);

-- Conversation participants
CREATE TABLE conversation_participants (
    id BIGSERIAL PRIMARY KEY,
    conversation_id BIGINT REFERENCES conversations(id) ON DELETE CASCADE,
    user_id UUID REFERENCES auth.users(id),
    joined_at TIMESTAMPTZ DEFAULT NOW(),
    last_read_at TIMESTAMPTZ,
    UNIQUE(conversation_id, user_id)
);

CREATE INDEX idx_conv_participants_conv ON conversation_participants(conversation_id);
CREATE INDEX idx_conv_participants_user ON conversation_participants(user_id);

-- Messages
CREATE TABLE messages (
    id BIGSERIAL PRIMARY KEY,
    conversation_id BIGINT REFERENCES conversations(id) ON DELETE CASCADE,
    user_id UUID REFERENCES auth.users(id),
    content TEXT NOT NULL,
    type VARCHAR(20) DEFAULT 'text',  -- 'text', 'system', 'code_snippet'
    metadata JSONB,  -- For @mentions, code language, etc
    created_at TIMESTAMPTZ DEFAULT NOW()
);

CREATE INDEX idx_messages_conversation ON messages(conversation_id);
CREATE INDEX idx_messages_created ON messages(created_at DESC);

-- User presence
CREATE TABLE user_presence (
    user_id UUID PRIMARY KEY REFERENCES auth.users(id),
    status VARCHAR(20) DEFAULT 'offline',  -- 'online', 'away', 'offline'
    last_seen TIMESTAMPTZ DEFAULT NOW(),
    updated_at TIMESTAMPTZ DEFAULT NOW()
);

-- Meetings
CREATE TABLE meetings (
    id BIGSERIAL PRIMARY KEY,
    title VARCHAR(255) NOT NULL,
    description TEXT,
    start_time TIMESTAMPTZ NOT NULL,
    end_time TIMESTAMPTZ NOT NULL,
    duration_minutes INTEGER,
    rrule TEXT,  -- Recurrence rule (RFC 5545)
    status VARCHAR(20) DEFAULT 'scheduled',  -- 'scheduled', 'in_progress', 'completed', 'cancelled'
    session_id BIGINT REFERENCES sessions(id),  -- Link to live session
    created_by UUID REFERENCES auth.users(id),
    created_at TIMESTAMPTZ DEFAULT NOW(),
    updated_at TIMESTAMPTZ DEFAULT NOW()
);

CREATE INDEX idx_meetings_start ON meetings(start_time);
CREATE INDEX idx_meetings_status ON meetings(status);

-- Meeting attendees
CREATE TABLE meeting_attendees (
    id BIGSERIAL PRIMARY KEY,
    meeting_id BIGINT REFERENCES meetings(id) ON DELETE CASCADE,
    user_id UUID REFERENCES auth.users(id),
    response VARCHAR(20) DEFAULT 'invited',  -- 'invited', 'accepted', 'declined', 'maybe'
    UNIQUE(meeting_id, user_id)
);

CREATE INDEX idx_attendees_meeting ON meeting_attendees(meeting_id);
CREATE INDEX idx_attendees_user ON meeting_attendees(user_id);

-- Row Level Security (RLS)
ALTER TABLE sessions ENABLE ROW LEVEL SECURITY;
ALTER TABLE session_participants ENABLE ROW LEVEL SECURITY;
ALTER TABLE conversations ENABLE ROW LEVEL SECURITY;
ALTER TABLE messages ENABLE ROW LEVEL SECURITY;
ALTER TABLE meetings ENABLE ROW LEVEL SECURITY;

-- Policies (example for sessions)
CREATE POLICY "Users can view sessions they're participating in"
ON sessions FOR SELECT
USING (
    host_user_id = auth.uid() OR
    id IN (
        SELECT session_id FROM session_participants
        WHERE user_id = auth.uid() AND left_at IS NULL
    )
);
```

---

## 5. Security Architecture

### 5.1 Multi-Layer Security Model

```
┌──────────────────────────────────────────────────────┐
│  LAYER 1: Network Security                          │
├──────────────────────────────────────────────────────┤
│  • CloudFlare WAF (DDoS protection)                  │
│  • Rate limiting (100 req/min per IP)                │
│  • TLS 1.3 only (no older protocols)                 │
│  • WSS (WebSocket Secure via STOMP/SockJS)           │
│  • Certificate pinning (mobile apps)                 │
└──────────────────────────────────────────────────────┘
                        ↓
┌──────────────────────────────────────────────────────┐
│  LAYER 2: Authentication                             │
├──────────────────────────────────────────────────────┤
│  • JWT tokens (HMAC-SHA symmetric signing)           │
│  • Access token: 30 min expiry                       │
│  • Refresh token: 7 days expiry                      │
│  • Spring Security + JwtAuthenticationFilter         │
│  • Supabase Auth (OAuth2 compatible)                 │
│  • SSO support (SAML, OIDC) - Enterprise             │
└──────────────────────────────────────────────────────┘
                        ↓
┌──────────────────────────────────────────────────────┐
│  LAYER 3: Authorization (RBAC)                       │
├──────────────────────────────────────────────────────┤
│  Organization Level:                                 │
│  • OWNER: Full control                               │
│  • ADMIN: Manage resources                           │
│  • MEMBER: Use platform                              │
│                                                      │
│  Session Level:                                      │
│  • HOST: Controls session                            │
│  • VIEWER: Can view                                  │
│  • VIEWER+CONTROL: Can remote control                │
│  • VIEWER+ANNOTATE: Can draw                         │
└──────────────────────────────────────────────────────┘
                        ↓
┌──────────────────────────────────────────────────────┐
│  LAYER 4: Data Protection                            │
├──────────────────────────────────────────────────────┤
│  • Code NEVER leaves local machine                   │
│  • WebRTC E2E encryption (DTLS-SRTP)                 │
│  • Database encryption at rest (AES-256)             │
│  • No code stored in cloud (only metadata)           │
│  • Session recordings encrypted (S3 + KMS)           │
└──────────────────────────────────────────────────────┘
                        ↓
┌──────────────────────────────────────────────────────┐
│  LAYER 5: Container Isolation                        │
├──────────────────────────────────────────────────────┤
│  • Docker containers (hardened via Docker SDK)       │
│  • network_mode: none (no internet)                  │
│  • Read-only filesystem (except /workspace)          │
│  • Non-root user (uid 1000)                          │
│  • Resource limits (CPU, memory, PIDs)               │
│  • seccomp profiles (agent.json)                     │
└──────────────────────────────────────────────────────┘
                        ↓
┌──────────────────────────────────────────────────────┐
│  LAYER 6: Audit & Compliance                         │
├──────────────────────────────────────────────────────┤
│  • All actions logged (immutable)                    │
│  • Retention: 2 years (compliance)                   │
│  • GDPR compliant (data deletion on request)         │
│  • LGPD compliant (Brazilian data protection)        │
│  • HIPAA ready (Enterprise - encrypted at rest)      │
└──────────────────────────────────────────────────────┘
```

### 5.2 WebRTC Security

```
┌─────────────────────────────────────────┐
│  WebRTC P2P Connection                  │
├─────────────────────────────────────────┤
│                                         │
│  1. DTLS (Datagram TLS)                 │
│     • Encrypts all media packets        │
│     • Perfect Forward Secrecy           │
│     • Certificate fingerprints in SDP   │
│                                         │
│  2. SRTP (Secure RTP)                   │
│     • Encrypts audio/video streams      │
│     • AES-128 or AES-256                │
│     • Authentication (HMAC-SHA1)        │
│                                         │
│  3. ICE (Interactive Connectivity)      │
│     • STUN: Find public IP              │
│     • TURN: Relay when P2P fails        │
│     • coturn server (self-hosted)       │
│     • Auto-reconnect on failure         │
│                                         │
│  4. Signaling (Supabase Realtime)       │
│     • SDP offer/answer exchange         │
│     • ICE candidate trickle             │
│     • Broadcast channels                │
│                                         │
│  5. Data Channel Encryption             │
│     • SCTP over DTLS                    │
│     • For chat, annotations, control    │
│                                         │
│  Result:                                │
│  • E2E encrypted                        │
│  • No server can decrypt                │
│  • Only peers see content               │
└─────────────────────────────────────────┘
```

---

## 6. Deployment Architecture

### 6.1 Production Infrastructure (AWS)

```
┌────────────────────────────────────────────────────────────┐
│                        INTERNET                            │
└────────────────────────┬───────────────────────────────────┘
                         │
                         ↓
┌────────────────────────────────────────────────────────────┐
│  CloudFlare (CDN + WAF)                                    │
│  • DDoS protection                                         │
│  • WAF rules                                               │
│  • Global CDN (200+ locations)                             │
└────────────────────────┬───────────────────────────────────┘
                         │
                         ↓
┌────────────────────────────────────────────────────────────┐
│  AWS Route 53 (DNS)                                        │
│  • squadx.dev → CloudFront                                 │
│  • api.squadx.dev → ALB                                    │
│  • live.squadx.dev → CloudFront                            │
└───────────┬────────────────────────────┬───────────────────┘
            │                            │
            ↓                            ↓
┌─────────────────────┐    ┌────────────────────────────────┐
│  CloudFront (CDN)   │    │  Application Load Balancer     │
│                     │    │  (ALB)                         │
│  • Next.js static   │    │                                │
│  • Edge caching     │    │  • SSL termination             │
│  • Gzip/Brotli      │    │  • Health checks               │
└──────────┬──────────┘    │  • Sticky sessions             │
           │               └───────────┬────────────────────┘
           │                           │
           ↓                           ↓
┌─────────────────────┐    ┌────────────────────────────────┐
│  S3 Bucket          │    │  ECS Fargate (Containers)      │
│                     │    │                                │
│  • Frontend build   │    │  ┌──────────────────────────┐  │
│  • Static assets    │    │  │  Backend Service         │  │
└─────────────────────┘    │  │  (Spring Boot 3.4)       │  │
                           │  │                          │  │
                           │  │  • Replicas: 3           │  │
                           │  │  • Auto-scaling          │  │
                           │  │  • Health checks         │  │
                           │  └────────┬─────────────────┘  │
                           │           │                    │
                           │           ↓                    │
                           │  ┌──────────────────────────┐  │
                           │  │  WebSocket Service       │  │
                           │  │  (STOMP/SockJS)          │  │
                           │  │                          │  │
                           │  │  • Replicas: 2           │  │
                           │  │  • Redis adapter         │  │
                           │  └────────┬─────────────────┘  │
                           └───────────┼────────────────────┘
                                       │
                                       ↓
┌──────────────────────────────────────────────────────────────┐
│  Data Layer                                                  │
├──────────────────────────────────────────────────────────────┤
│                                                              │
│  ┌────────────────────┐      ┌────────────────────┐        │
│  │  RDS PostgreSQL    │      │  ElastiCache Redis │        │
│  │  Multi-AZ          │      │  (Spring Cache)    │        │
│  │                    │      │                    │        │
│  │  • Primary: us-e-1a│      │  • 3 shards        │        │
│  │  • Standby: us-e-1b│      │  • 2 replicas each │        │
│  │  • Auto-failover   │      │  • Pub/Sub         │        │
│  │  • Flyway managed  │      │                    │        │
│  └────────────────────┘      └────────────────────┘        │
│                                                              │
│  ┌────────────────────┐      ┌────────────────────┐        │
│  │  S3 (Recordings)   │      │  Secrets Manager   │        │
│  │                    │      │                    │        │
│  │  • Versioning      │      │  • API keys        │        │
│  │  • Lifecycle: 90d  │      │  • DB credentials  │        │
│  │  • Server-side enc │      │  • JWT secret      │        │
│  └────────────────────┘      └────────────────────┘        │
└──────────────────────────────────────────────────────────────┘
```

### 6.2 Observability Stack

```
┌──────────────────────────────────────────────────────────┐
│  OpenTelemetry Collector                                 │
│  (Receives traces, metrics, logs)                        │
└───────────┬──────────────────────────────────────────────┘
            │
     ┌──────┴───────┬─────────────┐
     │              │             │
     ↓              ↓             ↓
┌─────────┐   ┌──────────┐   ┌────────┐
│ Tempo   │   │Prometheus│   │ Loki   │
│(Traces) │   │(Metrics) │   │(Logs)  │
└────┬────┘   └────┬─────┘   └───┬────┘
     │             │             │
     └─────────────┼─────────────┘
                   ↓
            ┌──────────────┐
            │   Grafana    │
            │  Dashboards  │
            └──────────────┘

Dashboards:
• Business metrics (tasks, cost, ROI)
• System health (CPU, memory, latency)
• Agent performance (per type)
• Live sessions (active, viewers, duration)
```

---

## 7. Performance Characteristics

### 7.1 Latency Targets

```
Component                          Target      Typical
─────────────────────────────────────────────────────────
API Response (p95)                 < 200ms     120ms
STOMP message delivery             < 100ms     60ms
WebRTC video latency               < 300ms     150ms
Chat message delivery (Supabase)   < 100ms     50ms
Annotation sync (P2P)              < 50ms      25ms
Remote control input (P2P)         < 100ms     50ms
Screen capture FPS                 30 FPS      30 FPS
Database query (p95)               < 50ms      20ms
```

### 7.2 Scalability

```
Resource               Starter    Professional    Enterprise
───────────────────────────────────────────────────────────
Concurrent sessions    1          3               50+
Viewers per session    1          3               10+
Messages/second        100        1000            10000
DB connections         10         50              200
WebSocket connections  50         500             5000
```

---

## 8. Cost Architecture

### 8.1 Infrastructure Costs (Monthly)

```
Component                      Cost (100 users)
─────────────────────────────────────────────
AWS ECS Fargate (3 tasks)      $150
RDS PostgreSQL (db.t3.medium)  $80
ElastiCache Redis (2 nodes)    $60
S3 (1TB recordings)            $25
CloudFront (100GB transfer)    $10
ALB                            $25
Supabase (Pro plan)            $25
coturn TURN server (EC2)       $20
────────────────────────────────────────────
Total Infrastructure:          $395/mês
```

### 8.2 Unit Economics

```
Professional Plan ($1.499/mês):

Revenue:               $1.499
Infra cost:           -$150 (allocated)
LLM credits:          -$0 (pass-through to customer)
─────────────────────────────
Gross profit:          $1.349
Gross margin:          90%

CAC:                   $600
Payback period:        0.4 months
```

---

**Próximo: [UI-WIREFRAMES.md](UI-WIREFRAMES.md)**
