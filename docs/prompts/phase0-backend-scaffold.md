# Phase 0 — Backend scaffold prompt

Paste-ready prompt for a Claude Code session run in the backend repo:

```
D:\The Bus App\Database-RideVibe
```

Not in the Android repo.

## Before you paste

1. ~~Copy the spec and seed data into the backend repo~~ **✅ done.** The repo is
   initialised on `main` with `spec/openapi.yaml`, `seed/*.json`, `seed/README.md`
   and a `.gitignore` that already excludes `.env`. Still to do: add the GitHub
   remote (`git remote add origin <url>`).

2. **Get both Supabase connection strings** — Project Settings → Database:
   - *Direct connection* (port 5432) — required for `LISTEN/NOTIFY`
   - *Transaction pooler* (port 6543) — for normal queries

3. **Do not paste the database password into the prompt.** The prompt tells
   Claude to read a `.env` file; you fill that in yourself.

4. Note the free-tier behaviour: Supabase pauses a project after about a week of
   inactivity. Fine during active development; just unpause from the dashboard.

## Decisions baked into this prompt

These settle open questions from `spec/openapi.yaml`. Change them in the prompt
if you disagree — but decide deliberately, they shape the schema.

| Question | Decision |
|---|---|
| Hold semantics | Match the app: `POST /seats/{id}/lock` + `/release`. Not the roadmap's `POST /holds` — that would require changing the client |
| Hold TTL | 10 minutes, `expires_at` column, lazy expiry on read + `pg_cron` sweep |
| Auth | **Deferred.** `X-Device-Id` header for v1, behind a middleware shaped so Firebase token verification drops in later |
| Money storage | `fare_minor bigint` + `currency char(3)` **now**, while the wire format still serves `farePhp` as a double |
| Route collision | `/v1/trips/related` registered before `/v1/trips/:tripId` |
| API hosting | Local for now. Dockerfile included; deployment is a follow-up |

---

## The prompt

```
You are scaffolding the RideVibe CRS backend in this repository, which is empty.

CONTEXT
RideVibe is a Philippine bus/ferry booking app (Android, Kotlin/Compose). The
Android client already exists and already defines the API contract. Your job is
to build the backend it expects, backed by a Supabase Postgres database, so the
app can be switched off its mock data layer.

Two files in this repo are your inputs and are authoritative:
  - spec/openapi.yaml  — the full API contract: 12 endpoints, 16 schemas,
    derived from the client's Retrofit interface and verified to match it
    exactly. Read this FIRST and in full, including the `x-open-questions` and
    `x-websocket` sections at the bottom.
  - seed/terminals.json, seed/routes.json, seed/journeys.json — 45 terminals,
    388 directed routes, 696 services, 73 operators, 4 curated journeys,
    exported from the app's mock data.

Do not invent endpoints or rename fields. The client is already built against
this contract; any deviation breaks it.

STACK
Node 20 + Fastify + TypeScript. Postgres via `pg`. Zod for request validation.
node-pg-migrate for migrations. Vitest for tests. No ORM.

TASK A — Database
1. Migrations for: operators, terminals, routes, route_services, trips, seats,
   seat_holds, bookings, booking_passengers, users.
2. Money is stored as `fare_minor BIGINT` + `currency CHAR(3) DEFAULT 'PHP'`,
   never as a float. The API still serialises `farePhp` as a JSON number to
   match the current client contract — convert at the edge, in one place.
3. seat_holds has `expires_at TIMESTAMPTZ` and a unique constraint preventing
   two live holds on the same seat. Seat availability must be correct under
   concurrent requests — use a transaction with `SELECT ... FOR UPDATE` on the
   seat row, not a read-then-write.
4. Hold TTL is 10 minutes. Expire them two ways: lazily (reads never return an
   expired hold as active) and actively (a `pg_cron` job every minute that
   deletes expired holds and NOTIFYs the affected seats). Supabase supports
   pg_cron — enable it in a migration.
5. A `NOTIFY seat_events` trigger (or explicit NOTIFY in the handlers) carrying
   the SeatStatusEvent payload from spec/openapi.yaml.

TASK B — Seeding
1. A `pnpm seed` script that loads seed/*.json idempotently (safe to re-run).
2. Two things the seed README flagged — handle both explicitly:
   - routes.json contains BOTH directions of every route as separate records.
     Load them as directed routes; do not dedupe silently.
   - `rating: 4.2` is a placeholder for workbook-imported services, not a real
     rating. Import those as NULL.
3. Trips are generated from route_services: each service has `departureHours`
   (hours of the day, not a schedule) and `durationMinutes`. Generate concrete
   trips for the next 14 days. Seat maps are generated per trip — 2x2 for bus,
   2x1 for van/luxury class.

TASK C — API
Implement every endpoint in spec/openapi.yaml exactly. Specific requirements:
1. Register `/v1/trips/related` BEFORE `/v1/trips/:tripId`, or the static path
   becomes unreachable. Add a test that proves it.
2. Enum discipline: the client parses busClass, passengerType and paymentStatus
   with `valueOf()` and NO fallback — an out-of-enum value crashes the app. Add
   a response-validation layer that fails loudly in dev if any response carries
   a value outside the spec's enums.
3. `GET /v1/bookings` is user-scoped. Auth is deliberately deferred: read an
   `X-Device-Id` header and scope bookings to it. Put this behind an auth
   middleware that resolves a `userId`, so Firebase ID token verification can
   replace it later without touching handlers. Add a clear TODO and a note in
   the README that device-id scoping is NOT secure and must not ship to
   production.
4. WebSocket at `/v1/trips/:tripId/seat-events` (this exact path — the roadmap
   says `/inventory`, the client does not). It broadcasts SeatStatusEvent.
   Back it with Postgres LISTEN/NOTIFY so multiple instances stay in sync.

TASK D — Connections and config
1. TWO connection strings, and this matters: LISTEN/NOTIFY requires a long-lived
   DIRECT connection (port 5432). A transaction-mode pooler (port 6543) silently
   breaks it — the listener connects and then never receives anything. Use the
   direct connection for the listener only, pooled for everything else.
2. Read config from env via a zod-validated schema that fails fast on startup:
   DATABASE_URL, DATABASE_DIRECT_URL, PORT, NODE_ENV.
3. Commit `.env.example` with empty values. Never commit `.env`; gitignore it.
4. Dockerfile listening on 0.0.0.0:$PORT, ready for a future container deploy.

TASK E — Docs
README.md covering: local setup, both connection strings and why, running
migrations, seeding, running tests, and a "connecting the Android app" section
with the exact command (see VERIFY below).

VERIFY — all of these must pass before you are done:
- `pnpm test` green. Tests must cover, at minimum: concurrent lock attempts on
  the same seat (exactly one wins), hold expiry, the /trips/related route
  ordering, and money conversion round-tripping through minor units.
- A contract test that loads spec/openapi.yaml and asserts every path+method in
  it is implemented, and that no route exists which is absent from the spec.
  This is the anti-drift mechanism — it is not optional.
- Migrations run cleanly against a fresh database, and `pnpm seed` is
  idempotent across two consecutive runs.
- `curl` examples in the README that actually work against a local server.

NON-GOALS — do not build these:
Payments, refunds, signed/HMAC QR payloads, ratings, push notifications,
boarding points, live GPS tracking, conductor mode, the operator dashboard.
Those are roadmap Phases 1-4. Also do not implement profile, wallet, support or
itinerary endpoints — they have no contract yet and the app keeps them on mocks.

Work on a branch. Conventional commits, in logical chunks. Do not commit
secrets.
```

## After it runs

Point a debug build of the Android app at the local server. `10.0.2.2` is the
host machine from the emulator; use your LAN IP for a physical device:

```bash
./gradlew installDebug -Pridevibe.useMocks=false -Pridevibe.apiBaseUrl.debug=http://10.0.2.2:8080/
```

The release build stays on mock data until you change the default in
`gradle.properties`, so this cannot affect the beta build your testers have.

**First thing to check:** trip search, then a seat lock from two devices at once.
Two phones seeing each other's locks live is the Phase 0 gate.
