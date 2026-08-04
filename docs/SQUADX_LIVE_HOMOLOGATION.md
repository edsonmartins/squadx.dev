# SquadX Live — homologation runbook

This runbook covers the three components in the live-view path:

1. `squadx.dev` creates and exposes the session.
2. `squadx-live` owns the room, browser signaling, and viewer UI.
3. The Python client publishes the agent VNC stream through the `squadx-live`
   REST/SSE signaling API.

## Required configuration

Use the same random `SQUADX_SERVICE_SECRET` (at least 32 characters) in all
three components. Do not reuse the user JWT secret.

### squadx.dev backend

```dotenv
SQUADX_LIVE_ENABLED=true
SQUADX_LIVE_URL=https://live-homolog.squadx.dev
SQUADX_SERVICE_SECRET=<shared-secret>
```

### squadx.dev frontend

`NEXT_PUBLIC_LIVE_URL` is a build-time variable:

```dotenv
NEXT_PUBLIC_LIVE_URL=https://live-homolog.squadx.dev
```

### Python agent client

```dotenv
SQUADX_LIVE_URL=https://live-homolog.squadx.dev
SQUADX_SERVICE_SECRET=<shared-secret>
```

If either value is absent, the client deliberately falls back to the legacy
Supabase signaling adapter. In homologation, confirm the startup log reports
the SquadX Live API adapter.

### squadx-live web

The deploy workflow expects a multiline GitHub Actions secret named
`WEB_ENV_FILE`. At minimum it must contain:

```dotenv
NEXT_PUBLIC_APP_URL=https://live-homolog.squadx.dev
POSTGRES_URL=postgres://...
SESSION_SECRET=<independent-random-secret>
ARCHGUARD_ISSUER_URL=https://...
ARCHGUARD_CLIENT_ID=squadx-live
ARCHGUARD_CLIENT_SECRET=...
ARCHGUARD_REDIRECT_URI=https://live-homolog.squadx.dev/api/auth/callback
SQUADX_SERVICE_SECRET=<shared-secret>
SQUADX_BACKEND_WEBHOOK_URL=https://api-homolog.squadx.dev/api/v1/webhooks/live
SQUADX_EMBED_ORIGINS=https://homolog.squadx.dev
NEXT_PUBLIC_LIVEKIT_URL=wss://livekit-homolog.squadx.dev
LIVEKIT_API_KEY=...
LIVEKIT_API_SECRET=...
TURN_SERVER_URL=turns:turn-homolog.squadx.dev:5349
TURN_SERVER_USERNAME=...
TURN_SERVER_CREDENTIAL=...
MINIO_ENDPOINT=https://minio-homolog.squadx.dev
MINIO_ACCESS_KEY=...
MINIO_SECRET_KEY=...
MINIO_BUCKET=squadx-live-media
MINIO_REGION=us-east-1
RUN_DB_MIGRATIONS=true
GATE_ENABLED=false
```

For more than one web replica, set `RUN_DB_MIGRATIONS=false` and execute the
Drizzle migrations once as a release step.

## Deployment order

1. Provision PostgreSQL, object storage, LiveKit, and a TLS TURN service.
2. Deploy `squadx-live`; wait for `GET /api/ready` to return HTTP 200.
3. Deploy the `squadx.dev` backend and verify its integration health reports
   `squadx-live` as `UP`.
4. Build/deploy the frontend with the homologation `NEXT_PUBLIC_LIVE_URL`.
5. Restart an agent daemon with the two Python client variables above.

The unified local stack can be rendered before startup with:

```bash
docker compose \
  --env-file platform/.env \
  -f platform/docker-compose.yml \
  config
```

## Acceptance test

1. Start a task assigned to an online agent.
2. Confirm one active external session is created for the task. Repeating the
   create call must return the same `sessionId` with `reused: true`.
3. Open **Watch Live** from the task details. The URL or embedded page must be
   served by `live-homolog.squadx.dev`, not the legacy Supabase viewer.
4. Confirm the parent UI changes from “Connecting” only after receiving the
   `session:connected` message from the embedded viewer.
5. Observe the VNC screen for at least five minutes and test reconnecting the
   viewer tab. Test once from a network that requires TURN (for example mobile
   data behind carrier NAT).
6. End the task and confirm the external room ends, the viewer receives
   `session:ended`, and the backend webhook is accepted.
7. Inspect logs for JWT audience/issuer failures, SSE reconnect loops, migration
   failures, and webhook non-2xx responses.

## Release blockers

- `/api/ready` is not HTTP 200.
- The three `SQUADX_SERVICE_SECRET` values differ.
- HTTPS/WSS or TURN TLS is unavailable from the public homologation network.
- The browser is blocked by `frame-ancestors` because the frontend origin is
  missing from `SQUADX_EMBED_ORIGINS`.
- The agent daemon logs that it selected the Supabase fallback.
- Database migrations have not been applied.

## Current compatibility boundary

Legacy sessions without `external_join_code` continue to use the existing
Supabase viewer. New integrated sessions use `squadx-live`. This fallback can
be removed after homologation data and older clients have been retired.
