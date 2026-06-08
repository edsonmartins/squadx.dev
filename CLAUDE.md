# CLAUDE.md

Guidance for Claude Code (claude.ai/code) when working in this repository.

## Project

**SquadX.dev** — a B2B SaaS that orchestrates squads of specialized AI agents to
develop software. Agents run in hardened Docker sandboxes with a live WebRTC view,
and (via the runtime adapter) can be either SquadX's native agentic loop or an
external coding CLI (Claude Code / Codex / Gemini).

## Architecture

Monorepo with four runtimes plus infra:

- `backend/` — Spring Boot 3.4 / Java 21 REST API + STOMP WebSocket (PostgreSQL 16, Redis, Flyway).
- `frontend/` — Next.js 16 / React 19 dashboard (TypeScript, Tailwind, Zustand, TanStack Query, shadcn/Radix).
- `client/` — Python 3.12 daemon: LangGraph + LiteLLM orchestration, Docker sandbox lifecycle, VNC→WebRTC streaming, STOMP client.
- `mobile/` (Expo / React Native) and `desktop/` (Tauri v2) — installed clients that wrap/mirror the dashboard.
- `infra/` — Helm, k8s, nginx (TLS 1.3), monitoring (Prometheus/Grafana/Loki/Tempo).

Flow: frontend creates a task → backend persists + dispatches over STOMP
(`/user/queue/tasks`) → client daemon runs the agent in a sandbox → status/logs
stream back over STOMP.

## Commands

Use the root `Makefile` targets:

```bash
make install        # backend mvnw deps + frontend pnpm + client pip -e ".[dev]"
make dev            # docker postgres+redis, then backend + frontend
make test           # backend mvnw test + frontend pnpm test + client pytest
make test-backend / make test-frontend / make test-client
make lint / make format / make type-check
make migrate        # Flyway migrate
make build          # package backend + build frontend + docker images
```

Targeted runs:

```bash
cd backend  && ./mvnw test -Dtest=AutopilotServiceTest         # single Java test class
cd frontend && pnpm exec vitest run src/path/to/file.test.tsx  # single TS test
cd client   && pytest tests/test_external_cli_agent.py -v       # single Python test
```

### ⚠️ JDK requirement

The backend targets **Java 21** with Lombok. Building with a newer JDK (e.g. 25)
fails with `TypeTag :: UNKNOWN` (Lombok incompatibility), and JDK 17 cannot compile
`release 21`. Ensure `JAVA_HOME` points to a JDK 21 before running `mvnw`.

## Backend conventions (Spring Boot)

- **Vertical slice per resource**: `model/` (`@Entity extends BaseEntity`), `repository/`
  (Spring Data JPA), `service/`, `controller/`, `dto/<domain>/` (Request/Response).
  Mirror an existing resource (e.g. `Task`, `Autopilot`) rather than inventing a new shape.
- **Multi-tenancy / authz**: every service method takes `User currentUser` and calls
  `validateUserAccess(organizationId, userId)` (membership check via
  `OrganizationMemberRepository`). There are no `@PreAuthorize` annotations on
  controllers — access is enforced in the service layer. Scope is
  Organization → Squad/Project → Agent/Task.
- **Controllers** inject `@AuthenticationPrincipal User user`, return
  `ResponseEntity<ApiResponse<T>>`, and page with `PageResponse<T>` /
  `PageResponse.from(page)`. `201` on create, `200` otherwise.
- **DTO JSON is snake_case** via `@JsonProperty` (e.g. `agent_type`, `cron_expression`).
- **UUID / delete safety**: when a write query (`Delete*`/`Update*`) uses an id, make sure
  it is the resolved entity's id (load → check access → use `entity.getId()`), not a raw
  unvalidated request string. A `DELETE` that returns 200 while matching zero rows is a bug.
- **Background jobs**: JobRunr (server + dashboard on :8081). Recurring jobs are registered
  dynamically via `JobScheduler.scheduleRecurrently(id, cron, zone, lambda)` /
  `deleteRecurringJob(id)` (see `AutopilotService`), and reconciled on
  `ApplicationReadyEvent`. Prefer this over hardcoded `@Recurring` for user-configurable schedules.
- **Migrations**: Flyway `V{n}__desc.sql` in `backend/src/main/resources/db/migration`.
  Always the next integer; timestamps are `TIMESTAMP WITH TIME ZONE`.

## Frontend conventions (Next.js)

- **Server state = TanStack Query; client state = Zustand.** Don't duplicate server data
  into stores. Query keys are resource + scope id (e.g. `["autopilots", organizationId]`).
- **Forms**: react-hook-form + zod (`zodResolver`); shadcn `Dialog` for modals,
  `Sheet` for detail panels. Mirror `task-modal.tsx` / `autopilot-modal.tsx`.
- **API client**: `src/lib/api.ts` — one `*Api` object per resource using the shared
  `api` (Bearer token). Response/request types are hand-written interfaces with snake_case
  fields matching backend JSON.
- **Pages** under `src/app/(dashboard)/<feature>/page.tsx`; nav in `components/layout/sidebar.tsx`.

### API Response Compatibility (installed clients)

Desktop (Tauri) and mobile (Expo) builds are **installed** and outlive any given
backend, so every response shape **will** drift. The frontend/mobile must survive drift
without white-screening:

- **Parse, don't cast.** For endpoints whose data drives UI logic, validate the response
  with a zod schema via `parseWithFallback` (`src/lib/schema.ts`) and an explicit fallback;
  it logs and returns the fallback instead of throwing. See `autopilotsApi` for the pattern.
- Optional-chain and default downstream; use explicit `=== true` checks over truthiness.
- `switch` on server-driven enums must have a `default` (enum drift downgrades, not crashes).
- When you add or change an endpoint, add/extend its schema in the same change and test it
  against a malformed payload (missing field, wrong type, null array).

### Shared types (mobile) — follow-up

`mobile/lib/api.ts` hand-duplicates a small subset (~7) of the API types in
`frontend/src/lib/api.ts`, so they can drift. There is currently **no pnpm
workspace** (frontend/mobile/desktop are independent projects). To share types
without drift: add a root `pnpm-workspace.yaml` + a `packages/shared-types`
package, have both apps import from it, and configure Expo for it
(`metro.config.js` `watchFolders` + `tsconfig.json` path mapping). This MUST be
verified against a real Expo/Metro build before landing — Metro does not resolve
symlinked workspace packages out of the box. Until then, keep the mobile types in
sync by hand and mirror the frontend field names exactly.

## Client conventions (Python daemon)

- Specialist agents live in `agents/` (`BaseAgent` subclasses); `create_agent` in
  `agents/factory.py` is the single entry point. The **runtime adapter**
  (`runtime_kind="EXTERNAL_CLI"`) routes to `ExternalCliAgent`, which shells out to a CLI
  in the sandbox via `AgentSandbox.execute_streaming` instead of the LangGraph loop.
- The daemon (`daemon.py`) receives tasks over STOMP and either runs the orchestrator,
  the external-CLI path (`_run_external_cli_task`), or smoke mode.
- Config is `pydantic-settings` in `config.py` (env-aliased). Provider API keys are injected
  into the sandbox environment at start; never bake secrets into images.

## Testing

- Backend: JUnit 5 + Mockito (`@ExtendWith(MockitoExtension.class)`), AssertJ. Mock repos,
  assert behavior + access control. See `AutopilotServiceTest`.
- Frontend: Vitest + Testing Library (jsdom). Mock `@/lib/api` and `@/hooks/use-toast`;
  wrap in `QueryClientProvider`. `src/test/setup.ts` polyfills `ResizeObserver` for Radix.
- Client: pytest (`asyncio_mode = auto`); reuse the `mock_sandbox` fixture in `conftest.py`.

## Commits

Conventional Commits (`feat(scope)`, `fix(scope)`, `docs`, `test`, `refactor`, `chore`),
atomic and grouped by intent.
