# RideVibe — Consolidated Backlog

Reconciles `ridevibe-roadmap.md` (pinned at `main @ 6e3f8b1`) against the repo as
it actually stands. Where the two disagree, **this file is authoritative** — the
roadmap is 11 commits stale. The roadmap document itself is not in this
repository; phase numbers below are kept for historical continuity.

---

## Roadmap ↔ repo drift

Things the roadmap assumes that aren't true. Fix these assumptions before
starting Phase 0, or the backend gets built against the wrong contract.

| # | Roadmap says | Reality |
|---|---|---|
| 1 | `main @ 6e3f8b1`, "mock backend" | HEAD is `scaffold-ridevibe-app`, 11 commits ahead — adds itineraries, fastcraft + regulated fares, Google/Facebook OAuth, beta release infra |
| 2 | "in core-data, real repository implementations" | **There is no `core-data` module.** Real impls live in `core-network` (`SeatRepositoryImpl`, `TripRepositoryImpl`, `CheckoutRepositoryImpl`) |
| 3 | Flag named `USE_MOCKS` | Named `USE_MOCK_DATA`. Now a BuildConfig field, property `ridevibe.useMocks` |
| 4 | Phase 0 API = 6 REST endpoints | `CrsApiService` already declares **12 endpoints + a WebSocket**. A backend built to the roadmap's list will not satisfy the app |
| 5 | Mock package gets deleted at go-live | **Keep it.** It is what lets the app build, demo, and run UI tests with no server. Dev asset, not debt |
| 6 | Auth arrives in Phase 1 | No `Authorization` header exists anywhere. `GET /bookings` is meaningless without it — auth is effectively Phase 0 work |
| 7 | (absent) | Beta distribution — signing, Crashlytics, App Distribution — wasn't in the roadmap at all. Now done, see Phase B |

### Endpoints the roadmap's Phase 0 omits

Present in `CrsApiService`, missing from the roadmap's backend task list:
`GET /v1/locations`, `GET /v1/bus-classes`, `GET /v1/journeys`,
`GET /v1/trips/related`, `GET /v1/tickets/{id}`,
`POST /v1/trips/{id}/seats/{seatId}/lock` and `/release` (roadmap models holds
as `POST /holds` / `DELETE /holds/:id` — pick one shape and align both sides).

### Domains with no contract at all

`Profile`, `Wallet`, `Support`, `Itinerary` are mock-only with TODOs in
`RepositoryModule.kt`. No endpoints in `CrsApiService`, no mention in the
roadmap's Phase 0 schema. **~Half the app has no backend contract.** These need
designing before Phase 0 can be called complete.

---

## Phase B — Beta distribution ✅ DONE

Not in the original roadmap. Landed ahead of Phase 0 so testers can exercise the
UX on mock data while the backend is built.

- Release signing wired to `keystore.properties` / `RIDEVIBE_*` env vars
- Crashlytics + R8 mapping upload; `SourceFile,LineNumberTable` keep rules
- Firebase App Distribution → `beta` tester group, `release-notes.txt`
- `ridevibe.versionCode` / `versionName` in `gradle.properties`
- Full runbook in `RELEASING.md`

**Remaining (blocked on you, not code):** generate the upload keystore, register
release SHA-1/SHA-256 with Firebase and the key hash with Facebook, install and
authenticate `firebase-tools`, create the `beta` group. See `RELEASING.md` Part 1.

---

## Phase 0a — Backend wiring ✅ DONE

Groundwork for Phase 0 that needed no backend to exist.

- `API_BASE_URL`, `WS_BASE_URL`, `USE_MOCK_DATA` are BuildConfig fields in
  `core-network`, per build type — no code edit to repoint the app
- WS URL derives from the REST URL (`http→ws`, `https→wss`) so they cannot drift
- Overridable per build:
  `./gradlew installDebug -Pridevibe.useMocks=false -Pridevibe.apiBaseUrl.debug=http://192.168.1.5:8080/`
- Debug-only cleartext manifest for local HTTP backends; release stays HTTPS-only
- Defaults in `gradle.properties`: debug `http://10.0.2.2:8080/`, mocks on

---

## Unblocked now — no backend, no keystore, no console access

Ordered by value. Everything here can start today.

### ~~1. Export the seed data~~ ✅ DONE
`./gradlew :core-network:exportSeedData` → `docs/seed/`. Generated from the
Kotlin by a unit test, so the JSON can't drift from the mock silently.
**45 terminals · 388 directed routes · 696 services · 73 operators · 4 journeys.**
`docs/seed/README.md` flags what the corpus does *not* carry: no coordinates
(blocks Phase 2 boarding points), no vehicles or seat layouts, placeholder 4.2
ratings on workbook-imported services, and `departureHours` is an hour list
rather than a real schedule.

### ~~2. Write the OpenAPI spec~~ ✅ DONE
`docs/api/openapi.yaml` — 12 paths, 16 schemas, verified to match
`CrsApiService` exactly in both directions. Raised **9 open questions, 2 of them
blockers**, recorded in `x-open-questions`:
- **auth** — nothing exists; `GET /v1/bookings` is unbuildable without it
- **unspecified domains** — profile/wallet/support/itinerary have no contract
- `/v1/trips/related` collides with `/v1/trips/{tripId}` (routing hazard)
- `busClass`, `passengerType`, `paymentStatus` **crash** the client on an
  unknown value; `rideKind`, seat `status`, location `kind` degrade silently
- hold TTL is never returned by REST, only hinted on the socket event
- WebSocket path drift: client uses `/seat-events`, roadmap says `/inventory`
  (resolved: backend serves `/seat-events`)
- ~~no auto-reconnect on the socket today~~ (done — see Consolidation below)

Still worth doing: automate the client↔spec check in CI (`docs/api/README.md`).

### 3. Money value class  ·  M  ← **move this out of Phase 5**
Roadmap puts multi-currency in Phase 5 (week 23+). That is backwards: it means
replacing raw doubles across domain, DTOs, **and live DB columns** after the
schema exists. Doing it now costs a domain-layer refactor and nothing else.
Cheapest it will ever be.

### 4. i18n string extraction  ·  M
Roadmap Phase 5 item 1. Large but mechanical, zero backend dependency, and it
gets harder with every screen added. Good background task.

### 5. Housekeeping  ·  XS
- `SupportRepository` is declared inside `WalletRepository.kt` — split it out
- `zxing-android-embedded` and `coil-compose` are in the version catalog but
  referenced by no module — drop or use them

---

## Phase 0 — Real backend ✅ LANDED (separate repo)

The CRS backend exists: Fastify + Postgres at
`D:\The Bus App\Database-RideVibe\Database-RideVibe-App` (its own git repo, kept
separate on purpose for commit/push protection). It implements all 12 `/v1`
routes and the seat-events socket, with a contract test that fails if either
side drifts from `docs/api/openapi.yaml` (mirrored there as `spec/openapi.yaml`).

The four "before starting" decisions, as taken:

1. **Module shape** — real impls stay in `core-network`; there is no `core-data`.
2. **Hold semantics** — `POST /seats/{id}/lock` and `/release`, no hold id, 10-minute
   TTL, max 12 live holds per user (409 beyond).
3. **Missing domains** — Profile, Wallet, Support, Itinerary stay on mocks via
   the per-repository switch until designed (roadmap Phases 3–4).
4. **Auth** — passengers are scoped by `X-Device-Id` (interim, not secure, must
   not ship); staff use real email/password accounts with 7-day sessions. See
   `x-open-questions.auth` in the spec for the remaining unification decision.

---

## Consolidation — 2026-09-03 ✅ DONE

The backend's two web dashboards (`/admin`, `/partner`) are now also in the app
as the **staff console** (`feature-admin`), reached from Profile → "Open staff
console". Two repos, one product:

- **Staff contract.** `docs/api/staff-openapi.yaml` (mirrored as the backend's
  `spec/staff-openapi.yaml`) documents `/auth`, `/admin/api`, `/partner/api` —
  36 operations. A second backend contract test locks it both ways, exactly like
  the passenger spec.
- **Data layer.** `StaffApiService` + `StaffAuthRepository` / `AdminRepository` /
  `PartnerRepository` in `core-network`, session persisted in
  EncryptedSharedPreferences, `x-admin-token` / `x-partner-token` attached only
  on staff routes. Mock implementations keep the console demoable offline
  (`admin@admin.com` / `admin`, `partner@partner.com` / `partner`, matching the
  backend's dev accounts).
- **Passenger client gaps closed.** `X-Device-Id` interceptor (401s gone);
  `X-User-Id` captured so the seat map recognises its own holds after a reload;
  409/429 bodies surface as readable messages; the seat-events socket reconnects
  with backoff and resyncs the seat map.
- **Backend copies reconciled.** The `Database-RideVibe-App-main` folder's
  uncommitted work (rate limiting, 12-hold cap, admin API stripped of device
  ids, new tests) was applied onto the clean repo. That folder is now
  superseded and can be deleted once the merged working tree is committed.

### Still open after consolidation

- **Identity unification** (blocker before go-live) — see `x-open-questions.auth`.
- **Google sign-in for staff in the app** — the backend verifies a *web* client
  ID token; the app would need `requestIdToken(webClientId)` and to post it to
  `/auth/google`. Email/password and emailed key work today.
- **Release build** points at a placeholder host; the staff console in a release
  APK needs a deployed backend.
- **Money as doubles** — now multiplied across admin analytics; still the
  cheapest time to fix is before more schema lands.
- **Run the backend suite** once Docker is available: `pnpm test` in the backend
  repo (the two contract tests, seat-hold concurrency, privacy and rate-limit
  suites).

Phases 1–5 stand as written in `ridevibe-roadmap.md`, minus the Money work
promoted to "unblocked now" above and the operator-dashboard/conductor-role work
that the staff console now covers in part (roadmap Phase 3 Task A item 1, Phase 4
Task C).
