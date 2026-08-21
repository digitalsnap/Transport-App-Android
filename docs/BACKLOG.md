# RideVibe — Consolidated Backlog

Reconciles `ridevibe-roadmap.md` (pinned at `main @ 6e3f8b1`) against the repo as
it actually stands. Where the two disagree, **this file is authoritative** — the
roadmap is 11 commits stale.

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
- no auto-reconnect on the socket today

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

## Phase 0 — Real backend (revised)

Unchanged in intent; corrected in scope. Before starting:

1. Decide the module shape — create `core-data`, or keep real impls in
   `core-network` and update the roadmap prompt.
2. Align on hold semantics: `seats/{id}/lock` (app today) vs `POST /holds`
   (roadmap). Pick one.
3. Design the four missing domains, or explicitly scope them out of v1 and leave
   them on mocks — which the BuildConfig switch now supports per-repository.
4. Decide auth before endpoints, not after.

Phases 1–5 stand as written in `ridevibe-roadmap.md`, minus the Money work
promoted to "unblocked now" above.
